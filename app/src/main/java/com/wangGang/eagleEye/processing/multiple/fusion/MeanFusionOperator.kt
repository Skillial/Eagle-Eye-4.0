package com.wangGang.eagleEye.processing.multiple.fusion

import android.graphics.Bitmap
import android.util.Log
import com.wangGang.eagleEye.constants.ParameterConfig
import com.wangGang.eagleEye.io.FileImageReader
import com.wangGang.eagleEye.io.FileImageWriter
import com.wangGang.eagleEye.io.ImageFileAttribute
import com.wangGang.eagleEye.processing.imagetools.ImageOperator
import com.wangGang.eagleEye.processing.imagetools.MatMemory
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/**
 * Experiment on new proposed method for performing mean fusion for images that have been warped and interpolated.
 * Created by NeilDG on 12/9/2016.
 */

class MeanFusionOperator(
    private var initialMat: Mat,
    private val imageMatPathList: Array<String>
)  {

    companion object {
        private const val TAG = "OptimizedFusionOperator"
    }

    external fun meanFuse(
        filenames: Array<Array<String>>,
        outputFilePath: String,
        outputFilePath2: String,
        quadrantNames: Array<String>,
        divisionFactor: Int,
        scale: Int,
        quadrantWidth: Int,
        quadrantHeight: Int
    ): Mat

    private var outputMat: Mat? = null

    fun perform(): Bitmap{
        // Delete acquired images if camera app was used.
        outputMat = Mat()
        return performAlternateFusion()
    }

    // Similar to the native counterpart, but has more overhead due to repetitive JNI calls.
    private fun performAlternateFusionWithInterpolation(): Bitmap {
        val scale = ParameterConfig.getScalingFactor().toFloat()
        outputMat = Mat()
        initialMat.convertTo(initialMat, CvType.CV_16UC(initialMat.channels())) // Convert to CV_16UC
        Log.d(TAG, "Initial image for fusion Size: ${initialMat.size()} Scale: $scale")
        var quadrantWidth = initialMat.width() / 4
        var quadrantHeight = initialMat.height() / 4
//        val sumMat = ImageOperator.performInterpolation(initialMat, scale, Imgproc.INTER_CUBIC) // Linear interpolation
        var fileList = mutableListOf<Array<String>>()
        fileList.add(ImageOperator.performJNIInterpolation(initialMat, 1))
        initialMat.release()
        outputMat?.release()

        for (imagePath in imageMatPathList) {
            // Load the next Mat
            initialMat = FileImageReader.getInstance()?.imReadOpenCV(imagePath, ImageFileAttribute.FileType.JPEG)
                ?: throw IllegalStateException("Failed to read image: $imagePath")
            // Delete file as it is no longer needed
            FileImageWriter.getInstance()?.deleteImage(imagePath, ImageFileAttribute.FileType.JPEG)
            Log.d(TAG, "Initial image for fusion. Name: $imagePath Size: ${initialMat.size()} Scale: $scale")
            fileList.add(ImageOperator.performJNIInterpolation(initialMat,fileList.size +1))

            // Perform interpolation
//            initialMat = ImageOperator.performInterpolation(initialMat, scale, Imgproc.INTER_CUBIC) // Cubic interpolation
//            val maskMat = ImageOperator.produceMask(initialMat)
//
//            Core.add(sumMat, initialMat, sumMat, maskMat, CvType.CV_16UC(initialMat.channels()))
//            Log.d(TAG, "sumMat size: ${sumMat.size()}")
//
            initialMat.release()
//            maskMat.release()
            MatMemory.cleanMemory()
        }
        val outputFilePath1 = FileImageWriter.getInstance()?.getSharedAfterPath(ImageFileAttribute.FileType.JPEG)
            ?: throw IllegalStateException("Failed to get output file path 1")
        val outputFilePath2 = FileImageWriter.getInstance()?.getSharedResultPath(ImageFileAttribute.FileType.JPEG)
            ?: throw IllegalStateException("Failed to get output file path 2")
        Log.d(TAG, "Output file path: $outputFilePath1")
        val fileList2D: Array<Array<String>> = fileList.map{it}.toTypedArray()
        val rowCount = fileList2D.size         // Number of rows in the original array
        val colCount = fileList2D[0].size      // Number of columns in the original array

        val fileList2dTransposed = Array(colCount) { Array(rowCount) { "" } }

        for (i in 0 until rowCount) {
            for (j in 0 until colCount) {
                fileList2dTransposed[j][i] = fileList2D[i][j]
            }
        }
        var divisionFactor  = 4;
        // initialize filenames
        val initQuadrantFilenames = Array(divisionFactor * divisionFactor) { index ->
            "/quadrant${index + 1}"
        }
        val quadrantsNames = FileImageWriter.getInstance()?.getPath(initQuadrantFilenames, ImageFileAttribute.FileType.JPEG)!!
        for (each in quadrantsNames){
            Log.d(TAG, "Quadrant name: $each")
        }

        Log.d("MeanFusionOperator", "OutputFilePath: $outputFilePath1")
        val newMat = meanFuse(fileList2dTransposed, outputFilePath1, outputFilePath2, quadrantsNames, divisionFactor, scale.toInt(), quadrantWidth, quadrantHeight)
        Core.rotate(newMat, newMat, Core.ROTATE_90_COUNTERCLOCKWISE)
        Imgproc.cvtColor(newMat, newMat, Imgproc.COLOR_BGR2RGB)
        val bitmap = Bitmap.createBitmap(newMat.cols(), newMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(newMat, bitmap)
        return bitmap
//        Core.divide(sumMat, Scalar.all(imageMatPathList.size + 1.0), sumMat)
//        sumMat.convertTo(outputMat, CvType.CV_8UC(sumMat.channels()))
//        sumMat.release()
    }

    private fun performAlternateFusion(): Bitmap {
        val scale = ParameterConfig.getScalingFactor().toFloat()
        outputMat = Mat()
        initialMat.convertTo(initialMat, CvType.CV_16UC(initialMat.channels())) // Convert to CV_16UC
        Log.d(TAG, "Initial image for fusion Size: ${initialMat.size()} Scale: $scale")

        val sumMat = initialMat.clone()
        initialMat.release()
        outputMat?.release()

        for (imagePath in imageMatPathList) {
            // Load the next Mat
            initialMat = FileImageReader.getInstance()?.imReadOpenCV(imagePath, ImageFileAttribute.FileType.JPEG)
                ?: throw IllegalStateException("Failed to read image: $imagePath")

            // Delete file as it is no longer needed
            FileImageWriter.getInstance()?.deleteImage(imagePath, ImageFileAttribute.FileType.JPEG)

            val maskMat = ImageOperator.produceMask(initialMat)

            Core.add(sumMat, initialMat, sumMat, maskMat, CvType.CV_16UC(initialMat.channels()))

            initialMat.release()
            maskMat.release()
            MatMemory.cleanMemory()
        }

        Core.divide(sumMat, Scalar.all(imageMatPathList.size + 1.0), sumMat)
        sumMat.convertTo(sumMat, CvType.CV_8UC(sumMat.channels()))
        return ImageOperator.matToBitmap(sumMat)
    }

}
