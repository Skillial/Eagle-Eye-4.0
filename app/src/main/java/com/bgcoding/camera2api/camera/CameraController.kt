package com.bgcoding.camera2api.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView

class CameraController(private val context: Context) {
    private lateinit var cameraManager: CameraManager
    private lateinit var cameraDevice: CameraDevice
    private lateinit var imageReader: ImageReader
    private lateinit var handler: Handler
    private lateinit var handlerThread: HandlerThread
    private lateinit var captureRequest: CaptureRequest.Builder
    private lateinit var cameraCaptureSession: CameraCaptureSession
    private var cameraId: String = ""

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

    private fun getCameraId(lensFacing: Int): String {
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == lensFacing) {
                return cameraId
            }
        }
        return ""
    }

    fun getCameraCharacteristics(): CameraCharacteristics {
        return cameraManager.getCameraCharacteristics(cameraId)
    }
}