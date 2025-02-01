package com.bgcoding.camera2api.io

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtLoggingLevel
import ai.onnxruntime.OrtSession
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.util.Log
import android.util.Size
import android.view.View
import com.bgcoding.camera2api.camera.CameraController
import com.bgcoding.camera2api.processing.ConcreteSuperResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.nio.FloatBuffer


class ImageReaderManager(
    private val context: Context,
    private val cameraController: CameraController,
    private val imageInputMap: MutableList<String>,
    private val concreteSuperResolution: ConcreteSuperResolution,
    private val loadingBox: View
) {

    fun initializeImageReader() {
        concreteSuperResolution.initialize(imageInputMap)
        val highestResolution = cameraController.getHighestResolution()
        setupImageReader(highestResolution)
        setImageReaderListener()
    }

    private fun setupImageReader(highestResolution: Size?) {
        val imageReader: ImageReader = if (highestResolution != null) {
            ImageReader.newInstance(highestResolution.width, highestResolution.height, ImageFormat.JPEG, 20)
        } else {
            ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1)
        }
        cameraController.setImageReader(imageReader)
    }

    private fun setImageReaderListener() {
        val imageReader = cameraController.getImageReader()
        imageReader.setOnImageAvailableListener(object : ImageReader.OnImageAvailableListener {
            override fun onImageAvailable(reader: ImageReader?) {
                val image = reader?.acquireNextImage()
                image?.let {
                    processImage(it)
                }
            }
        }, cameraController.getHandler())
    }

    private fun processImage(image: Image) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        image.close()

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
//        handleImage(bitmap)
        CoroutineScope(Dispatchers.IO).launch {
            lateinit var fileName: String
            val saveJob = launch {
                FileImageWriter.getInstance()?.saveImageToStorage(bitmap)?.let {
                    fileName = it
                }
            }
            saveJob.join()
            Log.d("dehaze","image saved")
            dehazeImage(fileName)
        }
    }

    private fun loadAndResize(imagePath: String, size: org.opencv.core.Size): Pair<org.opencv.core.Size, Mat> {
        val img = Imgcodecs.imread(imagePath)
        if (img.empty()) {
            throw IllegalArgumentException("Image not found: $imagePath")
        }
        val imSize = org.opencv.core.Size(img.cols().toDouble(), img.rows().toDouble())
        Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2RGB)  // Convert to RGB
        Imgproc.resize(img, img, size)  // Resize the image
        return Pair(imSize, img)
    }
    private fun loadModelFromAssets(env: OrtEnvironment, sessionOptions: OrtSession.SessionOptions, modelPath: String): OrtSession {
        return context.assets.open(modelPath).use { inputStream ->
            val modelBytes = inputStream.readBytes()
            env.createSession(modelBytes, sessionOptions)
        }
    }

    fun getAvailableMemory(): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        // Available memory in bytes
        val availableMemory = memoryInfo.availMem

        // Convert to megabytes (optional)
        val availableMemoryMB = availableMemory / (1024 * 1024)

        return availableMemoryMB // Returns available memory in MB
    }
    fun dehazeImage(path: String) {
        // Load ONNX models (REPLACE WITH YOUR ACTUAL PATHS)
        val env = OrtEnvironment.getEnvironment()
        val sessionOptions = OrtSession.SessionOptions()
        sessionOptions.setMemoryPatternOptimization(true)
        sessionOptions.setCPUArenaAllocator(false)
        sessionOptions.addNnapi()
        sessionOptions.addConfigEntry("session.use_device_memory_mapping", "1")
        sessionOptions.addConfigEntry("session.enable_stream_execution", "1")
        val ortSessionAlbedo = loadModelFromAssets(env, sessionOptions, "model/Quantized_albedoC.onnx")

        Log.d("dehaze","models loaded"+getAvailableMemory())
        // Image Loading and Resizing
        val (imSize, hazyImg) = loadAndResize(path, org.opencv.core.Size(1024.0, 1024.0))

        // Preprocessing
        val hazyInput = preprocess(hazyImg, env)

        // Run Inference
        val albedoOutput = try {
            ortSessionAlbedo.run(mapOf("input.1" to hazyInput)).use { results ->
                val output = results.get(0)
                if (output is OnnxTensor) {
                    output.floatBuffer.array() // Convert to FloatArray
                } else {
                    throw IllegalStateException("Unexpected output type: ${output::class.java}")
                }
            }
        } catch (e: Exception) {
            Log.e("dehaze", "Error running inference: ${e.message}")
            return
        }
        val ortSessionTransmission = loadModelFromAssets(env, sessionOptions, "model/transmission_model.onnx")
        val ortSessionAirlight = loadModelFromAssets(env, sessionOptions, "model/airlight_model.onnx")
        Log.d("dehaze","albedo output")

        val transmissionOutput = ortSessionTransmission.run(mapOf("input.1" to hazyInput)).use { results ->
            val output = results.get(0)
            if (output is OnnxTensor) {
                output.floatBuffer.array() // Convert to FloatArray
            } else {
                throw IllegalStateException("Unexpected output type: ${output::class.java}")
            }
        }
        Log.d("dehaze","transmission output")

        val hazyResized = Mat()
        Imgproc.resize(hazyImg, hazyResized, org.opencv.core.Size(512.0, 512.0), 0.0, 0.0, Imgproc.INTER_CUBIC)
        val airlightInput = preprocess(hazyResized, env)
        val airlightOutput = ortSessionAirlight.run(mapOf("input.1" to airlightInput)).use { results ->
            val output = results.get(0)
            if (output is OnnxTensor) {
                output.floatBuffer.array() // Convert to FloatArray
            } else {
                throw IllegalStateException("Unexpected output type: ${output::class.java}")
            }
        }

        Log.d("dehaze","airlight output")

// Postprocessing
        val T = transmissionOutput.map { (it * 0.5f) + 0.5f }.toFloatArray() // Apply transformation

// Create a Mat from the FloatArray
        val transmissionMat = Mat(hazyImg.rows(), hazyImg.cols(), CvType.CV_32F)
        transmissionMat.put(0, 0, T)

// Resize the Mat
        val TResized = Mat(hazyImg.size(), CvType.CV_32F)
        Imgproc.resize(transmissionMat, TResized, hazyImg.size(), 0.0, 0.0, Imgproc.INTER_CUBIC)

// Extract airlight values
        val airlightRed = airlightOutput[0]
        val airlightGreen = airlightOutput[1]
        val airlightBlue = airlightOutput[2]

        println("Airlight output: ${airlightOutput.contentToString()}")

// Use albedoOutput in the dehazing equation
        val albedoMat = Mat(hazyImg.rows(), hazyImg.cols(), CvType.CV_32F)
        albedoMat.put(0, 0, albedoOutput)

// Use airlight values and albedo in the dehazing equation
        val hazyImgNorm = Mat()
        Core.normalize(hazyImg, hazyImgNorm, 0.0, 1.0, Core.NORM_MINMAX, CvType.CV_32F)
        val clearImg = Mat.ones(hazyImgNorm.size(), CvType.CV_32F)

// Reconstruct the clear image using albedo, transmission, and airlight
        Core.subtract(hazyImgNorm, Scalar(airlightRed * (1 - TResized.get(0, 0)[0]), airlightGreen * (1 - TResized.get(0, 0)[0]), airlightBlue * (1 - TResized.get(0, 0)[0])), clearImg)
        Core.divide(clearImg, TResized, clearImg)

// Multiply by the albedo to restore the original colors
        Core.multiply(clearImg, albedoMat, clearImg)

        Core.minMaxLoc(clearImg).let { minMax ->
            Core.multiply(clearImg, Scalar(255.0 / minMax.maxVal), clearImg)
        }

// Save the dehazed image
        val clearImgResized = Mat()
        Imgproc.resize(clearImg, clearImgResized, imSize, 0.0, 0.0, Imgproc.INTER_CUBIC)
        val clearImgUint8 = Mat()
        clearImgResized.convertTo(clearImgUint8, CvType.CV_8U)
        Imgproc.cvtColor(clearImgUint8, clearImgUint8, Imgproc.COLOR_RGB2BGR)
        Imgcodecs.imwrite("dehazed_result.png", clearImgUint8)

        Log.d("dehaze","dehazed image saved")
    }

    private fun preprocess(img: Mat, env: OrtEnvironment): OnnxTensor {
        // Convert the image to a float array and normalize
        val imgFloat = Mat()
        img.convertTo(imgFloat, CvType.CV_32F, 1.0 / 255.0) // Normalize to [0, 1]

        // Subtract mean and divide by std (normalization)
        val mean = floatArrayOf(0.5f, 0.5f, 0.5f)
        val std = floatArrayOf(0.5f, 0.5f, 0.5f)
        Core.subtract(imgFloat, Scalar(mean[0].toDouble(), mean[1].toDouble(), mean[2].toDouble()), imgFloat)
        Core.divide(imgFloat, Scalar(std[0].toDouble(), std[1].toDouble(), std[2].toDouble()), imgFloat)

        // Convert the Mat to a FloatBuffer
        val floatBuffer = FloatBuffer.allocate(imgFloat.rows() * imgFloat.cols() * imgFloat.channels())
        imgFloat.get(0, 0, floatBuffer.array())

        // Create an OnnxTensor from the FloatBuffer
        val shape = longArrayOf(1, imgFloat.channels().toLong(), imgFloat.rows().toLong(), imgFloat.cols().toLong())
        return OnnxTensor.createTensor(env, floatBuffer, shape)
    }


    private fun handleImage(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            val sharedPreferences = context.getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE)
            val isSuperResolutionEnabled = sharedPreferences.getBoolean("super_resolution_enabled", false)

            if (isSuperResolutionEnabled) {
                val saveJob = launch {
                    FileImageWriter.getInstance()?.saveImageToStorage(bitmap)?.let {
                        imageInputMap.add(it)
                    }
                }

                saveJob.join() // Ensures the file is saved before checking the count

                if (imageInputMap.size == 5) {
                    // Run super resolution asynchronously
                    launch {
                        concreteSuperResolution.superResolutionImage(imageInputMap)
                        withContext(Dispatchers.Main) {
                            loadingBox.visibility = View.GONE
                        }
                        imageInputMap.clear()
                    }
                }
            } else {
                Log.i("Main", "No IE is toggled. Saving a single image to device.")
                launch { FileImageWriter.getInstance()?.saveImageToStorage(bitmap) }.join()

                withContext(Dispatchers.Main) {
                    loadingBox.visibility = View.GONE
                }
            }
        }
    }

}