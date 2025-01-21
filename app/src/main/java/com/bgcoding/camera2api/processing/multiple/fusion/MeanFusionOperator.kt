package com.bgcoding.camera2api.processing.multiple.fusion

import android.util.Log
import com.bgcoding.camera2api.constants.ParameterConfig
import com.bgcoding.camera2api.io.FileImageReader
import com.bgcoding.camera2api.io.FileImageWriter
import com.bgcoding.camera2api.io.ImageFileAttribute
import com.bgcoding.camera2api.processing.imagetools.ImageOperator
import com.bgcoding.camera2api.processing.imagetools.MatMemory
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

    private var outputMat: Mat? = null

    fun perform() {
        // Delete acquired images if camera app was used.
        outputMat = Mat()
        performAlternateFusion()
    }

    // Similar to the native counterpart, but has more overhead due to repetitive JNI calls.
    private fun performAlternateFusion() {
        val scale = ParameterConfig.getScalingFactor().toFloat()
        outputMat = Mat()
        initialMat.convertTo(initialMat, CvType.CV_16UC(initialMat.channels())) // Convert to CV_16UC
        Log.d(TAG, "Initial image for fusion Size: ${initialMat.size()} Scale: $scale")

        var sumMat = ImageOperator.performInterpolation(initialMat, scale, Imgproc.INTER_LINEAR) // Linear interpolation
        initialMat.release()
        outputMat?.release()

        for (imagePath in imageMatPathList) {
            // Load the next Mat
            initialMat = FileImageReader.getInstance()?.imReadOpenCV(imagePath, ImageFileAttribute.FileType.JPEG)
                ?: throw IllegalStateException("Failed to read image: $imagePath")

            // Delete file as it is no longer needed
            FileImageWriter.getInstance()?.deleteImage(imagePath, ImageFileAttribute.FileType.JPEG)
            Log.d(TAG, "Initial image for fusion. Name: $imagePath Size: ${initialMat.size()} Scale: $scale")


            // Perform interpolation
            initialMat = ImageOperator.performInterpolation(initialMat, scale, Imgproc.INTER_CUBIC) // Cubic interpolation
            val maskMat = ImageOperator.produceMask(initialMat)

            Core.add(sumMat, initialMat, sumMat, maskMat, CvType.CV_16UC(initialMat.channels()))
            Log.d(TAG, "sumMat size: ${sumMat.size()}")

            initialMat.release()
            maskMat.release()
            MatMemory.cleanMemory()
        }

        Core.divide(sumMat, Scalar.all(imageMatPathList.size + 1.0), sumMat)
        sumMat.convertTo(outputMat, CvType.CV_8UC(sumMat.channels()))
        sumMat.release()
    }

    fun getResult(): Mat? {
        return outputMat
    }
}
