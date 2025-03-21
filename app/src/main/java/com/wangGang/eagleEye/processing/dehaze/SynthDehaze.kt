package com.wangGang.eagleEye.processing.dehaze

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.wangGang.eagleEye.io.FileImageReader
import com.wangGang.eagleEye.io.FileImageWriter
import com.wangGang.eagleEye.io.ImageFileAttribute
import com.wangGang.eagleEye.io.ImageUtils
import com.wangGang.eagleEye.io.ResultType
import com.wangGang.eagleEye.processing.TAG
import com.wangGang.eagleEye.ui.activities.CameraControllerActivity
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
    private fun loadAndResize(bitmap: Bitmap, size: Size): Triple<Mat, Size, Mat> {

        // Save before image

        val rotatedBitmap = ImageUtils.rotateBitmap(bitmap, 90f)

        val img = Mat()
        Utils.bitmapToMat(rotatedBitmap, img)
        val origImg = Mat()
        Utils.bitmapToMat(rotatedBitmap, origImg)
        if (img.empty()) {
            throw IllegalArgumentException("Image conversion failed")
        }

        val imSize = Size(img.cols().toDouble(), img.rows().toDouble())

        Imgproc.resize(img, img, size)

        return Triple(origImg, imSize, img)
    }

    private fun loadAndResizeFromAssets (size: Size): Triple<Mat, Size, Mat> {
        val inputStream: InputStream
        try {
            inputStream = context.assets.open("test/dehaze/try.png")
        } catch (e: Exception) {
            throw IllegalArgumentException("Image not found in assets")
        }
        val byteArray = inputStream.readBytes()
        val matOfByte = MatOfByte(*byteArray)
        val img = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR)
        val origImg = img.clone()

        if (img.empty()) {
            throw IllegalArgumentException("Image decoding failed")
        }

        val imSize = Size(img.cols().toDouble(), img.rows().toDouble())

        // Save before image
        FileImageWriter.getInstance()!!.saveMatrixToResultsDir(img, ImageFileAttribute.FileType.JPEG, ResultType.BEFORE)

        Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2RGB)
        Imgproc.cvtColor(origImg, origImg, Imgproc.COLOR_RGB2BGR)
        Imgproc.resize(img, img, size)

        return Triple(origImg, imSize, img)
    }

    private fun loadModelFromAssets(env: OrtEnvironment, sessionOptions: OrtSession.SessionOptions, modelPath: String): OrtSession {
        return context.assets.open(modelPath).use { inputStream ->
            val modelBytes = inputStream.readBytes()
            env.createSession(modelBytes, sessionOptions)
        }
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

    fun dehazeImage(bitmap: Bitmap): Bitmap {
        val env = OrtEnvironment.getEnvironment()
        val sessionOptions = OrtSession.SessionOptions().apply {
            setMemoryPatternOptimization(true)
            addConfigEntry("session.use_device_memory_mapping", "1")
            addConfigEntry("session.enable_stream_execution", "1")
        }

        viewModel.updateLoadingText("Loading and Resizing Image")
        val (origImg, imSize, hazyImg) = loadAndResize(bitmap, Size(512.0, 512.0))
//        val (origImg, imSize, hazyImg) = loadAndResizeFromAssets(Size(512.0, 512.0))
        ProgressManager.getInstance().incrementProgress(ProgressManager.dehazeSteps[1])


        viewModel.updateLoadingText("Loading Albedo Model")
        val ortSessionAlbedo = loadModelFromAssets(env, sessionOptions, "model/albedo_model.onnx")
        ProgressManager.getInstance().incrementProgress(ProgressManager.dehazeSteps[2])

        viewModel.updateLoadingText("Preprocessing Image")
        val hazyInput = preprocess(hazyImg, env)
        ProgressManager.getInstance().incrementProgress(ProgressManager.dehazeSteps[3])

        viewModel.updateLoadingText("Running Albedo Model")
        val albedoOutput = hazyInput.use { input ->
            ortSessionAlbedo.run(mapOf("input.1" to input)).use { results ->
                (results.get(0) as OnnxTensor).use { tensor ->
                    FloatArray(tensor.floatBuffer.remaining()).also { tensor.floatBuffer.get(it) }
                }
            }
        }
        ProgressManager.getInstance().incrementProgress(ProgressManager.dehazeSteps[4])

        // Close the session
        ortSessionAlbedo.close()

        Log.d("dehaze", "Albedo output computed successfully")

        viewModel.updateLoadingText("Loading Transmission Model")
        val ortSessionTransmission = loadModelFromAssets(env, sessionOptions, "model/transmission_model.onnx")
        ProgressManager.getInstance().incrementProgress(ProgressManager.dehazeSteps[5])

        val transmissionInput = OnnxTensor.createTensor(env, FloatBuffer.wrap(albedoOutput), longArrayOf(1, 3, 512, 512))

        viewModel.updateLoadingText("Running Transmission Model")
        val transmissionOutput = transmissionInput.use { input ->
            ortSessionTransmission.run(mapOf("input.1" to input)).use { results ->
                (results.get(0) as OnnxTensor).use { tensor ->
                    FloatArray(tensor.floatBuffer.remaining()).also { tensor.floatBuffer.get(it) }
                }
            }
        }
        ProgressManager.getInstance().incrementProgress(ProgressManager.dehazeSteps[6])

        // Close the session
        ortSessionTransmission.close()
        val size = 512
        val reshapedTransmission = Array(size) { FloatArray(size) }
        for (h in 0 until size) {
            for (w in 0 until size) {
                reshapedTransmission[h][w] = transmissionOutput[h * size + w]
            }
        }
        val transmissionMat = Mat(size, size, CvType.CV_32F)
        for (h in 0 until size) {
            for (w in 0 until size) {
                transmissionMat.put(h, w, floatArrayOf(reshapedTransmission[h][w] * 0.5f + 0.5f))  // Wrap the value in a FloatArray
            }
        }
        val TResized = Mat()
        Imgproc.resize(transmissionMat, TResized, imSize, 0.0, 0.0, Imgproc.INTER_CUBIC)
        TResized.convertTo(TResized, CvType.CV_32F)
        Core.min(TResized, Scalar(1.0), TResized)
        Core.max(TResized, Scalar(0.0), TResized)



        Log.d("dehaze", "Transmission output computed successfully")

        viewModel.updateLoadingText("Resizing Image")
        val hazyResized = Mat()
        Imgproc.resize(hazyImg, hazyResized, Size(256.0, 256.0), 0.0, 0.0, Imgproc.INTER_CUBIC)

        Log.d(TAG, "Preprocessing Image")
        viewModel.updateLoadingText("Preprocessing Image")
        val airlightInput = preprocess(hazyResized, env)
        hazyResized.release()

        Log.d(TAG, "Loading Airlight Model")
        viewModel.updateLoadingText("Loading Airlight Model")
        val ortSessionAirlight = loadModelFromAssets(env, sessionOptions, "model/airlight_model.onnx")
        sessionOptions.close()
        ProgressManager.getInstance().incrementProgress(ProgressManager.dehazeSteps[7])

        Log.d(TAG, "Running Airlight Model")
        viewModel.updateLoadingText("Running Airlight Model")
        val airlightOutput = airlightInput.use { input ->
            ortSessionAirlight.run(mapOf("input.1" to input)).use { results ->
                (results.get(0) as OnnxTensor).floatBuffer.array()
            }
        }
        ProgressManager.getInstance().incrementProgress(ProgressManager.dehazeSteps[8])

        // Close the session
        ortSessionAirlight.close()

        Log.d("dehaze", "Airlight output computed successfully")

        val airlightRed = airlightOutput[0]
        val airlightGreen = airlightOutput[1]
        val airlightBlue = airlightOutput[2]

        Log.d(TAG, "Normalizing Image")
        viewModel.updateLoadingText("Normalizing Image")
        val hazyImgNorm = Mat()
        Core.normalize(origImg, hazyImgNorm, 0.0, 1.0, Core.NORM_MINMAX, CvType.CV_32FC3)
        hazyImg.release()

        Log.d(TAG, "Clearing Image")
        viewModel.updateLoadingText("Clearing Image")
        val clearImg = Mat(hazyImgNorm.rows(), hazyImgNorm.cols(), CvType.CV_32FC3)

        Log.d(TAG, "Processing Image")
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

        Log.d(TAG, "Resizing Image")
        viewModel.updateLoadingText("Resizing Image")

        Log.d(TAG, "Converting Image")
        viewModel.updateLoadingText("Converting Image")
        clearImg.convertTo(clearImg, CvType.CV_8U)
        Core.rotate(clearImg, clearImg, Core.ROTATE_90_COUNTERCLOCKWISE)
        ProgressManager.getInstance().incrementProgress(ProgressManager.dehazeSteps[9])

//        Log.d(TAG, "Saving Image")
//        viewModel.updateLoadingText("Saving Image")
//        FileImageWriter.getInstance()!!.saveMatrixToResultsDir(clearImg, ImageFileAttribute.FileType.JPEG, ResultType.AFTER)
//        FileImageWriter.getInstance()!!.saveMatrixToResultsDir(clearImg, ImageFileAttribute.FileType.JPEG)
        // convert clearImg to bitmap
        val clearBitmap = Bitmap.createBitmap(clearImg.cols(), clearImg.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(clearImg, clearBitmap)
        clearImg.release()
        return clearBitmap
//        clearImg.release()
//
//        ProgressManager.getInstance().incrementProgress(ProgressManager.dehazeSteps[10])
//
//        val uriList = FileImageReader.getInstance()
//            ?.let { listOfNotNull(it.getBeforeUriDefaultResultsFolder(), it.getAfterUriDefaultResultsFolder()) }
//        Log.d("ConcreteSuperResolution", "uriList: $uriList")
//        CameraControllerActivity.launchBeforeAndAfterActivity(uriList!!)
    }
}