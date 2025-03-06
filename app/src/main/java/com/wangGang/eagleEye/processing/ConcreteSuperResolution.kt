// ConcreteSuperResolution.kt
package com.wangGang.eagleEye.processing

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
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File
import java.util.concurrent.Semaphore

const val TAG = "ConcreteSuperResolution"

class ConcreteSuperResolution(private val viewModel: CameraViewModel) : SuperResolutionTemplate() {

    override fun readEnergy(imageInputMap: List<String>): Array<Mat> {
        viewModel.updateLoadingText("Reading energy")

        val inputMatList: Array<Mat> = Array(imageInputMap.size) { Mat() }
        val energyReaders: MutableList<InputImageEnergyReader> = mutableListOf()
        val energySem = Semaphore(0)

        try {
            for (i in inputMatList.indices) {
                val reader = InputImageEnergyReader(energySem, imageInputMap[i])
                energyReaders.add(reader)
                // Assuming each reader is run on a separate thread or coroutines
            }

            // Wait for all threads to finish
            for (reader in energyReaders) {
                reader.start()  // Ensure reader threads are started (this depends on how InputImageEnergyReader works)
            }

            // Wait for all semaphores to release
            energySem.acquire(inputMatList.size)

            ProgressManager.getInstance().incrementProgress("Reading energy")

            viewModel.updateLoadingText("Copying Results")
            Log.d("ProgressBar", "Copying Results")
            // Once all tasks are done, copy results
            for (i in energyReaders.indices) {
                inputMatList[i] = energyReaders[i].outputMat!!
            }

            ProgressManager.getInstance().incrementProgress("Copying Results")

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

    override fun performSuperResolution(filteredMatList: Array<Mat>, imageInputMap: List<String>) {
        viewModel.updateLoadingText("Measuring Sharpness")
        val sharpnessResult = SharpnessMeasure.getSharedInstance().measureSharpness(filteredMatList)
        val inputIndices: Array<Int> = SharpnessMeasure.getSharedInstance().trimMatList(imageInputMap.size, sharpnessResult, 0.0)

        ProgressManager.getInstance().incrementProgress("Measuring Sharpness")

        val rgbInputMatList = Array(inputIndices.size) { Mat() }
        val bestIndex = inputIndices.indexOf(sharpnessResult.bestIndex)

        viewModel.updateLoadingText("Performing Unsharp Masking")

        // Run image processing in parallel
        runBlocking {
            inputIndices.mapIndexed { index, i ->
                async(Dispatchers.IO) {
                    val inputMat = FileImageReader.getInstance()!!.imReadFullPath(imageInputMap[i])
                    val unsharpMaskOperator = UnsharpMaskOperator(inputMat, i)
                    unsharpMaskOperator.perform()
                    rgbInputMatList[index] = unsharpMaskOperator.getResult()
                }
            }.awaitAll()
        }

        viewModel.updateLoadingText("Interpolating Images")
        // Super-resolution interpolation
//        interpolateImage(sharpnessResult.getOutsideLeastIndex(), imageInputMap)
//        SRProcessManager.getInstance().initialHRProduced()

        ProgressManager.getInstance().incrementProgress("Interpolating Images")

        // Perform actual super-resolution
        performActualSuperres(rgbInputMatList, inputIndices, imageInputMap, bestIndex, false)

        // Mark process as completed
//        SRProcessManager.getInstance().srProcessCompleted()
    }

    private fun performActualSuperres(
        rgbInputMatList: Array<Mat>,
        inputIndices: Array<Int>,
        imageInputMap: List<String>,
        bestIndex: Int,
        debugMode: Boolean
    ) {
        // Perform denoising on original input list
        // Note: Commented this out since Eagle Eye 2.0 does not
        // use denoising by default
        // val denoisingOperator = DenoisingOperator(rgbInputMatList)
        // denoisingOperator.perform()
        // val updatedMatList = denoisingOperator.getResult()

        // Pass updatedMatList to the next method
        this.performFullSRMode(rgbInputMatList, inputIndices, imageInputMap, bestIndex, debugMode)
    }

    private fun performMedianAlignment(imagesToAlignList: Array<Mat>, resultNames: Array<String>) {

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
        rgbInputMatList: Array<Mat>,
        inputIndices: Array<Int>,
        imageInputMap: List<String>,
        bestIndex: Int,
        debug: Boolean
    ) {
        viewModel.updateLoadingText("Preprocessing Images")
        // Perform feature matching of LR images against the first image as reference mat.
        val warpChoice = ParameterConfig.getPrefsInt(ParameterConfig.WARP_CHOICE_KEY, 3)

        // Perform perspective warping and alignment
        val succeedingMatList = rgbInputMatList.sliceArray(1 until rgbInputMatList.size)

        val medianResultNames = Array(succeedingMatList.size) { i -> "median_align_$i" }
        val warpResultNames = Array(succeedingMatList.size) { i -> "warp_$i" }

        ProgressManager.getInstance().incrementProgress("Preprocessing Images")

        // 1 = Best Alignment Technique
        // 2 = Median Alignment
        // 3 = Perspective Warping
        // 3 is default
        when (warpChoice) {
            1 -> {
                viewModel.updateLoadingText("Performing Best Alignment Technique")
                this.performPerspectiveWarping(rgbInputMatList[0], succeedingMatList, succeedingMatList, warpResultNames)
                ProgressManager.getInstance().incrementProgress("Performing Best Alignment Technique")
            }
            2 -> {
                viewModel.updateLoadingText("Performing Median Alignment")
                this.performMedianAlignment(rgbInputMatList, medianResultNames)
                ProgressManager.getInstance().incrementProgress("Performing Median Alignment")
            }
            3 -> {
                viewModel.updateLoadingText("Performing Perspective Warping")
                this.performPerspectiveWarping(rgbInputMatList[0], succeedingMatList, succeedingMatList, warpResultNames)
                ProgressManager.getInstance().incrementProgress("Performing Perspective Warping")
            }
        }


        SharpnessMeasure.destroy()

        viewModel.updateLoadingText("Assessing Image Warp Results")

        val numImages = AttributeHolder.getSharedInstance()!!.getValue("WARPED_IMAGES_LENGTH_KEY", 0)
        val warpedImageNames = Array(numImages) { i -> "warp_$i" }
        val medianAlignedNames = Array(numImages) { i -> "median_align_$i" }

        val alignedImageNames = assessImageWarpResults(inputIndices[0], warpChoice, imageInputMap, warpedImageNames, medianAlignedNames, debug)

        ProgressManager.getInstance().incrementProgress("Assessing Image Warp Results")

        this.performMeanFusion(inputIndices[0], bestIndex, alignedImageNames, imageInputMap, debug)

//        try {
//            Thread.sleep(3000)
//        } catch (e: InterruptedException) {
//            e.printStackTrace()
//        }
        // Refresh the gallery
        FileImageWriter.getInstance()?.refreshThumbnailFolder()

        Log.d("ConcreteSuperResolution", "launchBeforeAndAfterActivity")
        CameraControllerActivity.launchBeforeAndAfterActivity()
    }

    private fun performPerspectiveWarping(
        referenceMat: Mat,
        candidateMatList: Array<Mat>,
        imagesToWarpList: Array<Mat>,
        resultNames: Array<String>
    ) {

        val matchingOperator = FeatureMatchingOperator(referenceMat, candidateMatList)
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
    ) {
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

            // No need to perform image fusion, just use the best image.
            ProgressManager.getInstance().incrementProgress("Skipping Mean Fusion, Interpolate Selected Best Image")


            val savePath = FileImageWriter.getInstance()?.getSharedAfterPath(ImageFileAttribute.FileType.JPEG)
                ?: throw IllegalStateException("Failed to get output file path")
            val savePath2 = FileImageWriter.getInstance()?.getSharedResultPath(ImageFileAttribute.FileType.JPEG)
                ?: throw IllegalStateException("Failed to get output file path")
            ImageOperator.performJNIInterpolationWithMerge(
                resultMat, ParameterConfig.getScalingFactor().toFloat(), Imgproc.INTER_CUBIC, 1, savePath, savePath2
            )

            Log.d(TAG, "savePath: $savePath")

            ProgressManager.getInstance().incrementProgress("Saving Results")

            Log.d("ConcreteSuperResolution", "saveHRResultToUserDir 1")

            resultMat.release()
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
            fusionOperator.perform()
            ProgressManager.getInstance().incrementProgress("Performing Mean Fusion")


            if (fusionOperator.getResult() == null) {
                throw IllegalStateException("MeanFusionOperator result is null")
            }

            fusionOperator.getResult()!!.release()
        }
    }
}