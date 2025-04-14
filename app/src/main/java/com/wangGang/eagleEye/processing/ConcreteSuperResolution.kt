// ConcreteSuperResolution.kt
package com.wangGang.eagleEye.processing

import android.graphics.Bitmap
import android.util.Log
import com.wangGang.eagleEye.processing.multiple.alignment.LRWarpingOperator
import com.wangGang.eagleEye.assessment.InputImageEnergyReader
import com.wangGang.eagleEye.constants.ParameterConfig
import com.wangGang.eagleEye.io.DirectoryStorage
import com.wangGang.eagleEye.io.FileImageReader
import com.wangGang.eagleEye.io.FileImageWriter
import com.wangGang.eagleEye.io.ImageFileAttribute
import com.wangGang.eagleEye.model.AttributeHolder
import com.wangGang.eagleEye.model.multiple.SharpnessMeasure
import com.wangGang.eagleEye.model.multiple.SharpnessMeasure.SharpnessResult
import com.wangGang.eagleEye.processing.filters.YangFilter
import com.wangGang.eagleEye.processing.imagetools.ImageOperator
import com.wangGang.eagleEye.processing.multiple.alignment.FeatureMatchingOperator
import com.wangGang.eagleEye.processing.multiple.alignment.MedianAlignmentOperator
import com.wangGang.eagleEye.processing.multiple.alignment.WarpResultEvaluator
import com.wangGang.eagleEye.processing.multiple.enhancement.UnsharpMaskOperator
import com.wangGang.eagleEye.processing.multiple.fusion.MeanFusionOperator
import com.wangGang.eagleEye.ui.activities.CameraControllerActivity
import com.wangGang.eagleEye.ui.utils.ProgressManager
import com.wangGang.eagleEye.ui.viewmodels.CameraViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File
import java.util.concurrent.Semaphore

const val TAG = "ConcreteSuperResolution"

class ConcreteSuperResolution(private val viewModel: CameraViewModel) : SuperResolutionTemplate() {

