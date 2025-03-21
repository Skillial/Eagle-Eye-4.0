package com.wangGang.eagleEye.ui.activities

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.wangGang.eagleEye.databinding.ActivityPhotoBinding
import com.wangGang.eagleEye.ui.utils.PhotoActivityHelper


class PhotoActivity() : AppCompatActivity() {

    private lateinit var ivThumbnailPhoto: ImageView
    private lateinit var backButton: ImageButton
    private lateinit var binding: ActivityPhotoBinding
    private val photoActivityHelper: PhotoActivityHelper = PhotoActivityHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupView()
        loadPhoto()
    }

    private fun setupView() {
        assignViews()
        setupListeners()
    }

    private fun assignViews() {
        backButton = binding.btnBack
        ivThumbnailPhoto = binding.ivThumbnailPhoto
    }

    private fun setupListeners() {
        backButton.setOnClickListener {
            finish()
        }
    }

    private fun getImageUri(): String? {
        // Retrieve the image URI from intent
        val imageUri = intent.getStringExtra("imageUri")
        Log.d("PhotoActivity", "Loading: $imageUri")
        return imageUri
    }

    private fun loadPhoto() {
        val imageUri = getImageUri()

        // Log image size
        val imageSize = photoActivityHelper.getImageSize(this, Uri.parse(imageUri))
        Log.d("PhotoActivity", "Image size: $imageSize")

        Glide.with(this)
            .load(Uri.parse(imageUri))
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .dontAnimate()
            .into(ivThumbnailPhoto)
    }
}