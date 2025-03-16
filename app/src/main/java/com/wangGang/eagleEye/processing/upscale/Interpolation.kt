package com.wangGang.eagleEye.processing.upscale

import android.graphics.Bitmap
import com.wangGang.eagleEye.constants.ParameterConfig
import com.wangGang.eagleEye.processing.imagetools.ImageOperator
import com.wangGang.eagleEye.ui.viewmodels.CameraViewModel
import org.opencv.core.Core
import org.opencv.imgproc.Imgproc

class Interpolation(private val viewModel: CameraViewModel) {
    fun upscaleImageWithImageSave(bitmap: Bitmap, scale: Float) {
        val oldMat = ImageOperator.bitmapToMat(bitmap)
        return ImageOperator.performInterpolationWithImageSave(oldMat, scale)
    }

    fun upscaleImage(bitmap: Bitmap, scale: Float): Bitmap {
        val oldMat = ImageOperator.bitmapToMat(bitmap)
        return ImageOperator.performJNIInterpolationWithMerge(oldMat, scale)
    }
}