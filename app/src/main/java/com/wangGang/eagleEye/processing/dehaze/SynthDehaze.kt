package com.wangGang.eagleEye.processing.dehaze

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.wangGang.eagleEye.io.FileImageWriter
import com.wangGang.eagleEye.io.ImageFileAttribute
import com.wangGang.eagleEye.io.ResultType
import com.wangGang.eagleEye.ui.utils.ProgressManager
import com.wangGang.eagleEye.ui.viewmodels.CameraViewModel
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.InputStream
import java.nio.FloatBuffer

class SynthDehaze(private val context: Context, private val viewModel: CameraViewModel) {
    private fun loadAndResize(bitmap: Bitmap, size: Size): Pair<Size, Mat> {
        val matrix = Matrix()
        matrix.postRotate(90f)
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        val img = Mat()
        Utils.bitmapToMat(rotatedBitmap, img)

        if (img.empty()) {
            throw IllegalArgumentException("Image conversion failed")
        }

        val imSize = Size(img.cols().toDouble(), img.rows().toDouble())

        Imgproc.resize(img, img, size)

        // Save before image
        FileImageWriter.getInstance()!!.saveMatrixToResultsDir(img, ImageFileAttribute.FileType.JPEG, ResultType.BEFORE)

        return Pair(imSize, img)
    }


    private fun loadAndResizeFromAssets (size: Size): Pair<Size, Mat> {
        val inputStream: InputStream
        try {
            inputStream = context.assets.open("test/dehaze/try.png")
        } catch (e: Exception) {
            throw IllegalArgumentException("Image not found in assets")
        }
        val byteArray = inputStream.readBytes()
        val matOfByte = MatOfByte(*byteArray)
        val img = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR)

        if (img.empty()) {
            throw IllegalArgumentException("Image decoding failed")
        }

