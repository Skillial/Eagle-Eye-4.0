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
import com.wangGang.eagleEye.constants.ParameterConfig
import com.wangGang.eagleEye.processing.ConcreteSuperResolution
import com.wangGang.eagleEye.processing.dehaze.SynthDehaze
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
        if (handler != null && handler.looper.thread.isAlive) {
            Log.d("ImageReaderManager", "Handler is alive")
            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader?.acquireNextImage()
                image?.let { processImage(it) }
            }, handler)
        }
    }

    private fun processImage(image: Image) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        image.close()
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val isSuperResolutionEnabled = ParameterConfig.isSuperResolutionEnabled()
        val isDehazeEnabled = ParameterConfig.isDehazeEnabled()

        if (isSuperResolutionEnabled) {
            handleSuperResolutionImage(bitmap)
        } else if (isDehazeEnabled) {
            handleDehazeImage(bitmap)
        } else {
            handleNormalImage(bitmap)
        }

        Log.d("ImageReaderManager", "Image processed")
    }
    private fun handleNormalImage(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            FileImageWriter.getInstance()!!.saveBitmapToResultsDir(bitmap,ImageFileAttribute.FileType.JPEG, ResultType.AFTER)
            FileImageWriter.getInstance()!!.deleteImage("${DirectoryStorage.RESULT_ALBUM_NAME_PREFIX}/${ResultType.BEFORE}", ImageFileAttribute.FileType.JPEG)
            // delete "Before" Image
            viewModel.setLoadingBoxVisible(false)
        }
    }
    private fun handleDehazeImage(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.Main).launch {
            cameraController.closeCamera()
            withContext(Dispatchers.IO) {
                SynthDehaze(context, viewModel).dehazeImage(bitmap)
            }
            cameraController.openCamera()
            setImageReaderListener()
            viewModel.setLoadingBoxVisible(false)
        }
    }


    private fun handleSuperResolutionImage(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            viewModel.updateLoadingText("Saving Images")
            val saveJob = launch {
                FileImageWriter.getInstance()?.saveImageToStorage(bitmap)?.let {
                    viewModel.addImageInput(it)
                }

                if (viewModel.imageInputMap.value?.size == 1) {
                    FileImageWriter.getInstance()?.saveBitmapToResultsDir(bitmap, ImageFileAttribute.FileType.JPEG, ResultType.BEFORE)
                        ?.let { FileImageReader.getInstance()?.setBeforeUri(it) }
                }
            }
            saveJob.join() // Ensures the file is saved before checking the count

            if (viewModel.imageInputMap.value?.size == 10) {
                // Run super resolution asynchronously
                launch {
                    cameraController.closeCamera()
                    concreteSuperResolution.superResolutionImage(viewModel.imageInputMap.value!!)
                    viewModel.setLoadingBoxVisible(false)
                    viewModel.clearImageInputMap()
                    cameraController.openCamera()
                    setImageReaderListener()
                }
            }
        }
    }

}