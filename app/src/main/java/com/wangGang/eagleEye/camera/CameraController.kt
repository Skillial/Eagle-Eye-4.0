package com.wangGang.eagleEye.camera

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.ImageFormat
import android.graphics.Rect
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
import kotlin.math.abs

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
    private var cameraId: String = ""
    var zoomLevel = 1f
    private var maxZoom = 1f
    private var hasFlash: Boolean = false
    private var supportsHdr: Boolean = false
    private lateinit var supportedAwbModes: IntArray
    fun deviceSupportsZSL(cameraManager: CameraManager, cameraId: String): Boolean {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        // Adjust this check if needed; here we assume PRIVATE_REPROCESSING indicates ZSL support
        return capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING) == true
    }

    fun captureImage() {
        try {
            cameraCaptureSession.stopRepeating()
            cameraCaptureSession.abortCaptures()
        } catch (e: Exception) {
            Log.e("CameraController", "Failed to stop repeating: ${e.message}")
        }

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
        // Apply the current zoom level to the capture request
        captureRequest.get(CaptureRequest.SCALER_CROP_REGION)?.let {
            captureBuilder.set(CaptureRequest.SCALER_CROP_REGION, it)
        }
        captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        applyCommonCaptureSettings(captureBuilder)

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

    private fun applyCommonCaptureSettings(builder: CaptureRequest.Builder) {
        if (ParameterConfig.isFlashEnabled() && hasFlash) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE)
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
        }

        if (ParameterConfig.isHdrEnabled() && supportsHdr) {
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE)
            builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_HDR)
            Log.d("CameraController", "HDR enabled for capture.")
        } else {
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            builder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_DISABLED)
            Log.d("CameraController", "HDR disabled for capture.")
        }

        builder.set(CaptureRequest.CONTROL_AWB_MODE, ParameterConfig.getWhiteBalanceMode())

        val exposureCompensation = ParameterConfig.getExposureCompensation()
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureCompensation.toInt())
    }



    fun setPreview(textureView: TextureView) {
        preview = textureView
    }

    @SuppressLint("MissingPermission")
    fun openCamera() {
        initializeHandlerThread()

        val displayMetrics = Resources.getSystem().displayMetrics
        val screenWidth = displayMetrics.widthPixels

// 4:3 aspect ratio: height = 4/3 * width
        val calculatedHeight = screenWidth * 4 / 3

// Set TextureView layout height programmatically
        preview.layoutParams.height = calculatedHeight
        preview.requestLayout()

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                cameraDevice = camera
                captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
                val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                val availableSizes = map?.getOutputSizes(SurfaceTexture::class.java)

                val previewSize = chooseOptimalSize(
                    availableSizes ?: arrayOf(Size(1920, 1080)),
                    calculatedHeight,
                    screenWidth
                )

                preview.surfaceTexture?.setDefaultBufferSize(previewSize.width, previewSize.height)
                val surface = Surface(preview.surfaceTexture)
                captureRequest.addTarget(surface)

                applyCommonCaptureSettings(captureRequest)

                val exposureCompensation = ParameterConfig.getExposureCompensation()
                captureRequest.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureCompensation.toInt())

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
                val layoutParams = textureView.layoutParams
                val screenAspectRatio = width.toFloat() / height
                val viewAspectRatio = textureView.width.toFloat() / textureView.height

                if (screenAspectRatio > viewAspectRatio) {
                    // Width constrained
                    layoutParams.height = (textureView.width / screenAspectRatio).toInt()
                } else {
                    // Height constrained
                    layoutParams.width = (textureView.height * screenAspectRatio).toInt()
                }

                textureView.layoutParams = layoutParams
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


    // helpers
    fun getHighestResolution(): Size? {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(ImageFormat.JPEG)
        return sizes?.sortedWith(compareBy { it.width * it.height })?.last()
    }

    fun setZoom(scaleFactor: Float) {
        val newZoom = zoomLevel * scaleFactor
        zoomLevel = newZoom.coerceIn(1f, maxZoom)

        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val sensorRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        if (sensorRect != null) {
            val newWidth = (sensorRect.width() / zoomLevel).toInt()
            val newHeight = (sensorRect.height() / zoomLevel).toInt()
            val newLeft = (sensorRect.width() - newWidth) / 2
            val newTop = (sensorRect.height() - newHeight) / 2
            val cropRect = Rect(newLeft, newTop, newLeft + newWidth, newTop + newHeight)
            captureRequest.set(CaptureRequest.SCALER_CROP_REGION, cropRect)
            cameraCaptureSession.setRepeatingRequest(captureRequest.build(), null, handler)
        }
    }

    private fun playShutterSound() {
        val sound = MediaActionSound()
        sound.play(MediaActionSound.SHUTTER_CLICK)
    }

    fun getSensorOrientation(): Int {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        return characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
    }

    // getters
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

    fun supportsHdr(): Boolean {
        return supportsHdr
    }

    fun getSupportedAwbModes(): IntArray {
        return supportedAwbModes
    }

    fun getImageReader(): ImageReader {
        return imageReader
    }

    fun getCaptureRequest(): CaptureRequest.Builder {
        return captureRequest
    }

    // setters
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

        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        val capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        supportsHdr = capabilities?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE) == true &&
                characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)?.contains(CaptureRequest.CONTROL_SCENE_MODE_HDR) == true
        supportedAwbModes = characteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES) ?: intArrayOf(CaptureRequest.CONTROL_AWB_MODE_AUTO)

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
            if (::cameraCaptureSession.isInitialized) {
                cameraCaptureSession.close()
            }
            if (::cameraDevice.isInitialized) {
                cameraDevice.close()
            }
        } catch (e: Exception) {
            Log.e("CameraController", "Error closing camera: ", e)
        }
    }

    fun shutdownBackgroundThread() {
        try {
            if (::handlerThread.isInitialized) handlerThread.quitSafely()
        } catch(e: Exception) {
            Log.e("CameraController", "Error shutting down background thread", e)
        }
    }

    private fun chooseOptimalSize(choices: Array<Size>, width: Int, height: Int): Size {
        val targetRatio = width.toFloat() / height
        Log.d("ChooseOptimalSize", "Target width: $width, height: $height, aspectRatio: $targetRatio")

        choices.forEachIndexed { index, size ->
            val ratio = size.width.toFloat() / size.height
            Log.d("ChooseOptimalSize", "Choice[$index]: ${size.width}x${size.height}, ratio: $ratio")
        }

        val optimal = choices.minByOrNull {
            val ratio = it.width.toFloat() / it.height
            abs(ratio - targetRatio)
        } ?: choices[0]

        Log.d("ChooseOptimalSize", "Chosen optimal size: ${optimal.width}x${optimal.height}")
        return optimal
    }


}
