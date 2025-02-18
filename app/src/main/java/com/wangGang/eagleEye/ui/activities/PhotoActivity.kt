package com.wangGang.eagleEye.ui.activities

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.wangGang.eagleEye.R
import java.io.File


class PhotoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo)

//        val imageView: ZoomableImageView = findViewById(R.id.photoView)
        val backButton: ImageButton = findViewById(R.id.backButton)
        val testImageView: ImageView = findViewById(R.id.testImageView)

        // Retrieve the image URI from intent
        val imageUri = intent.getStringExtra("imageUri")
        Log.d("PhotoActivity", "Loading: $imageUri");

        // Log image size
        val imageSize = getImageSize(this, Uri.parse(imageUri))
        Log.d("PhotoActivity", "Image size: $imageSize")

        Glide.with(this)
            .load(Uri.parse(imageUri))
            .dontAnimate()
            .into(testImageView)

        backButton.setOnClickListener {
            finish()
        }
    }

    private fun getImageSize(context: Context, uri: Uri): Pair<Int, Int> {
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