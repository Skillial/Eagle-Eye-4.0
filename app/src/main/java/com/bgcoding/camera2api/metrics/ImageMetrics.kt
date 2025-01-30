package com.bgcoding.camera2api.metrics

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.log10
import kotlin.math.sqrt

object ImageMetrics {
    fun getPSNR(I1: Mat, I2: Mat): Double {
        val mse = getMSE(I1, I2)
        val psnr = 10.0 * log10((255 * 255) / mse)
        return psnr
    }

    fun getMSE(I1: Mat, I2: Mat): Double {
        var s1 = Mat()
        Core.absdiff(I1, I2, s1) // |I1 - I2|

        s1.convertTo(s1, CvType.CV_32F) // cannot make a square on 8 bits
        s1 = s1.mul(s1) // |I1 - I2|^2

        val s = Core.sumElems(s1) // sum elements per channel
        s1.release()

        val sse = s.`val`[0] + s.`val`[1] + s.`val`[2] // sum channels

        if (sse <= 1e-10)  // for small values return zero
            return 0.0
        else {
            val mse = sse / (I1.channels() * I1.total()).toDouble()
            return mse
        }
    }

    fun getRMSE(I1: Mat, I2: Mat): Double {
        val mse = getMSE(I1, I2)
        return sqrt(mse)
    }

    fun getSSIM(i1: Mat, i2: Mat): Scalar {
        val C1 = 6.5025
        val C2 = 58.5225

        val I1 = Mat()
        val I2 = Mat()
        i1.convertTo(I1, CvType.CV_32F)
        i2.convertTo(I2, CvType.CV_32F)

        val I1_2 = I1.mul(I1)
        val I2_2 = I2.mul(I2)
        val I1_I2 = I1.mul(I2)

        val mu1 = Mat()
        val mu2 = Mat()
        Imgproc.GaussianBlur(I1, mu1, Size(11.0, 11.0), 1.5)
        Imgproc.GaussianBlur(I2, mu2, Size(11.0, 11.0), 1.5)

        I1.release()
        I2.release()

        val mu1_2 = mu1.mul(mu1)
        val mu2_2 = mu2.mul(mu2)
        val mu1_mu2 = mu1.mul(mu2)

        val sigma1_2 = Mat()
        val sigma2_2 = Mat()
        val sigma12 = Mat()

        Imgproc.GaussianBlur(I1_2, sigma1_2, Size(11.0, 11.0), 1.5)
        Core.subtract(sigma1_2, mu1_2, sigma1_2)

        Imgproc.GaussianBlur(I2_2, sigma2_2, Size(11.0, 11.0), 1.5)
        Core.subtract(sigma2_2, mu2_2, sigma2_2)

        Imgproc.GaussianBlur(I1_I2, sigma12, Size(11.0, 11.0), 1.5)
        Core.subtract(sigma12, mu1_mu2, sigma12)

        var t1 = Mat()
        val t2 = Mat()
        var t3 = Mat()
        val twoScalar = Scalar.all(2.0)
        val c1Scalar = Scalar.all(C1)
        val c2Scalar = Scalar.all(C2)

        Core.multiply(mu1_mu2, twoScalar, t1)
        Core.add(t1, c1Scalar, t1)
        Core.multiply(sigma12, twoScalar, t2)
        Core.add(t2, c2Scalar, t2)
        t3 = t1.mul(t2)

        Core.add(mu1_2, mu2_2, t1)
        Core.add(t1, c1Scalar, t1)
        Core.add(sigma1_2, sigma2_2, t2)
        Core.add(t2, c2Scalar, t2)
        t1 = t1.mul(t2)

        mu1_2.release()
        mu2_2.release()
        sigma1_2.release()
        sigma2_2.release()

        val ssim_map = Mat()
        Core.divide(t3, t1, ssim_map)

        var mssim = Scalar.all(0.0)
        mssim = Core.mean(ssim_map)

        t3.release()
        t1.release()
        ssim_map.release()
        return mssim
    }
}