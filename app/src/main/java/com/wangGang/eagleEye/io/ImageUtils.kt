package com.wangGang.eagleEye.io

import android.graphics.Bitmap
import android.graphics.Matrix

class ImageUtils {

    companion object {
        fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
            val matrix = Matrix()
            matrix.postRotate(degrees)
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }
}