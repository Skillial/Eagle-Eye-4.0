package com.bgcoding.camera2api.model.multiple

import android.util.Log
import org.opencv.core.Core
import org.opencv.core.Mat

/**
 * Stores the sharpness measure of images. To retrieve a sharpness measure from a certain image, use the image index.
 * Created by NeilDG on 7/20/2016.
 */
class SharpnessMeasure private constructor() {

    private val TAG = "SharpnessMeasure"

    private var latestResult: SharpnessResult? = null

    companion object {
        private var sharedInstance: SharpnessMeasure? = null

        fun getSharedInstance(): SharpnessMeasure {
            return sharedInstance ?: SharpnessMeasure().also {
                sharedInstance = it
            }
        }

        fun initialize() {
            sharedInstance = SharpnessMeasure()
        }

        fun destroy() {
            sharedInstance = null
        }
    }

    fun measureSharpness(edgeMatList: Array<Mat>): SharpnessResult {
        val sharpnessResult = SharpnessResult()
        sharpnessResult.sharpnessValues = DoubleArray(edgeMatList.size)

        var sum = 0.0
        for (i in edgeMatList.indices) {
            sharpnessResult.sharpnessValues[i] = measure(edgeMatList[i])
            sum += sharpnessResult.sharpnessValues[i]
        }

        // get mean
        sharpnessResult.mean = sum / edgeMatList.size

        // trimmed values that do not meet the mean
        val trimMatList = mutableListOf<Double>()
        val indexList = mutableListOf<Int>()

        for (i in edgeMatList.indices) {
            if (sharpnessResult.sharpnessValues[i] >= sharpnessResult.mean) {
                trimMatList.add(sharpnessResult.sharpnessValues[i])
                indexList.add(i)
            }
        }

        // store index values
        sharpnessResult.trimmedIndexes = indexList.toIntArray()

        // get best
        var bestSharpness = 0.0
        var leastSharpness = 99999.0
        for (i in sharpnessResult.sharpnessValues.indices) {
            if (sharpnessResult.sharpnessValues[i] >= bestSharpness) {
                sharpnessResult.bestIndex = i
                bestSharpness = sharpnessResult.sharpnessValues[i]
            }

            if (sharpnessResult.sharpnessValues[i] <= leastSharpness) {
                sharpnessResult.leastIndex = i
                leastSharpness = sharpnessResult.sharpnessValues[i]
            }
        }

        this.latestResult = sharpnessResult

        trimMatList.clear()
        indexList.clear()

        return this.latestResult!!
    }

    fun getLatestResult(): SharpnessResult? {
        return this.latestResult
    }

    fun measure(edgeMat: Mat): Double {
        val withValues = Core.countNonZero(edgeMat)
        val dimension = edgeMat.cols() * edgeMat.rows()

        return withValues.toDouble() / dimension
    }

    fun trimMatList(inputMatList: Array<Mat>, sharpnessResult: SharpnessResult, weight: Double): Array<Mat> {
        val trimMatList = mutableListOf<Mat>()
        val weightedMean = sharpnessResult.mean + (sharpnessResult.mean * weight)

        for (i in inputMatList.indices) {
            Log.d(TAG, "Value: ${sharpnessResult.sharpnessValues[i]} Mean: ${sharpnessResult.mean} Weighted mean: $weightedMean")
            if (sharpnessResult.sharpnessValues[i] >= weightedMean) {
                Log.d(TAG, "Selected input mat: $i")
                trimMatList.add(inputMatList[i])
            } else {
                inputMatList[i].release()
            }
        }

        return trimMatList.toTypedArray()
    }

    fun trimMatList(inputLength: Int, sharpnessResult: SharpnessResult, weight: Double): Array<Int> {
        val trimMatList = mutableListOf<Int>()
        for (i in 0 until inputLength) {
            if (sharpnessResult.sharpnessValues[i] >= sharpnessResult.mean + weight) {
                trimMatList.add(i)
            }
        }

        return trimMatList.toTypedArray()
    }


    class SharpnessResult {
        var sharpnessValues: DoubleArray = DoubleArray(0)
        var trimmedIndexes: IntArray = IntArray(0)

        var mean: Double = 0.0

        var bestIndex: Int = 0
        var leastIndex: Int = 0



        fun getOutsideLeastIndex(): Int {
            return this.leastIndex
        }

    }
}