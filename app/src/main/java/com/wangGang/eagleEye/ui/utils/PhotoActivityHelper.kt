package com.wangGang.eagleEye.ui.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log

class PhotoActivityHelper {

    fun getImageSize(context: Context, uri: Uri): Pair<Int, Int> {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true // Prevents loading the full image
            }

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            val imageWidth = options.outWidth
            val imageHeight = options.outHeight
            Log.d("PhotoActivity", "getImageSize() - Image width: $imageWidth, Image height: $imageHeight")

            Pair(imageWidth, imageHeight)
        } catch (e: Exception) {
            Log.e("PhotoActivity", "Failed to get image size: ${e.message}")
            Pair(0, 0) // Return 0,0 if the operation fails
        }
    }
}