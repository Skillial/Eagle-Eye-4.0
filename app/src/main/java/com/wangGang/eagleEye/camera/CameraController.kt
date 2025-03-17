package com.wangGang.eagleEye.camera

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.media.MediaActionSound
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import com.wangGang.eagleEye.constants.ParameterConfig
import com.wangGang.eagleEye.ui.utils.ProgressManager
import com.wangGang.eagleEye.ui.viewmodels.CameraViewModel
import kotlin.math.max

class CameraController(private val context: Context, private val viewModel: CameraViewModel) {

    companion object {
        // Constants
        const val MAX_BURST_IMAGES = 10

        @Volatile
        private var instance: CameraController? = null

        fun initialize(context: Context, viewModel: CameraViewModel): CameraController {
            return instance ?: synchronized(this) {
                instance ?: CameraController(context, viewModel).also {
                    it.initializeCamera()
                    instance = it
                }
            }
        }

        fun destroy() {
            instance = null
        }

        fun getInstance(): CameraController {
            return instance ?: throw IllegalStateException("Singleton is not initialized, call initialize(context) first.")
        }
    }

    // Properties
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraDevice: CameraDevice
    private lateinit var imageReader: ImageReader
    private lateinit var handler: Handler
    private lateinit var handlerThread: HandlerThread
    private lateinit var captureRequest: CaptureRequest.Builder
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private lateinit var preview: TextureView

    // This will hold our chosen preview size
    private lateinit var optimalPreviewSize: Size

    private var cameraId: String = ""

