package com.bgcoding.camera2api.io

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.os.Environment
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
import java.io.File
import java.io.InputStream
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
                image?.let { processImage(it) }
            }
        }, cameraController.getHandler())
    }

    private fun processImage(image: Image) {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        image.close()
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
//        handleSuperResolutionImage(bitmap)
        handleDehazeImage(bitmap)
    }

    private fun handleDehazeImage(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            val fileName = FileImageWriter.getInstance()?.saveImageToStorage(bitmap)
            bitmap.recycle()
            fileName?.let { dehazeImage(it) }
        }
    }

    private fun loadAndResize(imagePath: String, size: org.opencv.core.Size): Pair<org.opencv.core.Size, Mat> {
        val img = Imgcodecs.imread(imagePath)
        if (img.empty()) {
            throw IllegalArgumentException("Image not found: $imagePath")
        }
        val imSize = org.opencv.core.Size(img.cols().toDouble(), img.rows().toDouble())
        Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2RGB)
        Imgproc.resize(img, img, size)
        return Pair(imSize, img)
    }

    private fun loadAndResizeFromAssets(imagePath: String, size: org.opencv.core.Size): Pair<org.opencv.core.Size, Mat> {
        val inputStream: InputStream
        try {
            inputStream = context.assets.open("model/dehaze-test/try.png")
        } catch (e: Exception) {
            throw IllegalArgumentException("Image not found in assets: $imagePath")
        }
        val byteArray = inputStream.readBytes()
        val matOfByte = org.opencv.core.MatOfByte(*byteArray)
        val img = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR)

        if (img.empty()) {
            throw IllegalArgumentException("Image decoding failed: $imagePath")
        }

        val imSize = org.opencv.core.Size(img.cols().toDouble(), img.rows().toDouble())
        Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2RGB)
        Imgproc.resize(img, img, size)

        return Pair(imSize, img)
    }

    private fun loadModelFromAssets(env: OrtEnvironment, sessionOptions: OrtSession.SessionOptions, modelPath: String): OrtSession {
        return context.assets.open(modelPath).use { inputStream ->
            val modelBytes = inputStream.readBytes()
            env.createSession(modelBytes, sessionOptions)
        }
    }

    private fun dehazeImage(path: String) {
        val env = OrtEnvironment.getEnvironment()
        val sessionOptions = OrtSession.SessionOptions().apply {
            setMemoryPatternOptimization(true)
            addConfigEntry("session.use_device_memory_mapping", "1")
            addConfigEntry("session.enable_stream_execution", "1")
        }
        val ortSessionAlbedo = loadModelFromAssets(env, sessionOptions, "model/albedo_model.onnx")

        val (imSize, hazyImg) = loadAndResizeFromAssets(path, org.opencv.core.Size(512.0, 512.0))

        val hazyInput = preprocess(hazyImg, env)
        val albedoOutput = hazyInput.use { input ->
            ortSessionAlbedo.run(mapOf("input.1" to input)).use { results ->
                (results.get(0) as OnnxTensor).use { tensor ->
                    FloatArray(tensor.floatBuffer.remaining()).also { tensor.floatBuffer.get(it) }
                }
            }
        }


        Log.d("dehaze", "Albedo output computed successfully")

        val ortSessionTransmission = loadModelFromAssets(env, sessionOptions, "model/transmission_model.onnx")

        val transmissionInput = OnnxTensor.createTensor(env, FloatBuffer.wrap(albedoOutput), longArrayOf(1, 3, 512, 512))

        val transmissionOutput = transmissionInput.use { input ->
            ortSessionTransmission.run(mapOf("input.1" to input)).use { results ->
                (results.get(0) as OnnxTensor).use { tensor ->
                    FloatArray(tensor.floatBuffer.remaining()).also { tensor.floatBuffer.get(it) }
                }
            }
        }


        val T: FloatArray = transmissionOutput.map { (it * 0.5f) + 0.5f }.toFloatArray()

        val TResized = Mat(hazyImg.rows(), hazyImg.cols(), CvType.CV_32F)
        TResized.put(0, 0, T)


        Log.d("dehaze", "Transmission output computed successfully")

        val hazyResized = Mat()
        Imgproc.resize(hazyImg, hazyResized, org.opencv.core.Size(256.0, 256.0), 0.0, 0.0, Imgproc.INTER_CUBIC)
        val airlightInput = preprocess(hazyResized, env)
        hazyResized.release()
        val ortSessionAirlight = loadModelFromAssets(env, sessionOptions, "model/airlight_model.onnx")
        sessionOptions.close()
        val airlightOutput = airlightInput.use { input ->
            ortSessionAirlight.run(mapOf("input.1" to input)).use { results ->
                (results.get(0) as OnnxTensor).floatBuffer.array()
            }
        }

        Log.d("dehaze", "Airlight output computed successfully")

        val airlightRed = airlightOutput[0]
        val airlightGreen = airlightOutput[1]
        val airlightBlue = airlightOutput[2]


        val hazyImgNorm = Mat()
        Core.normalize(hazyImg, hazyImgNorm, 0.0, 1.0, Core.NORM_MINMAX, CvType.CV_32FC3)
        hazyImg.release()

        val clearImg = Mat(hazyImgNorm.rows(), hazyImgNorm.cols(), CvType.CV_32FC3)

        for (i in 0 until hazyImgNorm.rows()) {
            for (j in 0 until hazyImgNorm.cols()) {
                val tVal = TResized.get(i, j)[0]
                val tValMax = maxOf(tVal, 0.001)

                val hazyPixel = hazyImgNorm.get(i, j)
                var clearRed = (hazyPixel[0] - airlightRed * (1 - tVal)) / tValMax
                var clearGreen = (hazyPixel[1] - airlightGreen * (1 - tVal)) / tValMax
                var clearBlue = (hazyPixel[2] - airlightBlue * (1 - tVal)) / tValMax

                clearRed = clearRed.coerceIn(0.0, 1.0)
                clearGreen = clearGreen.coerceIn(0.0, 1.0)
                clearBlue = clearBlue.coerceIn(0.0, 1.0)

                clearImg.put(i, j, clearRed * 255.0, clearGreen * 255.0, clearBlue * 255.0)
            }
        }
        TResized.release()
        hazyImgNorm.release()

        val clearImgResized = Mat()
        Imgproc.resize(clearImg, clearImgResized, imSize, 0.0, 0.0, Imgproc.INTER_CUBIC)
        clearImg.release()
        clearImgResized.convertTo(clearImgResized, CvType.CV_8U)
        Imgproc.cvtColor(clearImgResized, clearImgResized, Imgproc.COLOR_RGB2BGR)

        val mediaStorageDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "DehazeApp")
        if (!mediaStorageDir.exists()) {
            mediaStorageDir.mkdirs()
        }

        val file = File(mediaStorageDir, "dehazed_result.png")
        Imgcodecs.imwrite(file.absolutePath, clearImgResized)
        clearImgResized.release()

        Log.d("dehaze", "dehazed image saved")
    }

    private fun preprocess(img: Mat, env: OrtEnvironment): OnnxTensor {
        val imgFloat = Mat()
        img.convertTo(imgFloat, CvType.CV_32F, 1.0 / 255.0)

        Core.subtract(imgFloat, Scalar(0.5, 0.5, 0.5), imgFloat)
        Core.divide(imgFloat, Scalar(0.5, 0.5, 0.5), imgFloat)

        Imgproc.cvtColor(imgFloat, imgFloat, Imgproc.COLOR_BGR2RGB)

        val chwData = FloatArray(3 * img.rows() * img.cols())
        val channels = mutableListOf(Mat(), Mat(), Mat())
        Core.split(imgFloat, channels)

        val height = img.rows()
        val width = img.cols()

        for (i in 0 until height) {
            for (j in 0 until width) {
                chwData[i * width + j] = channels[0].get(i, j)[0].toFloat()
                chwData[height * width + i * width + j] = channels[1].get(i, j)[0].toFloat()
                chwData[2 * height * width + i * width + j] = channels[2].get(i, j)[0].toFloat()
            }
        }

        val inputShape = longArrayOf(1, 3, img.rows().toLong(), img.cols().toLong())
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(chwData), inputShape)
    }


    private fun handleSuperResolutionImage(bitmap: Bitmap) {
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