package com.bgcoding.camera2api.processing.multiple.refinement

import android.util.Log
import com.bgcoding.camera2api.io.FileImageWriter
import com.bgcoding.camera2api.io.ImageFileAttribute
import com.bgcoding.camera2api.processing.ColorSpaceOperator
import com.bgcoding.camera2api.thread.FlaggingThread
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfFloat
import org.opencv.photo.Photo
import java.util.concurrent.Semaphore

/**
 * Class that handles denoising operations
 * Created by NeilDG on 7/10/2016.
 */
class DenoisingOperator(private val matList: Array<Mat>) {

    private val outputMatList = Array(matList.size) { Mat() }

    fun perform() {
        for (i in matList.indices) {
            // Perform denoising on energy channel only
            val yuvMat = ColorSpaceOperator.convertRGBToYUV(matList[i])
            val denoisedMat = Mat()
            val h = MatOfFloat(6.0f)
            Photo.fastNlMeansDenoising(yuvMat[ColorSpaceOperator.Y_CHANNEL], denoisedMat, h, 7, 21, Core.NORM_L1)

            // Save the noise and denoised images
            FileImageWriter.getInstance()?.saveMatrixToImage(yuvMat[ColorSpaceOperator.Y_CHANNEL], "noise_$i", ImageFileAttribute.FileType.JPEG)
            FileImageWriter.getInstance()?.saveMatrixToImage(denoisedMat, "denoise_$i", ImageFileAttribute.FileType.JPEG)

            // Merge channel then convert back to RGB
            yuvMat[ColorSpaceOperator.Y_CHANNEL].release()
            yuvMat[ColorSpaceOperator.Y_CHANNEL] = denoisedMat

            outputMatList[i] = ColorSpaceOperator.convertYUVtoRGB(yuvMat)
        }
    }

    fun getResult(): Array<Mat> {
        return outputMatList
    }

    private inner class DenoisingWorker(
        private val inputMat: Mat,
        private val indexCount: Int,
        semaphore: Semaphore
    ) : FlaggingThread(semaphore) {

        lateinit var outputMat: Mat

        override fun run() {
            Log.i(TAG, "Started denoising for image index $indexCount")

            // Perform denoising on energy channel only
            val yuvMat = ColorSpaceOperator.convertRGBToYUV(inputMat)
            val h = MatOfFloat(6.0f)

            val denoisedMat = Mat()
            Photo.fastNlMeansDenoising(yuvMat[ColorSpaceOperator.Y_CHANNEL], denoisedMat, h, 7, 21, Core.NORM_L1)

            // Save the noise and denoised images
            FileImageWriter.getInstance()?.saveMatrixToImage(yuvMat[ColorSpaceOperator.Y_CHANNEL], "noise_$indexCount", ImageFileAttribute.FileType.JPEG)
            FileImageWriter.getInstance()?.saveMatrixToImage(denoisedMat, "denoise_$indexCount", ImageFileAttribute.FileType.JPEG)

            // Merge channel then convert back to RGB
            yuvMat[ColorSpaceOperator.Y_CHANNEL].release()
            yuvMat[ColorSpaceOperator.Y_CHANNEL] = denoisedMat

            outputMat = ColorSpaceOperator.convertYUVtoRGB(yuvMat)

            finishWork()
            Log.i(TAG, "Finished denoising for image index $indexCount")
        }
    }

    companion object {
        private const val TAG = "DenoisingOperator"
    }
}