    fun deviceSupportsZSL(cameraManager: CameraManager, cameraId: String): Boolean {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        // Adjust this check if needed; here we assume PRIVATE_REPROCESSING indicates ZSL support
        return capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING) == true
    }

    fun captureImage() {
        val totalCaptures = if (ParameterConfig.isSuperResolutionEnabled()) MAX_BURST_IMAGES else 1

        // Choose capture template based on ZSL support
        val captureTemplate = if (deviceSupportsZSL(cameraManager, cameraId)) {
            CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG
        } else {
            CameraDevice.TEMPLATE_STILL_CAPTURE
        }

        // Create and configure the capture request builder once
        val captureBuilder = cameraDevice.createCaptureRequest(captureTemplate)
        captureBuilder.addTarget(imageReader.surface)
        captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)

        // Build the burst capture list using the same builder if settings don't change
        val captureList = MutableList(totalCaptures) { captureBuilder.build() }

        playShutterSound()

        viewModel.setLoadingBoxVisible(true)

        cameraCaptureSession.captureBurst(
            captureList,
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    Log.d("BurstCapture", "Capture completed")
                }
            },
            null
        )
    }

    /**
     * Adjusts the TextureView transform based on the optimal preview size.
     * Uses Matrix.ScaleToFit.CENTER so that the entire preview is visible (i.e. no cropping)
     * which better represents the field of view of the captured image.
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        val rotation = (context as? Activity)?.windowManager?.defaultDisplay?.rotation ?: return
        val matrix = Matrix()

        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        // When rotated, width and height may need to be swapped.
        val bufferRect = RectF(0f, 0f, optimalPreviewSize.height.toFloat(), optimalPreviewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (rotation == android.view.Surface.ROTATION_90 || rotation == android.view.Surface.ROTATION_270) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            // Use CENTER scaling to show the entire preview without cropping.
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.CENTER)
            val scale = max(viewWidth.toFloat() / optimalPreviewSize.height, viewHeight.toFloat() / optimalPreviewSize.width)
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate(90f * (rotation - 2), centerX, centerY)
        } else if (rotation == android.view.Surface.ROTATION_180) {
            matrix.postRotate(180f, centerX, centerY)
        }
        preview.setTransform(matrix)
    }

    fun setPreview(textureView: TextureView) {
        preview = textureView
        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                configureTransform(width, height)
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                configureTransform(width, height)
            }

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                return true
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
        }
    }

    @SuppressLint("MissingPermission")
    fun openCamera() {
        initializeHandlerThread()

        Log.d("CameraController", "Opening camera")
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0
                captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

                val surfaceTexture = preview.surfaceTexture

                // Determine the optimal preview size from the available sizes.
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val choices = map?.getOutputSizes(SurfaceTexture::class.java)
                    ?: arrayOf(Size(preview.width, preview.height))
                optimalPreviewSize = chooseOptimalSize(choices, preview.width, preview.height)

                // Set the SurfaceTexture buffer size to the optimal preview size.
                surfaceTexture!!.setDefaultBufferSize(optimalPreviewSize.width, optimalPreviewSize.height)

                val surface = Surface(surfaceTexture)
                captureRequest.addTarget(surface)

                val surfaces = listOf(surface, imageReader.surface)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val outputConfigurations = surfaces.map { OutputConfiguration(it) }
                    val sessionConfiguration = SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR,
                        outputConfigurations,
                        context.mainExecutor,
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(p0: CameraCaptureSession) {
                                cameraCaptureSession = p0
                                cameraCaptureSession.setRepeatingRequest(
                                    captureRequest.build(),
                                    null,
                                    handler
                                )
                            }

                            override fun onConfigureFailed(p0: CameraCaptureSession) {
                                Log.e("CameraCaptureSession", "Configuration failed")
                            }
                        }
                    )
                    cameraDevice.createCaptureSession(sessionConfiguration)
                } else {
                    cameraDevice.createCaptureSession(
                        surfaces,
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(p0: CameraCaptureSession) {
                                cameraCaptureSession = p0
                                cameraCaptureSession.setRepeatingRequest(
                                    captureRequest.build(),
                                    null,
                                    handler
                                )
                            }

                            override fun onConfigureFailed(p0: CameraCaptureSession) {
                                Log.e("CameraCaptureSession", "Configuration failed")
                            }
                        },
                        handler
                    )
                }
            }

            override fun onDisconnected(p0: CameraDevice) {
                Log.e("CameraController", "Camera disconnected")
            }

            override fun onError(camera: CameraDevice, error: Int) {
                Log.e("CameraController", "Error opening camera: $error")
            }
        }, handler)
    }

    /**
     * Chooses the smallest preview size that is at least as large as the TextureView.
     */
    private fun chooseOptimalSize(choices: Array<Size>, textureViewWidth: Int, textureViewHeight: Int): Size {
        val bigEnough = choices.filter { it.width >= textureViewWidth && it.height >= textureViewHeight }
        return if (bigEnough.isNotEmpty()) {
            bigEnough.minByOrNull { it.width * it.height }!!
        } else {
            choices[0]
        }
    }

    private fun getCameraId(lensFacing: Int): String {
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == lensFacing) {
                return cameraId
            }
        }
        return ""
    }

    fun switchCamera(textureView: TextureView) {
        val currentLensFacing = cameraManager.getCameraCharacteristics(cameraId)
            .get(CameraCharacteristics.LENS_FACING) ?: return

        val newLensFacing = if (currentLensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            CameraCharacteristics.LENS_FACING_BACK
        } else {
            CameraCharacteristics.LENS_FACING_FRONT
        }

        cameraDevice.close()
        cameraId = getCameraId(newLensFacing)

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                return true
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
        }

        if (textureView.isAvailable) {
            openCamera()
        }
    }

    // Helpers
    fun getHighestResolution(): Size? {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(ImageFormat.JPEG)
        return sizes?.sortedWith(compareBy { it.width * it.height })?.last()
    }

    private fun playShutterSound() {
        val sound = MediaActionSound()
        sound.play(MediaActionSound.SHUTTER_CLICK)
    }

    fun getSensorOrientation(): Int {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        return characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
    }

    // Getters
    fun getCameraCharacteristics(): CameraCharacteristics {
        return cameraManager.getCameraCharacteristics(cameraId)
    }

    fun getCameraDevice(): CameraDevice {
        return cameraDevice
    }

    fun getHandler(): Handler {
        return handler
    }

    fun getCameraManager(): CameraManager {
        return cameraManager
    }

    fun getCameraCaptureSession(): CameraCaptureSession {
        return cameraCaptureSession
    }

    fun getImageReader(): ImageReader {
        return imageReader
    }

    fun getCaptureRequest(): CaptureRequest.Builder {
        return captureRequest
    }

    // Setters
    fun setCameraCaptureSession(cameraCaptureSession: CameraCaptureSession) {
        this.cameraCaptureSession = cameraCaptureSession
    }

    fun setCameraDevice(cameraDevice: CameraDevice) {
        this.cameraDevice = cameraDevice
    }

    fun setImageReader(imageReader: ImageReader) {
        this.imageReader = imageReader
    }

    fun setCaptureRequest(captureRequest: CaptureRequest.Builder) {
        this.captureRequest = captureRequest
    }

    // Lifecycle
    fun initializeCamera() {
        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = getCameraId(CameraCharacteristics.LENS_FACING_BACK)
        initializeHandlerThread()
    }

    private fun initializeHandlerThread() {
        if (!::handlerThread.isInitialized || !handlerThread.isAlive) {
            handlerThread = HandlerThread("CameraBackgroundThread")
            handlerThread.start()
            handler = Handler(handlerThread.looper)
        }
    }

    fun closeCamera() {
        try {
            Log.d("CameraController", "Closing camera")
            cameraCaptureSession.close()
            cameraDevice.close()
            handlerThread.quitSafely()
        } catch (e: Exception) {
            Log.e("CameraController", "Error closing camera: ", e)
        }
    }
}