        val imSize = Size(img.cols().toDouble(), img.rows().toDouble())
        Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2RGB)
        Imgproc.resize(img, img, size)

        // Save before image
        FileImageWriter.getInstance()!!.saveMatrixToResultsDir(img, ImageFileAttribute.FileType.JPEG, ResultType.BEFORE)

        return Pair(imSize, img)
    }

    private fun loadModelFromAssets(env: OrtEnvironment, sessionOptions: OrtSession.SessionOptions, modelPath: String): OrtSession {
        return context.assets.open(modelPath).use { inputStream ->
            val modelBytes = inputStream.readBytes()
            env.createSession(modelBytes, sessionOptions)
        }
    }

    fun dehazeImage(bitmap: Bitmap) {
        val env = OrtEnvironment.getEnvironment()
        val sessionOptions = OrtSession.SessionOptions().apply {
            setMemoryPatternOptimization(true)
            addConfigEntry("session.use_device_memory_mapping", "1")
            addConfigEntry("session.enable_stream_execution", "1")
        }

        viewModel.updateLoadingText("Loading Albedo Model")
        val ortSessionAlbedo = loadModelFromAssets(env, sessionOptions, "model/albedo_model.onnx")
        ProgressManager.getInstance().incrementProgress("Loading Albedo Model")

        viewModel.updateLoadingText("Loading and Resizing Image")
        val (imSize, hazyImg) = loadAndResize(bitmap, Size(512.0, 512.0))
//        val (imSize, hazyImg) = loadAndResizeFromAssets(Size(512.0, 512.0))
        ProgressManager.getInstance().incrementProgress()

        viewModel.updateLoadingText("Preprocessing Image")
        val hazyInput = preprocess(hazyImg, env)
        ProgressManager.getInstance().incrementProgress("Loading and Resizing Image")

        viewModel.updateLoadingText("Running Albedo Model")
        val albedoOutput = hazyInput.use { input ->
            ortSessionAlbedo.run(mapOf("input.1" to input)).use { results ->
                (results.get(0) as OnnxTensor).use { tensor ->
                    FloatArray(tensor.floatBuffer.remaining()).also { tensor.floatBuffer.get(it) }
                }
            }
        }
        ProgressManager.getInstance().incrementProgress("Running Albedo Model")

        // Close the session
        ortSessionAlbedo.close()

        Log.d("dehaze", "Albedo output computed successfully")

        viewModel.updateLoadingText("Loading Transmission Model")
        val ortSessionTransmission = loadModelFromAssets(env, sessionOptions, "model/transmission_model.onnx")
        val transmissionInput = OnnxTensor.createTensor(env, FloatBuffer.wrap(albedoOutput), longArrayOf(1, 3, 512, 512))
        ProgressManager.getInstance().incrementProgress("Loading Transmission Model")

        viewModel.updateLoadingText("Running Transmission Model")
        val transmissionOutput = transmissionInput.use { input ->
            ortSessionTransmission.run(mapOf("input.1" to input)).use { results ->
                (results.get(0) as OnnxTensor).use { tensor ->
                    FloatArray(tensor.floatBuffer.remaining()).also { tensor.floatBuffer.get(it) }
                }
            }
        }
        ProgressManager.getInstance().incrementProgress("Running Transmission Model")

        // Close the session
        ortSessionTransmission.close()

        val T: FloatArray = transmissionOutput.map { (it * 0.5f) + 0.5f }.toFloatArray()

        val TResized = Mat(hazyImg.rows(), hazyImg.cols(), CvType.CV_32F)
        TResized.put(0, 0, T)


        Log.d("dehaze", "Transmission output computed successfully")

        viewModel.updateLoadingText("Resizing Image")
        val hazyResized = Mat()
        Imgproc.resize(hazyImg, hazyResized, Size(256.0, 256.0), 0.0, 0.0, Imgproc.INTER_CUBIC)
        ProgressManager.getInstance().incrementProgress("Resizing Image")

        viewModel.updateLoadingText("Preprocessing Image")
        val airlightInput = preprocess(hazyResized, env)
        ProgressManager.getInstance().incrementProgress("Preprocessing Image")
        hazyResized.release()

        viewModel.updateLoadingText("Loading Airlight Model")
        val ortSessionAirlight = loadModelFromAssets(env, sessionOptions, "model/airlight_model.onnx")
        sessionOptions.close()
        ProgressManager.getInstance().incrementProgress("Loading Airlight Model")

        viewModel.updateLoadingText("Running Airlight Model")
        val airlightOutput = airlightInput.use { input ->
            ortSessionAirlight.run(mapOf("input.1" to input)).use { results ->
                (results.get(0) as OnnxTensor).floatBuffer.array()
            }
        }
        ProgressManager.getInstance().incrementProgress("Running Airlight Model")

        // Close the session
        ortSessionAirlight.close()

        Log.d("dehaze", "Airlight output computed successfully")

        val airlightRed = airlightOutput[0]
        val airlightGreen = airlightOutput[1]
        val airlightBlue = airlightOutput[2]

        viewModel.updateLoadingText("Normalizing Image")
        val hazyImgNorm = Mat()
        Core.normalize(hazyImg, hazyImgNorm, 0.0, 1.0, Core.NORM_MINMAX, CvType.CV_32FC3)
        hazyImg.release()
        ProgressManager.getInstance().incrementProgress("Normalizing Image")

        viewModel.updateLoadingText("Clearing Image")
        val clearImg = Mat(hazyImgNorm.rows(), hazyImgNorm.cols(), CvType.CV_32FC3)
        ProgressManager.getInstance().incrementProgress("Clearing Image")

        viewModel.updateLoadingText("Processing Image")
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
        ProgressManager.getInstance().incrementProgress("Processing Image")

        viewModel.updateLoadingText("Resizing Image")
        val clearImgResized = Mat()
        Imgproc.resize(clearImg, clearImgResized, imSize, 0.0, 0.0, Imgproc.INTER_CUBIC)
        clearImg.release()
        ProgressManager.getInstance().incrementProgress("Resizing Image")

        viewModel.updateLoadingText("Converting Image to an 8-bit format")
        clearImgResized.convertTo(clearImgResized, CvType.CV_8U)
        Imgproc.cvtColor(clearImgResized, clearImgResized, Imgproc.COLOR_RGB2BGR)
        ProgressManager.getInstance().incrementProgress("Converting Image to an 8-bit format")

        viewModel.updateLoadingText("Saving Image")
        FileImageWriter.getInstance()!!.saveMatrixToResultsDir(clearImgResized, ImageFileAttribute.FileType.JPEG, ResultType.BEFORE)
        clearImgResized.release()
        ProgressManager.getInstance().incrementProgress("Saving Image")

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
}