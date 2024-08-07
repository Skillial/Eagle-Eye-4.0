package com.bgcoding.camera2api

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bgcoding.camera2api.ui.theme.Camera2ApiTheme
import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

class MainActivity : ComponentActivity() {

    lateinit var captureRequest: CaptureRequest.Builder
    lateinit var handler: Handler
    lateinit var handlerThread: HandlerThread
    lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView
    lateinit var cameraCaptureSession: CameraCaptureSession
    lateinit var cameraDevice: CameraDevice
    lateinit var imageReader: ImageReader

    private val permissionsRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        permissions.entries.forEach {
            if (!it.value) {
                // Permission was denied, handle this situation
            } else {
                // Permission was granted, you can now proceed with your operations
                setCameraPreview()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Initialization Failed")
        } else {
            Log.d("OpenCV", "Initialization Successful")
        }
        enableEdgeToEdge()

        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )

        val allPermissionsGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        when {
            allPermissionsGranted -> {
                // All permissions already granted
                setCameraPreview()
            }
            else -> {
                // Some permissions not granted, request them
                permissionsRequest.launch(permissions)
            }
        }

        textureView = findViewById(R.id.textureView)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        handlerThread = HandlerThread("video thread")
        handlerThread.start()
        handler = Handler((handlerThread).looper)

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                open_camera()
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                /*TODO("Not yet implemented")*/

                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            }

        }


        val cameraId = cameraManager.cameraIdList[0]
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(ImageFormat.JPEG)

        // Sort the sizes array in descending order and select the first one (highest resolution)
        val highestResolution = sizes?.sortedWith(compareBy { it.width * it.height })?.last()

        imageReader = if (highestResolution != null) {
            ImageReader.newInstance(highestResolution.width, highestResolution.height, ImageFormat.JPEG, 1)
        } else {
            // Fallback to a default resolution if no sizes are available
            ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1)
        }

        imageReader.setOnImageAvailableListener(object: ImageReader.OnImageAvailableListener {
            override fun onImageAvailable(p0: ImageReader?) {
                var image = p0?.acquireLatestImage()
                val buffer = image!!.planes[0].buffer
                var bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)

                // Generate a unique filename for each image
                val filename = "img_${System.currentTimeMillis()}.jpeg"
                image.close()




                // Convert byte array to Bitmap
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                // Convert Bitmap to OpenCV Mat
                val mat = Mat(bitmap.height, bitmap.width, CvType.CV_8UC4)
                val bitmapBuffer = ByteBuffer.allocate(bitmap.byteCount)
                bitmap.copyPixelsToBuffer(bitmapBuffer)
                mat.put(0, 0, bitmapBuffer.array())

                // Perform bicubic interpolation
                val size = Size(bitmap.width.toDouble() * 6, bitmap.height.toDouble() * 6) // Adjust the size as needed
                val resizedMat = Mat()
                Imgproc.resize(mat, resizedMat, size, 0.0, 0.0, Imgproc.INTER_CUBIC)

                // Convert the processed Mat back to Bitmap
                val resizedBitmap = Bitmap.createBitmap(resizedMat.cols(), resizedMat.rows(), Bitmap.Config.ARGB_8888)
                val resizedBuffer = ByteBuffer.allocate(resizedBitmap.byteCount)
                resizedMat.get(0, 0, resizedBuffer.array())
                resizedBitmap.copyPixelsFromBuffer(resizedBuffer)

                // Convert the processed bitmap back to byte array
                val stream = ByteArrayOutputStream()
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                val processedBytes = stream.toByteArray()




                // Add the image to the MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }

                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                contentResolver.openOutputStream(uri!!).use {
                    it?.write(processedBytes)
                }

                Toast.makeText(this@MainActivity, "Image captured and saved.", Toast.LENGTH_SHORT).show()
            }
        }, handler)

        /*findViewById<Button>(R.id.capture).apply {
            setOnClickListener {
                captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                captureRequest.addTarget(imageReader.surface)
                cameraCaptureSession.capture(captureRequest.build(), null, null)
            }
        }*/
        findViewById<Button>(R.id.capture).apply {
            setOnClickListener {
                val captureList = mutableListOf<CaptureRequest>()
                for (i in 0 until 10) { // Change this to the number of photos you want to capture in the burst
                    val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                    captureRequest.addTarget(imageReader.surface)
                    captureList.add(captureRequest.build())
                }
                cameraCaptureSession.captureBurst(captureList, object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        super.onCaptureCompleted(session, request, result)
                        // Handle the result of the capture here
                        Log.d("BurstCapture", "Capture completed")
                    }
                }, null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun open_camera() {
        cameraManager.openCamera(cameraManager.cameraIdList[0], object: CameraDevice.StateCallback() {
            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0

                captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

                var surface = Surface(textureView.surfaceTexture)
                captureRequest.addTarget(surface)

                cameraDevice.createCaptureSession(listOf(surface, imageReader.surface), object: CameraCaptureSession.StateCallback() {
                    override fun onConfigured(p0: CameraCaptureSession) {
                        cameraCaptureSession = p0
                        cameraCaptureSession.setRepeatingRequest(captureRequest.build(), null, null)
                    }

                    override fun onConfigureFailed(p0: CameraCaptureSession) {

                    }
                }, handler)
            }

            override fun onDisconnected(p0: CameraDevice) {
            }

            override fun onError(camera: CameraDevice, error: Int) {
            }

        }, handler)
    }

    private fun setCameraPreview() {
        /*setContent {
            Camera2ApiTheme {

            }
        }*/
        setContentView(R.layout.activity_main)
    }

}