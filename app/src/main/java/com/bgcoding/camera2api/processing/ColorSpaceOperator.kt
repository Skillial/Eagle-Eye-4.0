package com.bgcoding.camera2api.processing

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.*

object ColorSpaceOperator {

    const val Y_CHANNEL = 0
    const val U_CHANNEL = 1
    const val V_CHANNEL = 2

    const val R_CHANNEL = 2
    const val G_CHANNEL = 1
    const val B_CHANNEL = 0
    const val A_CHANNEL = 3

    /*
     * Converts a mat into its YUV format and separated into channels
     */
    @JvmStatic
    fun convertRGBToYUV(mat: Mat): Array<Mat> {
        val yuvMat = Mat()
        Imgproc.cvtColor(mat, yuvMat, Imgproc.COLOR_BGR2YUV)

        val matList: MutableList<Mat> = ArrayList()
        Core.split(yuvMat, matList)

        return matList.toTypedArray()
    }

    /*
     * Converts the yuv mat (assuming channels were separated in different matrices), into its RGB mat form
     */
    @JvmStatic
    fun convertYUVtoRGB(yuvMat: Array<Mat>): Mat {
        val rgbMat = Mat()
        Core.merge(Arrays.asList(*yuvMat), rgbMat)

        Imgproc.cvtColor(rgbMat, rgbMat, Imgproc.COLOR_YUV2BGR)

        return rgbMat
    }

    @JvmStatic
    fun rgbToGray(inputMat: Mat): Mat {
        val grayScaleMat = Mat()
        if (inputMat.channels() == 3 || inputMat.channels() == 4) {
            Imgproc.cvtColor(inputMat, grayScaleMat, Imgproc.COLOR_BGR2GRAY)
        } else {
            inputMat.copyTo(grayScaleMat)
        }

        return grayScaleMat
    }
}
