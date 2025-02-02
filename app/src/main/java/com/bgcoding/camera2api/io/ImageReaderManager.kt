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
import com.bgcoding.camera2api.camera.CameraController
import com.bgcoding.camera2api.processing.ConcreteSuperResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bgcoding.camera2api.processing.dehaze.SynthDehaze

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
                image?.let { processImage(it) }
            }
        }, cameraController.getHandler())
    }

    private fun processImage(image: Image) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        image.close()
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val sharedPreferences = context.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE)
        val isSuperResolutionEnabled = sharedPreferences.getBoolean("super_resolution_enabled", false)
        val isDehazeEnabled = sharedPreferences.getBoolean("dehaze_enabled", false)
        if (isSuperResolutionEnabled) {
            handleSuperResolutionImage(bitmap)
        } else if (isDehazeEnabled) {
            handleDehazeImage(bitmap)
        } else {
            handleNormalImage(bitmap)
        }
    }
    private fun handleNormalImage(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            FileImageWriter.getInstance()?.saveImageToStorage(bitmap)
            withContext(Dispatchers.Main) {
                loadingBox.visibility = View.GONE
            }
        }
    }
    private fun handleDehazeImage(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                SynthDehaze(context).dehazeImage(bitmap)
            }
            loadingBox.visibility = View.GONE
        }
    }


    private fun handleSuperResolutionImage(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            val saveJob = launch {
                FileImageWriter.getInstance()?.saveImageToStorage(bitmap)?.let {
                    imageInputMap.add(it)
                }
            }

            saveJob.join() // Ensures the file is saved before checking the count

            if (imageInputMap.size == 10) {
                // Run super resolution asynchronously
                launch {
                    concreteSuperResolution.superResolutionImage(imageInputMap)
                    withContext(Dispatchers.Main) {
                        loadingBox.visibility = View.GONE
                    }
                    imageInputMap.clear()
                }
            }
        }
    }

}