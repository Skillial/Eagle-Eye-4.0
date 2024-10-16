package com.bgcoding.camera2api

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
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
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import android.media.MediaActionSound
import org.opencv.imgcodecs.Imgcodecs

class MainActivity : ComponentActivity() {
    private var processedImagesCounter = 0
    lateinit var captureRequest: CaptureRequest.Builder
    lateinit var handler: Handler
    lateinit var handlerThread: HandlerThread
    lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView
    lateinit var cameraCaptureSession: CameraCaptureSession
    lateinit var cameraDevice: CameraDevice
    lateinit var imageReader: ImageReader
    lateinit var progressBar: ProgressBar
    lateinit var loadingText: TextView
    lateinit var loadingBox: LinearLayout
    lateinit var cameraId: String

    private val permissionsRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        permissions.entries.forEach {
            if (!it.value) {
                // Permission was denied, handle this situation
            } else {
                // Permission was granted, you can now proceed with your operations
                // setCameraPreview()
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
                // setCameraPreview()
            }
            else -> {
                // Some permissions not granted, request them
                permissionsRequest.launch(permissions)
            }
        }
        setCameraPreview()

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        handlerThread = HandlerThread("video thread")
        handlerThread.start()
        handler = Handler((handlerThread).looper)

        this.textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
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


        this.cameraId = getCameraId(CameraCharacteristics.LENS_FACING_BACK) // Dynamically get the rear camera ID
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(ImageFormat.JPEG)

        // Sort the sizes array in descending order and select the first one (highest resolution)
        val highestResolution = sizes?.sortedWith(compareBy { it.width * it.height })?.last()

        imageReader = if (highestResolution != null) {
            ImageReader.newInstance(highestResolution.width, highestResolution.height, ImageFormat.JPEG, 20)
        } else {
            // Fallback to a default resolution if no sizes are available
            ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1)
        }

        imageReader.setOnImageAvailableListener(object: ImageReader.OnImageAvailableListener {
            override fun onImageAvailable(p0: ImageReader?) {
                var image = p0?.acquireNextImage()

                val buffer = image!!.planes[0].buffer
                var bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)

                // Generate a unique filename for each image
//                val filename = "img_${System.currentTimeMillis()}.jpeg"
                image.close()




                // Convert byte array to Bitmap
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                // Convert Bitmap to OpenCV Mat
                val width = bitmap.width
                val height = bitmap.height

                // Convert Bitmap to OpenCV Mat
                val mat = Mat(bitmap.height, bitmap.width, CvType.CV_8UC3)
                Utils.bitmapToMat(bitmap, mat)

                val midX = width / 2
                val midY = height / 2
                // Create submatrices for each quadrant
                val quadrants = arrayOf(
                    mat.submat(0, midY, 0, midX), // Top-left
                    mat.submat(0, midY, midX, width), // Top-right
                    mat.submat(midY, height, 0, midX), // Bottom-left
                    mat.submat(midY, height, midX, width) // Bottom-right
                )

                // Save the temporary quadrant files
                val filenames = arrayOf(
                    getExternalFilesDir(null)?.absolutePath + "/quadrant1.jpg",
                    getExternalFilesDir(null)?.absolutePath + "/quadrant2.jpg",
                    getExternalFilesDir(null)?.absolutePath + "/quadrant3.jpg",
                    getExternalFilesDir(null)?.absolutePath + "/quadrant4.jpg"
                )

                // Save each quadrant to a file
                for (i in quadrants.indices) {
                    Imgcodecs.imwrite(filenames[i], quadrants[i])
                }
                val interpolationValue = 4
                // Create an empty Mat to store the final merged image
                val mergedImage = Mat.zeros(height*interpolationValue, width*interpolationValue, CvType.CV_8UC3)

                // Process each quadrant, apply bicubic interpolation, and merge them back
                for (i in 0 until quadrants.size) {
                    val quadrant = Imgcodecs.imread(filenames[i])
                    val resizedQuadrant = Mat()

                    // Perform bicubic interpolation on the loaded quadrant
                    Imgproc.resize(quadrant, resizedQuadrant, Size(quadrant.cols().toDouble() * interpolationValue, quadrant.rows().toDouble() * interpolationValue), 0.0, 0.0, Imgproc.INTER_CUBIC)

                    // Determine the position to place the resized quadrant in the merged image
                    val rowOffset = if (i < 2) 0 else quadrant.rows()*interpolationValue
                    val colOffset = if (i % 2 == 0) 0 else quadrant.cols()*interpolationValue

                    // Copy the resized quadrant into the correct position in the merged image
                    resizedQuadrant.copyTo(mergedImage.submat(rowOffset, rowOffset + resizedQuadrant.rows(), colOffset, colOffset + resizedQuadrant.cols()))

                    // Release resources
                    quadrant.release()
                    resizedQuadrant.release()
                }

// Save the final merged image
                val finalFilename = "merged_image_${System.currentTimeMillis()}.jpg"
                val finalFilePath = getExternalFilesDir(null)?.absolutePath + "/" + finalFilename
                Imgcodecs.imwrite(finalFilePath, mergedImage)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, finalFilename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    }

                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    contentResolver.openOutputStream(uri!!)?.use { outputStream ->
                        val mergedBitmap = BitmapFactory.decodeFile(finalFilePath)
                        mergedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                        mergedBitmap.recycle()
                    }
                } else {
                    // API 28 and below, save directly to external storage
                    val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val imageFile = File(picturesDir, finalFilename)

                    try {
                        FileOutputStream(imageFile).use { outputStream ->
                            val mergedBitmap = BitmapFactory.decodeFile(finalFilePath)
                            mergedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                            mergedBitmap.recycle()
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
// Delete temporary quadrant files
                for (filename in filenames) {
                    val file = File(filename)
                    if (file.exists()) {
                        file.delete()
                    }
                }

                processedImagesCounter+=1
                val currentCount = processedImagesCounter

                // Check if all 10 images are processed
                if (currentCount == 10) {
                    runOnUiThread {
                        loadingBox.visibility = View.GONE
                    }
                    Toast.makeText(this@MainActivity, "All 10 images processed and saved.", Toast.LENGTH_SHORT).show()
                }
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
                var totalCaptures = 10
//                var completedCaptures = 0
                val captureList = mutableListOf<CaptureRequest>()

                for (i in 0 until totalCaptures) { // Change this to the number of photos you want to capture in the burst
                    val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                    captureRequest.addTarget(imageReader.surface)
                    captureList.add(captureRequest.build())
                }

                playShutterSound()
                runOnUiThread {
                    loadingBox.visibility = View.VISIBLE
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
//                        completedCaptures++
//                        if (completedCaptures >= totalCaptures) {
//                            runOnUiThread {
//                                loadingBox.visibility = View.GONE
//                            }
//                            Toast.makeText(this@MainActivity, "Images captured and saved.", Toast.LENGTH_SHORT).show()
//                        }
                    }
                }, null)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun open_camera() {
        cameraManager.openCamera(cameraId, object: CameraDevice.StateCallback() {
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
        // Initialize UI elements
        this.textureView = findViewById(R.id.textureView)
        this.progressBar = findViewById(R.id.progressBar)
        this.loadingText = findViewById(R.id.loadingText)
        this.loadingBox = findViewById(R.id.loadingBox)
    }

    fun playShutterSound() {
        val sound = MediaActionSound()
        sound.play(MediaActionSound.SHUTTER_CLICK)
    }

    // Get id of front camera
    fun getCameraId(lensFacing: Int): String {
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == lensFacing) {
                return cameraId
            }
        }
        return ""
    }
}