package com.wangGang.eagleEye.assessment

import android.util.Log
import com.wangGang.eagleEye.processing.ColorSpaceOperator.convertRGBToYUV
import com.wangGang.eagleEye.thread.FlaggingThread
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.util.concurrent.Semaphore

class InputImageEnergyReader(semaphore: Semaphore?, private val inputImagePath: String) :
    FlaggingThread(semaphore!!) {
    var outputMat: Mat? = null
        private set

    override fun run() {
        Log.d(TAG, "Started energy reading for " + this.inputImagePath)

        val inputMat = Imgcodecs.imread(this.inputImagePath)
        Imgproc.resize(inputMat, inputMat, Size(), 0.125, 0.125, Imgproc.INTER_AREA) // downsampled

        val yuvMat = convertRGBToYUV(inputMat)

        this.outputMat = yuvMat[0]
        Log.d(TAG, "Output mat size: " + this.outputMat)
        inputMat.release()

        this.finishWork()

        Log.d(TAG, "Ended energy reading! Success!")
    }


    companion object {
        private const val TAG = "InputImageEnergyReader"
    }
}