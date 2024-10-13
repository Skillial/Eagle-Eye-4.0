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


import android.app.ActivityManager
import android.media.MediaScannerConnection
import android.os.Debug
import org.opencv.core.Core


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
    private var sensorOrientation: Int = 0

    fun getAppMemoryUsage(): Long {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val memoryInfoArray = arrayOf(Debug.MemoryInfo())
        Debug.getMemoryInfo(memoryInfoArray[0])

        val usedMemory = memoryInfoArray[0].getTotalPss() * 1024L // in bytes
        return usedMemory
    }

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

                    var width = bitmap.width
                    var height = bitmap.height

                    // Convert Bitmap to OpenCV Mat
                    val mat = Mat(bitmap.height, bitmap.width, CvType.CV_8UC3)
                    Utils.bitmapToMat(bitmap, mat)
                    bitmap.recycle()

                    if (sensorOrientation == 90) {
                        Core.rotate(mat, mat, Core.ROTATE_90_CLOCKWISE)
                        width = height.also { height = width }
                    } else if (sensorOrientation == 270) {
                        Core.rotate(mat, mat, Core.ROTATE_90_COUNTERCLOCKWISE)
                    } else if (sensorOrientation == 180) {
                        Core.rotate(mat, mat, Core.ROTATE_180)
                        width = height.also { height = width }
                    }

                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2RGB)

                    val divisionFactor = 3    // You can change this value to divide into more parts

                    val quadrantWidth = width / divisionFactor
                    val remainderWidth = width % divisionFactor
                    val quadrantHeight = height / divisionFactor
                    val remainderHeight = height % divisionFactor

                    // initialize filenames
                    val filenames = Array(divisionFactor * divisionFactor) { index ->
                        getExternalFilesDir(null)?.absolutePath + "/quadrant${index + 1}.jpg"
                    }

                    // Create submatrices for each section based on the division factor
                    var quadrantCount =0;
                    for (i in 0 until divisionFactor) {
                        for (j in 0 until divisionFactor) {
                            // Calculate the top-left and bottom-right coordinates for each quadrant

                            val topLeftX = j * quadrantWidth
                            val bottomRightX = (j + 1) * quadrantWidth + if (j == divisionFactor - 1) remainderWidth else 0
                            val topLeftY = i * quadrantHeight
                            val bottomRightY = (i + 1) * quadrantHeight + if (i == divisionFactor - 1) remainderHeight else 0

                            // Add submat to the list
                            Imgcodecs.imwrite(filenames[quadrantCount],mat.submat(topLeftY, bottomRightY, topLeftX, bottomRightX))
                            quadrantCount+=1;
                        }
                    }
                    mat.release()

                    val interpolationValue = 8

                    // Process each quadrant, apply bicubic interpolation, and merge them back
                    for (i in 0 until filenames.size) {
                        val quadrant = Imgcodecs.imread(filenames[i])
                        val resizedQuadrant = Mat()

                        // Perform bicubic interpolation on the loaded quadrant
                        Imgproc.resize(
                            quadrant, resizedQuadrant,
                            Size(
                                quadrant.cols().toDouble() * interpolationValue,
                                quadrant.rows().toDouble() * interpolationValue
                            ),
                            0.0, 0.0, Imgproc.INTER_CUBIC
                        )
                        Imgcodecs.imwrite(filenames[i],resizedQuadrant)
                        quadrant.release()
                        resizedQuadrant.release()
                    }

                    // Create an empty Mat to store the final merged image
                    val mergedImage = Mat.zeros(height * interpolationValue, width * interpolationValue, CvType.CV_8UC3)

                    // Calculate the row and column offset based on the division factor
                    for (i in 0 until filenames.size) {
                        val quadrant = Imgcodecs.imread(filenames[i])
                        val row = i / divisionFactor
                        val col = i % divisionFactor

                        val rowOffset = row * quadrantHeight * interpolationValue
                        val colOffset = col * quadrantWidth * interpolationValue
                        // Copy the resized quadrant into the correct position in the merged image
                        quadrant.copyTo(
                            mergedImage.submat(
                                rowOffset, rowOffset + quadrant.rows(),
                                colOffset, colOffset + quadrant.cols()
                            )
                        )
                        // Release resources
                        quadrant.release()

                        val file = File(filenames[i])
                        if (file.exists()) {
                            file.delete()
                        }
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
                        // Trigger a media scan to add the image to the gallery
                        MediaScannerConnection.scanFile(
                            this@MainActivity,  // Pass the correct context reference
                            arrayOf(imageFile.absolutePath),
                            arrayOf("image/jpeg")
                        ) { path, uri ->
                            Log.d("MediaScanner", "Image saved to gallery: $path")
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                mergedImage.release()

                processedImagesCounter+=1
                val currentCount = processedImagesCounter

                // Check if all 10 images are processed
                if (currentCount == 1) {
                    runOnUiThread {
                        loadingBox.visibility = View.GONE
                    }
                    Toast.makeText(this@MainActivity, "images processed and saved.", Toast.LENGTH_SHORT).show()
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
                var totalCaptures = 1
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

                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

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