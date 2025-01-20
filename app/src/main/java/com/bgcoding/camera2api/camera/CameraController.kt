package com.bgcoding.camera2api.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.media.MediaActionSound
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.core.app.ActivityCompat

class CameraController(private val context: Context) {
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraDevice: CameraDevice
    private lateinit var imageReader: ImageReader
    private lateinit var handler: Handler
    private lateinit var handlerThread: HandlerThread
    private lateinit var captureRequest: CaptureRequest.Builder
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private var cameraId: String = ""

    // Constants
    private val maxNumberOfBurstImages: Int = 10

    fun initializeCamera() {
        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraId = getCameraId(CameraCharacteristics.LENS_FACING_BACK)

        handlerThread = HandlerThread("CameraBackgroundThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
    }

    fun captureImage() {
        val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        captureRequest.addTarget(imageReader.surface)

        cameraCaptureSession.capture(
            captureRequest.build(),
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    Log.d("CameraCapture", "Image captured successfully")
                }
            },
            handler
        )
    }

    @SuppressLint("MissingPermission")
    private fun open_camera(textureView: TextureView) {

        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0

                captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

                val surface = Surface(textureView.surfaceTexture)
                captureRequest.addTarget(surface)

                cameraDevice.createCaptureSession(
                    listOf(surface, imageReader.surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(p0: CameraCaptureSession) {
                            cameraCaptureSession = p0
                            cameraCaptureSession.setRepeatingRequest(
                                captureRequest.build(),
                                null,
                                null
                            )
                        }

                        override fun onConfigureFailed(p0: CameraCaptureSession) {
                        }
                    },
                    handler
                )
            }

            override fun onDisconnected(p0: CameraDevice) {
            }

            override fun onError(camera: CameraDevice, error: Int) {
            }
        }, handler)
    }

    fun getCameraId(lensFacing: Int): String {
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == lensFacing) {
                return cameraId
            }
        }
        return ""
    }

    // helpers
    fun getHighestResolution(): Size? {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(ImageFormat.JPEG)
        return sizes?.sortedWith(compareBy { it.width * it.height })?.last()
    }

    fun playShutterSound() {
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
}