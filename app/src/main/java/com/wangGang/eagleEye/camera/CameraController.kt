package com.wangGang.eagleEye.camera

import CameraViewModel
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.ImageFormat
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
import android.view.View
import android.widget.LinearLayout

class CameraController(private val context: Context, private val viewModel: CameraViewModel) {

    companion object {
        // Constants
        const val maxNumberOfBurstImages = 10

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
    private var cameraId: String = ""

    // Methods
    fun captureImage(loadingBox: LinearLayout) {
        val sharedPreferences = context.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE)
        val isSuperResolutionEnabled = sharedPreferences.getBoolean("super_resolution_enabled", false)
        val totalCaptures = if (isSuperResolutionEnabled) maxNumberOfBurstImages else 1
        val captureList = mutableListOf<CaptureRequest>()

        viewModel.updateLoadingText("Capturing Images...")

        for (i in 0 until totalCaptures) {
            val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureRequest.addTarget(imageReader.surface)
            captureRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            captureRequest.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            captureList.add(captureRequest.build())
        }

        playShutterSound()

        loadingBox.post {
            loadingBox.visibility = View.VISIBLE
        }

        cameraCaptureSession.captureBurst(
            captureList,
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    super.onCaptureCompleted(session, request, result)
                    Log.d("BurstCapture", "Capture completed")
                }
            },
            null
        )
    }

    @SuppressLint("MissingPermission")
    fun openCamera(textureView: TextureView) {
        initializeHandlerThread()

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0
                captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                val surface = Surface(textureView.surfaceTexture)
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
            }

            override fun onError(camera: CameraDevice, error: Int) {
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
                openCamera(textureView)
            }

            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                return true
            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
        }

        if (textureView.isAvailable) {
            openCamera(textureView)
        }
    }


    // helpers
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
            cameraCaptureSession.close()
            cameraDevice.close()
            handlerThread.quitSafely()
        } catch (e: Exception) {
            Log.e("CameraController", "Error closing camera: ", e)
        }
    }

    fun reopenCamera(textureView: TextureView) {
        if (!::cameraDevice.isInitialized) {
            openCamera(textureView)
        }
    }
}