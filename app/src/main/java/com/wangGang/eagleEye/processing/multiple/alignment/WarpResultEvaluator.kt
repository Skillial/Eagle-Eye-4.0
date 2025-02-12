package com.wangGang.eagleEye.processing.multiple.alignment

import android.util.Log
import com.wangGang.eagleEye.constants.ParameterConfig
import com.wangGang.eagleEye.io.FileImageReader
import com.wangGang.eagleEye.io.ImageFileAttribute
import com.wangGang.eagleEye.io.JSONSaver
import com.wangGang.eagleEye.processing.imagetools.ImageOperator
import com.wangGang.eagleEye.processing.imagetools.ImageOperator.edgeSobelMeasure
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

/**
 * Operator that verifies the quality of warped images by measuring its norm against the first reference  LR image.
 * If it's above the threshold, it will be filtered out.
 * Created by NeilDG on 12/12/2016.
 */
class WarpResultEvaluator(
    private val referenceMat: Mat,
    private val warpedMatNames: Array<String>,
    private val medianAlignedNames: Array<String>
) {
    val chosenAlignedNames: Array<String?> =
        arrayOfNulls(warpedMatNames.size) //output the chosen aligned names for mean fusion here

    fun perform() {
        referenceMat.convertTo(
            this.referenceMat, CvType.CV_16UC(
                referenceMat.channels()
            )
        )
        val sobelReferenceMeasure = edgeSobelMeasure(this.referenceMat, true)

        val warpedDifferenceList =
            this.measureDifference(this.referenceMat, sobelReferenceMeasure, this.warpedMatNames)
        val medianDifferenceList =
            this.measureDifference(
                this.referenceMat,
                sobelReferenceMeasure,
                this.medianAlignedNames
            )

        referenceMat.release()
        //assessWarpedImages(sobelReferenceMeasure, warpedDifferenceList, this.warpedMatNames);
        this.chooseAlignedImages(
            warpedDifferenceList, medianDifferenceList,
            this.warpedMatNames,
            this.medianAlignedNames
        )
    }

    private fun measureDifference(
        referenceMat: Mat,
        referenceSobelMeasure: Int,
        compareNames: Array<String>
    ): IntArray {
        val warpedDifferenceList = IntArray(compareNames.size)
        for (i in compareNames.indices) {
            val fileImageReader = FileImageReader.getInstance()

            if (fileImageReader != null) {
                Log.d(TAG, "Processing file: ${compareNames[i]}")
                val warpedMat: Mat = fileImageReader.imReadOpenCV(compareNames[i], ImageFileAttribute.FileType.JPEG)

                val maskMat = ImageOperator.produceMask(warpedMat)

                warpedMat.convertTo(warpedMat, CvType.CV_16UC(warpedMat.channels()))

                Log.e(
                    TAG, "Reference mat type: " + CvType.typeToString(referenceMat.type()) +
                            " Warped mat type: " + CvType.typeToString(warpedMat.type()) +
                            " Warped mat name: " + compareNames[i]
                )
                Core.add(referenceMat, warpedMat, warpedMat)

                maskMat.release()

                warpedDifferenceList[i] = edgeSobelMeasure(warpedMat, true) - referenceSobelMeasure
                Log.d(
                    TAG,
                    "Non zero elems in difference mat for " + compareNames[i] + " : " + warpedDifferenceList[i]
                )

                warpedMat.release()
            } else {
                Log.e(TAG, "FileImageReader instance is null. Cannot process file: ${compareNames[i]}")
            }
        }

        return warpedDifferenceList
    }

    private fun chooseAlignedImages(
        warpedResults: IntArray,
        medianAlignedResults: IntArray,
        warpedMatNames: Array<String>,
        medianAlignedNames: Array<String>
    ) {
        var warpedMean = 0.0f

        for (i in warpedResults.indices) {
            warpedMean += warpedResults[i].toFloat()
        }

        warpedMean = (warpedMean * 1.0f) / warpedResults.size

        for (i in chosenAlignedNames.indices) {
            val absDiffFromMean =
                abs((warpedResults[i] - warpedMean).toDouble()).toFloat()
            val medianAlignDiff =
                abs((medianAlignedResults[i] - warpedMean).toDouble()).toFloat()
            if (warpedResults[i] < medianAlignedResults[i] && absDiffFromMean < MAX_THRESHOLD) {
                chosenAlignedNames[i] = warpedMatNames[i]
            } else {
                chosenAlignedNames[i] = medianAlignedNames[i]
            }
            Log.d(TAG, "Chosen image name: " + chosenAlignedNames[i])
        }
    }

    companion object {
        private const val TAG = "WarpResultEvaluator"
        private const val MAX_THRESHOLD = 200000

        private fun assessWarpedImages(
            referenceSobelMeasure: Int,
            warpedResults: IntArray,
            warpedMatNames: Array<String>
        ) {
            val average: Float
            var sum = 0
            for (i in warpedResults.indices) {
                Log.d(
                    TAG,
                    "Non zero elems in edge sobel mat for " + warpedMatNames[i] + " : " + warpedResults[i]
                )
                sum += warpedResults[i]
            }

            average = (sum * 1.0f) / warpedResults.size
            Log.d(
                TAG,
                "Average non zero difference: $average"
            )

            val sobelReferenceDifferences =
                IntArray(warpedResults.size) //difference from the reference sobel measure
            for (i in warpedResults.indices) {
                sobelReferenceDifferences[i] = warpedResults[i] - referenceSobelMeasure
                Log.d(
                    TAG,
                    "Non zero elems in difference mat for " + warpedMatNames[i] + " : " + sobelReferenceDifferences[i]
                )
            }

            val warpChoice: Int = ParameterConfig.getPrefsInt(
                ParameterConfig.WARP_CHOICE_KEY,
                1
            )
            JSONSaver.debugWriteEdgeConsistencyMeasure(
                warpChoice,
                warpedResults,
                sobelReferenceDifferences,
                warpedMatNames
            )
        }
    }
}