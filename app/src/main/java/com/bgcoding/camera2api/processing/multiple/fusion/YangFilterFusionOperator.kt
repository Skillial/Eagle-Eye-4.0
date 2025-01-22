package com.bgcoding.camera2api.processing.filters

import android.util.Log
import com.bgcoding.camera2api.processing.imagetools.ImageOperator
import com.bgcoding.camera2api.processing.imagetools.MatMemory
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * Performs a meanwise fusion on all interpolated images
 * Created by NeilDG on 7/7/2016.
 */
class YangFilterFusionOperator(private val combineMatList: Array<Mat>) {

    companion object {
        private const val TAG = "MeanFusionOperator"
    }

    private lateinit var outputMat: Mat

    fun perform() {
        val rows = combineMatList[0].rows()
        val cols = combineMatList[0].cols()

        // Initialize matrices for summing and division
        val sumMat = Mat.zeros(rows, cols, CvType.CV_32FC(combineMatList[0].channels()))
        val divMat = Mat.zeros(rows, cols, CvType.CV_32FC1)
        val maskMat = Mat()

        // Iterate through the list of matrices
        for (hrMat in combineMatList) {
            hrMat.convertTo(hrMat, CvType.CV_32FC(hrMat.channels()))
            ImageOperator.produceMask(hrMat, maskMat)

            Log.d(TAG, "CombineMat size: ${hrMat.size()} sumMat size: ${sumMat.size()}")
            Core.add(hrMat, sumMat, sumMat, maskMat, CvType.CV_32FC(hrMat.channels()))

            maskMat.convertTo(maskMat, CvType.CV_32FC1)
            Core.add(maskMat, divMat, divMat)

            hrMat.release()
        }

        maskMat.release()

        // Split the sumMat into its channels
        val splittedSumMat = mutableListOf<Mat>()
        Core.split(sumMat, splittedSumMat)

        // Divide each channel by divMat
        for (i in splittedSumMat.indices) {
            Core.divide(splittedSumMat[i], divMat, splittedSumMat[i])
        }

        divMat.release()
        sumMat.release()

        // Merge the channels back into the output matrix
        outputMat = Mat.zeros(rows, cols, CvType.CV_32FC(combineMatList[0].channels()))
        Core.merge(splittedSumMat, outputMat)

        // Clean up the split matrices
        MatMemory.releaseAll(splittedSumMat, false)
        splittedSumMat.clear()

        // Convert the output matrix to 8-bit unsigned channels
        outputMat.convertTo(outputMat, CvType.CV_8UC(combineMatList[0].channels()))
    }

    fun getResult(): Mat {
        return outputMat
    }
}