    override fun readEnergy(imageInputMap: List<String>): Array<Mat> {
//        viewModel.updateLoadingText("Reading energy")
        val inputMatList: Array<Mat> = Array(imageInputMap.size) { Mat() }

        try {
            for (i in imageInputMap.indices) {
                // Create a dummy semaphore because InputImageEnergyReader requires one.
                // Its release from finishWork() is not used in sequential processing.
                val dummySem = Semaphore(0)
                val reader = InputImageEnergyReader(dummySem, imageInputMap[i])

                // Start the reader and wait for it to finish before proceeding
                reader.start()
                reader.join()  // Wait until this thread completes

                // Copy the result from the reader to the array
                inputMatList[i] = reader.outputMat!!
            }

        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        return inputMatList
    }


    override fun applyFilter(energyInputMatList: Array<Mat>): Array<Mat> {
        viewModel.updateLoadingText("Applying filter")

        val yangFilter = YangFilter(energyInputMatList)
        yangFilter.perform()
        return yangFilter.getEdgeMatList()
    }

    override fun measureSharpness(filteredMatList: Array<Mat>): SharpnessResult {
        return SharpnessMeasure.getSharedInstance().measureSharpness(filteredMatList)
    }

    override fun performSuperResolution(filteredMatList: Array<Mat>, imageInputMap: List<String>): Bitmap {
        viewModel.updateLoadingText("Measuring Sharpness")
        val sharpnessResult = SharpnessMeasure.getSharedInstance().measureSharpness(filteredMatList)
        val inputIndices: Array<Int> = SharpnessMeasure.getSharedInstance().trimMatList(imageInputMap.size, sharpnessResult, 0.0)

        ProgressManager.getInstance().nextTask()

        val rgbInputMatList = Array(inputIndices.size) { "" }

        val bestIndex = inputIndices.indexOf(sharpnessResult.bestIndex)

        ProgressManager.getInstance().nextTask()

        runBlocking {
            for ((index, i) in inputIndices.withIndex()) {
                withContext(Dispatchers.IO) {
                    val inputMat = FileImageReader.getInstance()!!.imReadFullPath(imageInputMap[i])
                    val unsharpMaskOperator = UnsharpMaskOperator(inputMat, i)
                    unsharpMaskOperator.perform()
                    rgbInputMatList[index] = unsharpMaskOperator.getFilePath()!!
                    inputMat.release()
                }
            }
        }

        // Perform actual super-resolution
        return performFullSRMode(rgbInputMatList, inputIndices, imageInputMap, bestIndex, false)
    }

    private fun performMedianAlignment(imagesToAlignList: Array<String>, resultNames: Array<String>) {

        val medianAlignmentOperator = MedianAlignmentOperator(imagesToAlignList, resultNames)
        medianAlignmentOperator.perform()
    }

    private fun interpolateImage(index: Int, imageInputMap: List<String>) {
        val inputMat = FileImageReader.getInstance()!!.imReadFullPath(imageInputMap[index])

        val outputMat = ImageOperator.performInterpolation(inputMat, ParameterConfig.getScalingFactor().toFloat(), Imgproc.INTER_LINEAR)
        FileImageWriter.getInstance()?.saveMatrixToImage(outputMat, DirectoryStorage.SR_ALBUM_NAME_PREFIX, "linear", ImageFileAttribute.FileType.JPEG)
        outputMat.release()

        inputMat.release()
        System.gc()
    }

    private fun performFullSRMode(
        rgbInputMatList: Array<String>,
        inputIndices: Array<Int>,
        imageInputMap: List<String>,
        bestIndex: Int,
        debug: Boolean
    ): Bitmap {
        // Perform feature matching of LR images against the first image as reference mat.
        val warpChoice = ParameterConfig.getPrefsInt(ParameterConfig.WARP_CHOICE_KEY, 3)

        // Perform perspective warping and alignment
//        Preprocessing Images
        val succeedingMatList = rgbInputMatList.sliceArray(1 until rgbInputMatList.size)
        val medianResultNames = Array(succeedingMatList.size) { i -> "median_align_$i" }
        val warpResultNames = Array(succeedingMatList.size) { i -> "warp_$i" }
        ProgressManager.getInstance().nextTask()

        // 1 = Best Alignment Technique
        // 2 = Median Alignment
        // 3 = Perspective Warping
        // 3 is default
        when (warpChoice) {
            1 -> {
                viewModel.updateLoadingText("Performing Best Alignment Technique")
                this.performPerspectiveWarping(rgbInputMatList[0], succeedingMatList, succeedingMatList, warpResultNames)
            }
            2 -> {
                viewModel.updateLoadingText("Performing Median Alignment")
                this.performMedianAlignment(rgbInputMatList, medianResultNames)
            }
            3 -> {
                viewModel.updateLoadingText("Performing Perspective Warping")
                this.performPerspectiveWarping(rgbInputMatList[0], succeedingMatList, succeedingMatList, warpResultNames)
            }
        }

        ProgressManager.getInstance().nextTask()
        SharpnessMeasure.destroy()

        val numImages = AttributeHolder.getSharedInstance()!!.getValue("WARPED_IMAGES_LENGTH_KEY", 0)
        val warpedImageNames = Array(numImages) { i -> "warp_$i" }
        val medianAlignedNames = Array(numImages) { i -> "median_align_$i" }

        val alignedImageNames = assessImageWarpResults(inputIndices[0], warpChoice, imageInputMap, warpedImageNames, medianAlignedNames, debug)

        ProgressManager.getInstance().nextTask()

        return this.performMeanFusion(inputIndices[0], bestIndex, alignedImageNames, imageInputMap, debug)
    }

    private fun performPerspectiveWarping(
        referenceMat: String,
        candidateMatList: Array<String>,
        imagesToWarpList: Array<String>,
        resultNames: Array<String>
    ) {
        val refMat = FileImageReader.getInstance()?.imReadFullPath(referenceMat)!!
        val matchingOperator = FeatureMatchingOperator(refMat, candidateMatList)
        matchingOperator.perform()
        val perspectiveWarpOperator = LRWarpingOperator(
            matchingOperator.refKeypoint,
            imagesToWarpList,
            resultNames,
            matchingOperator.getdMatchesList(),
            matchingOperator.lrKeypointsList
        )
        perspectiveWarpOperator.perform()

        // release images
        matchingOperator.refKeypoint.release()
    }

    private fun assessImageWarpResults(
        index: Int,
        alignmentUsed: Int,
        imageInputMap: List<String>,
        warpedImageNames: Array<String>,
        medianAlignedNames: Array<String>,
        useLocalDir: Boolean
    ): Array<String> {  // Change return type to Array<String>
        return when (alignmentUsed) {
            1 -> {
                val referenceMat: Mat
                val fileImageReader = FileImageReader.getInstance()
                if (fileImageReader != null) {
                    referenceMat = if (useLocalDir) {
                        fileImageReader.imReadOpenCV("input_$index", ImageFileAttribute.FileType.JPEG)
                    } else {
                        fileImageReader.imReadFullPath(imageInputMap[index])
                    }

                    val warpResultEvaluator = WarpResultEvaluator(referenceMat, warpedImageNames, medianAlignedNames)
                    warpResultEvaluator.perform()

                    // Filter out null values and convert to a non-nullable array
                    warpResultEvaluator.chosenAlignedNames.filterNotNull().toTypedArray()
                } else {
                    // Handle the case where FileImageReader is null (optional)
                    throw IllegalStateException("FileImageReader instance is null.")
                }
            }
            2 -> medianAlignedNames
            else -> warpedImageNames
        }
    }

    private fun performMeanFusion(
        index: Int,
        bestIndex: Int,
        alignedImageNames: Array<String>,
        imageInputMap: List<String>,
        debugMode: Boolean
    ): Bitmap {
        if (alignedImageNames.size == 1) {
            viewModel.updateLoadingText("Skipping Mean Fusion, Interpolate Selected Best Image")
            val resultMat: Mat = if (debugMode) {
                FileImageReader.getInstance()?.imReadOpenCV(
                    "input_$bestIndex",
                    ImageFileAttribute.FileType.JPEG
                ) ?: throw IllegalStateException("FileImageReader instance is null")
            } else {
                FileImageReader.getInstance()?.imReadFullPath(
                    imageInputMap[bestIndex]
                ) ?: throw IllegalStateException("FileImageReader instance is null")
            }

            ProgressManager.getInstance().nextTask()

            return ImageOperator.matToBitmap(resultMat)

        } else {
            viewModel.updateLoadingText("Performing Mean Fusion")

            val imagePathList = mutableListOf<String>()
            // Add initial input HR image
            val inputMat: Mat = if (debugMode) {
                FileImageReader.getInstance()?.imReadOpenCV(
                    "input_$index",
                    ImageFileAttribute.FileType.JPEG
                ) ?: throw IllegalStateException("FileImageReader instance is null")
            } else {
                FileImageReader.getInstance()?.imReadFullPath(
                    imageInputMap[index]
                ) ?: throw IllegalStateException("FileImageReader instance is null")
            }

            for (alignedImageName in alignedImageNames) {
                imagePathList.add(alignedImageName)
            }

            val fusionOperator = MeanFusionOperator(inputMat, imagePathList.toTypedArray())
            for (i in imageInputMap.indices) {
                val dirFile = File(imageInputMap[i])
                FileImageWriter.getInstance()?.deleteRecursive(dirFile)
            }

            val bitmapResult = fusionOperator.perform()

            ProgressManager.getInstance().nextTask()

            return bitmapResult
        }
    }
}