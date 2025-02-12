package com.wangGang.eagleEye.io

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.media.Image
import android.media.ImageReader
import android.util.Size
import android.view.View
import com.wangGang.eagleEye.camera.CameraController
import com.wangGang.eagleEye.processing.ConcreteSuperResolution
import com.wangGang.eagleEye.processing.dehaze.SynthDehaze
import com.wangGang.eagleEye.ui.fragments.CameraViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImageReaderManager(
    private val context: Context,
    private val cameraController: CameraController,
    private val concreteSuperResolution: ConcreteSuperResolution,
    private val viewModel: CameraViewModel
) {

    fun initializeImageReader() {
        concreteSuperResolution.initialize(viewModel.getImageInputMap()!!)
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
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader?.acquireNextImage()
            image?.let { processImage(it) }
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
            viewModel.updateLoadingText("Processing Super Resolution...")
            handleSuperResolutionImage(bitmap)
        } else if (isDehazeEnabled) {
            viewModel.updateLoadingText("Processing Dehaze...")
            handleDehazeImage(bitmap)
        } else {
            viewModel.updateLoadingText("Processing Normal Image...")
            handleNormalImage(bitmap)
        }
    }
    private fun handleNormalImage(bitmap: Bitmap) {
        val matrix = Matrix()
        matrix.postRotate(90f)
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        CoroutineScope(Dispatchers.IO).launch {
            FileImageWriter.getInstance()!!.saveBitmapToUserDir(rotatedBitmap,ImageFileAttribute.FileType.JPEG)
            viewModel.setLoadingBoxVisible(false)
        }
    }
    private fun handleDehazeImage(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                SynthDehaze(context, viewModel).dehazeImage(bitmap)
            }
            viewModel.setLoadingBoxVisible(false)
        }
    }


    private fun handleSuperResolutionImage(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            viewModel.updateLoadingText("Saving Images...")
            val saveJob = launch {
                FileImageWriter.getInstance()?.saveImageToStorage(bitmap)?.let {
                    viewModel.addImageInput(it)
                }
            }

            saveJob.join() // Ensures the file is saved before checking the count

            if (viewModel.imageInputMap.value?.size == 10) {
                // Run super resolution asynchronously
                launch {
                    concreteSuperResolution.superResolutionImage(viewModel.imageInputMap.value!!)
                    viewModel.setLoadingBoxVisible(false)
                    viewModel.clearImageInputMap()
                }
            }
        }
    }

}