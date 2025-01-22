package com.bgcoding.camera2api.io

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.util.Log
import android.util.Size
import android.view.View
import com.bgcoding.camera2api.MainActivity
import com.bgcoding.camera2api.camera.CameraController
import com.bgcoding.camera2api.processing.ConcreteSuperResolution

class ImageReaderManager(
    private val context: Context,
    private val cameraController: CameraController,
    private val imageInputMap: MutableList<String>,
    private val concreteSuperResolution: ConcreteSuperResolution,
    private val loadingBox: View
) {

    fun initializeImageReader() {
        concreteSuperResolution.initialize(imageInputMap)
        val highestResolution = cameraController.getHighestResolution()
        setupImageReader(highestResolution)
        setImageReaderListener()
    }

    private fun setupImageReader(highestResolution: Size?) {
        val imageReader: ImageReader = if (highestResolution != null) {
            ImageReader.newInstance(highestResolution.width, highestResolution.height, ImageFormat.JPEG, 20)
        } else {
            ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1)
        }
        cameraController.setImageReader(imageReader)
    }

    private fun setImageReaderListener() {
        val imageReader = cameraController.getImageReader()
        imageReader.setOnImageAvailableListener(object : ImageReader.OnImageAvailableListener {
            override fun onImageAvailable(reader: ImageReader?) {
                val image = reader?.acquireNextImage()
                image?.let {
                    processImage(it)
                }
            }
        }, cameraController.getHandler())
    }

    private fun processImage(image: Image) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        image.close()

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        handleImage(bitmap)
    }

    private fun handleImage(bitmap: Bitmap) {
        val sharedPreferences = context.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE)
        val isSuperResolutionEnabled = sharedPreferences.getBoolean("super_resolution_enabled", false)

        if (isSuperResolutionEnabled) {
            Log.i("Main", "Super Resolution is toggled. Performing Super Resolution.")
            FileImageWriter.getInstance()?.saveImageToStorage(bitmap)?.let { imageInputMap.add(it) }
            if (imageInputMap.size == 5) {
                concreteSuperResolution.superResolutionImage(imageInputMap)
                imageInputMap.clear()
                (context as MainActivity).runOnUiThread {
                    loadingBox.visibility = View.GONE
                }
            }
        } else {
            Log.i("Main", "No IE is toggled. Saving a single image to device.")
            FileImageWriter.getInstance()?.saveImageToStorage(bitmap)
            (context as MainActivity).runOnUiThread {
                loadingBox.visibility = View.GONE
            }
        }
    }
}