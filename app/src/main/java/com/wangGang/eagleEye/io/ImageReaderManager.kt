package com.wangGang.eagleEye.io

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.media.Image
import android.media.ImageReader
import android.util.Log
import android.util.Size
import com.wangGang.eagleEye.camera.CameraController
import com.wangGang.eagleEye.camera.CameraController.Companion.MAX_BURST_IMAGES
import com.wangGang.eagleEye.constants.ParameterConfig
import com.wangGang.eagleEye.processing.ConcreteSuperResolution
import com.wangGang.eagleEye.processing.commands.Dehaze
import com.wangGang.eagleEye.processing.commands.SuperResolution
import com.wangGang.eagleEye.processing.commands.Upscale
import com.wangGang.eagleEye.processing.commands.ShadowRemoval
import com.wangGang.eagleEye.processing.dehaze.SynthDehaze
import com.wangGang.eagleEye.processing.denoise.AKDT
import com.wangGang.eagleEye.processing.shadow_remove.SynthShadowRemoval
import com.wangGang.eagleEye.processing.upscale.Interpolation
import com.wangGang.eagleEye.ui.utils.ProgressManager
import com.wangGang.eagleEye.ui.viewmodels.CameraViewModel
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
    private lateinit var imageReader: ImageReader
    private var imageList = mutableListOf<Bitmap>()
    private var saveAfter = true
    fun initializeImageReader() {
        concreteSuperResolution.initialize(viewModel.getImageInputMap()!!)
        val highestResolution = cameraController.getHighestResolution()
        setupImageReader(highestResolution)
    }

    private fun setupImageReader(highestResolution: Size?) {
        imageReader = if (highestResolution != null) {
            Log.d("ImageReaderManager", "Setting up image reader with resolution: ${highestResolution.width}x${highestResolution.height}")
            ImageReader.newInstance(highestResolution.width, highestResolution.height, ImageFormat.JPEG, 20)
        } else {
            ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1)
        }
        cameraController.setImageReader(imageReader)
    }

    fun setImageReaderListener() {
        imageReader = cameraController.getImageReader()
        val handler = cameraController.getHandler()

        if (handler.looper.thread.isAlive) {
            Log.d("ImageReaderManager", "Handler is alive")

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader?.acquireNextImage()
                image?.let {
                    CoroutineScope(Dispatchers.Main).launch {
                        waitForImageBurst(it) // Now we call the suspend function properly
                    }
                }
            }, handler)
        }
    }

    private suspend fun waitForImageBurst(image: Image) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        image.close()
        imageList.add(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        val totalCaptures = if (ParameterConfig.isSuperResolutionEnabled()) MAX_BURST_IMAGES else 1
        Log.d("ImageReaderManager", "Total captures: $totalCaptures")
        if (imageList.size == totalCaptures) {
            ProgressManager.getInstance().showFirstTask()
            processImage()
        }
    }


    private suspend fun processImage() {
        val oldBitmap = imageList[0]
        val order = ParameterConfig.getProcessingOrder()
        cameraController.closeCamera()
        if (order.isNotEmpty()) {
            Log.d("order", ""+order)
            for (each in order) {
                Log.d("ImageReaderManager", "Processing image with: $each")
                when (each) {
                    Dehaze.displayName -> handleDehazeImage()
                    SuperResolution.displayName -> handleSuperResolutionImage()
                    Upscale.displayName -> handleUpscaleImage()
                    ShadowRemoval.displayName -> handleShadowRemoval()
                }
            }
        }
        saveImages(oldBitmap)
        setImageReaderListener()
        cameraController.openCamera()
        viewModel.setLoadingBoxVisible(false)
    }

    private suspend fun handleUpscaleImage() {
        Log.d("ImageReaderManager", "Upscaling image")
        val newImageList = mutableListOf<Bitmap>()
        val scale =  ParameterConfig.getScalingFactor().toFloat()
        for (each in imageList.toList()){
            if (scale >= 8){
                withContext(Dispatchers.IO) {
                    Interpolation(viewModel).upscaleImageWithImageSave(each, scale)
                }
            } else {
                val newBitmap = withContext(Dispatchers.IO) {
                    Interpolation(viewModel).upscaleImage(each, scale)
                }
                newImageList.add(newBitmap)
            }
        }
        ProgressManager.getInstance().nextTask()
        if (scale < 8){
            imageList.clear()
            imageList.addAll(newImageList)
        } else {
            saveAfter = false
        }
    }

    private fun saveImages(oldBitmap: Bitmap) {
        FileImageWriter.getInstance()!!
            .saveBitmapToResultsDir(oldBitmap, ImageFileAttribute.FileType.PNG, ResultType.BEFORE)
        if (saveAfter) {
            FileImageWriter.getInstance()!!
                .saveBitmapToResultsDir(imageList[0], ImageFileAttribute.FileType.PNG, ResultType.AFTER)
            imageList.clear()
        }
    }

    private suspend fun handleDehazeImage() {
        val newImageList = mutableListOf<Bitmap>()
        for (each in imageList.toList()){
            val newBitmap = withContext(Dispatchers.IO) {
//                SynthDehaze(context, viewModel).dehazeImage(each)
                AKDT(context, viewModel).denoiseImage(each)
            }
            newImageList.add(newBitmap)
        }
        imageList.clear()
        imageList.addAll(newImageList)
    }

    private suspend fun handleShadowRemoval() {
        val newImageList = mutableListOf<Bitmap>()

        for (each in imageList.toList()) {
            val newBitmap = withContext(Dispatchers.IO) {
                SynthShadowRemoval(context, viewModel).removeShadow(each)
            }

            // Save the image to storage
            FileImageWriter.getInstance()?.saveImageToStorage(newBitmap)?.let { savedFile ->
                viewModel.addImageInput(savedFile)
            }

            newImageList.add(newBitmap)
        }
        imageList.clear()
        imageList.addAll(newImageList)
    }

    private suspend fun handleSuperResolutionImage() = withContext(Dispatchers.IO) {
        val newImageList = mutableListOf<Bitmap>()
        // Process each image sequentially

        for (each in imageList.toList()) {
            viewModel.updateLoadingText("Saving Images")
            // Save image synchronously
            FileImageWriter.getInstance()?.saveImageToStorage(each)?.let {
                viewModel.addImageInput(it)
            }
        }
        imageList.clear()
        if (viewModel.imageInputMap.value?.size == 10) {
            // Run super resolution and update image list immediately
            newImageList.add(concreteSuperResolution.superResolutionImage(viewModel.imageInputMap.value!!))
            viewModel.clearImageInputMap()
        }

        imageList.addAll(newImageList)
    }


}