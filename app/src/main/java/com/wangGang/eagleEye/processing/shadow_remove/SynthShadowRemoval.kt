
package com.wangGang.eagleEye.processing.shadow_remove

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
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
import androidx.core.graphics.scale
import com.wangGang.eagleEye.ui.utils.ProgressManager

class SynthShadowRemoval(
    private val context: Context,
    private val viewModel: CameraViewModel
) {
    companion object {
        private const val TARGET_WIDTH = 512
        private const val TARGET_HEIGHT = 512
    }

    private fun loadAndResize(bitmap: Bitmap, size: Size): Pair<Size, Mat> {
        val img = Mat()
        Utils.bitmapToMat(bitmap, img)
        if (img.empty()) {
            throw IllegalArgumentException("Image conversion failed")
        }
        val imSize = Size(img.cols().toDouble(), img.rows().toDouble())
        Imgproc.resize(img, img, size)
        return Pair(imSize, img)
    }

    private fun loadAndResizeFromAssets(size: Size): Pair<Size, Mat> {
        val inputStream: InputStream = try {
            context.assets.open("test/shadow/input.png")
        } catch (e: Exception) {
            throw IllegalArgumentException("Image not found in assets", e)
        }
        val byteArray = inputStream.readBytes()
        val matOfByte = MatOfByte(*byteArray)
        val img = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR)
        if (img.empty()) {
            throw IllegalArgumentException("Image decoding failed")
        }
        val imSize = Size(img.cols().toDouble(), img.rows().toDouble())

        // Convert BGR -> RGB, resize, and rotate to match the Python pipeline
        Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2RGB)
        Imgproc.resize(img, img, size)
        Core.rotate(img, img, Core.ROTATE_90_COUNTERCLOCKWISE)
        return Pair(imSize, img)
    }


    private fun preprocess(img: Mat, env: OrtEnvironment): OnnxTensor {
        // Convert to RGB already done during loading if needed
        val imgFloat = Mat()
        img.convertTo(imgFloat, CvType.CV_32FC3, 1.0 / 255.0) // now in [0,1]

        // Normalize as in PyTorch: (x - 0.5)/0.5
        Core.subtract(imgFloat, Scalar(0.5, 0.5, 0.5), imgFloat)
        Core.divide(imgFloat, Scalar(0.5, 0.5, 0.5), imgFloat)

        // Convert from HWC to CHW format.
        val channels = mutableListOf<Mat>()
        Core.split(imgFloat, channels)

        val chwData = FloatArray(3 * TARGET_HEIGHT * TARGET_WIDTH)
        for (c in 0 until 3) {
            val channelBuffer = FloatArray(TARGET_HEIGHT * TARGET_WIDTH)
            channels[c].get(0, 0, channelBuffer)
            System.arraycopy(channelBuffer, 0, chwData, c * TARGET_HEIGHT * TARGET_WIDTH, channelBuffer.size)
        }

        // Release resources.
        imgFloat.release()
        channels.forEach { it.release() }

        val inputShape = longArrayOf(1, 3, TARGET_HEIGHT.toLong(), TARGET_WIDTH.toLong())
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(chwData), inputShape)
    }


    private fun processInput(
        env: OrtEnvironment,
        rgbTensor: OnnxTensor,
        matteTensor: OnnxTensor
    ): OnnxTensor {
        // Extract float arrays from the ONNX tensors.
        val rgbData = rgbTensor.floatBuffer.array()  // Expected shape: [1, 3, H, W]
        val matteData = matteTensor.floatBuffer.array() // Expected shape: [1, 1, H, W]

        // Create combined array with 4 channels.
        val combinedData = FloatArray(4 * TARGET_HEIGHT * TARGET_WIDTH).apply {
            // Copy the three RGB channels.
            System.arraycopy(rgbData, 0, this, 0, 3 * TARGET_HEIGHT * TARGET_WIDTH)
            // Copy the matte channel (shadow matte).
            System.arraycopy(matteData, 0, this, 3 * TARGET_HEIGHT * TARGET_WIDTH, TARGET_HEIGHT * TARGET_WIDTH)
        }

        val combinedShape = longArrayOf(1, 4, TARGET_HEIGHT.toLong(), TARGET_WIDTH.toLong())
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(combinedData), combinedShape)
    }


    private fun convertToBitmap(shadowRemoved: FloatArray): Bitmap {
        val intArray = IntArray(TARGET_WIDTH * TARGET_HEIGHT)
        val channelSize = TARGET_WIDTH * TARGET_HEIGHT

        for (i in 0 until channelSize) {
            val r = (shadowRemoved[i] * 255).coerceIn(0f, 255f).toInt()
            val g = (shadowRemoved[i + channelSize] * 255).coerceIn(0f, 255f).toInt()
            val b = (shadowRemoved[i + 2 * channelSize] * 255).coerceIn(0f, 255f).toInt()
            intArray[i] = 0xFF shl 24 or (r shl 16) or (g shl 8) or b
        }

        return Bitmap.createBitmap(TARGET_WIDTH, TARGET_HEIGHT, Bitmap.Config.ARGB_8888).apply {
            setPixels(intArray, 0, TARGET_WIDTH, 0, 0, TARGET_WIDTH, TARGET_HEIGHT)
        }
    }


    private fun loadModelFromAssets(
        env: OrtEnvironment,
        sessionOptions: OrtSession.SessionOptions,
        modelPath: String
    ): OrtSession {
        return context.assets.open(modelPath).use { inputStream ->
            val modelBytes = inputStream.readBytes()
            env.createSession(modelBytes, sessionOptions)
        }
    }

    fun removeShadow(bitmap: Bitmap): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        // Initialize the ONNX Runtime environment.
        val env = OrtEnvironment.getEnvironment()
        val sessionOptions = OrtSession.SessionOptions().apply {
            setMemoryPatternOptimization(true)
            addConfigEntry("session.use_device_memory_mapping", "1")
            addConfigEntry("session.enable_stream_execution", "1")
        }

        // Load and preprocess the input image from assets.
        val (imSize, img) = loadAndResize(bitmap, Size(512.0, 512.0))
        //val (_, img) = loadAndResizeFromAssets(Size(TARGET_WIDTH.toDouble(), TARGET_HEIGHT.toDouble()))
        val rgbTensor = preprocess(img, env)

        // Load and run the shadow matte model.
        val matteSession = loadModelFromAssets(env, sessionOptions, "model/shadow_matte.onnx")
        val matteResults = matteSession.run(
            mapOf(matteSession.inputNames.first() to rgbTensor)
        )
        val matteTensor = matteResults.get(0) as OnnxTensor

        // Process input by concatenating the RGB tensor and shadow matte.
        val shadowInput = processInput(env, rgbTensor, matteTensor)

        // Load and run the shadow removal model.
        val removalSession = loadModelFromAssets(env, sessionOptions, "model/shadow_removal.onnx")
        val shadowRemovedData = shadowInput.use { input ->
            removalSession.run(
                mapOf(removalSession.inputNames.first() to input)
            ).use { outputs ->
                // Assuming the output tensor is the first output.
                (outputs.get(0) as OnnxTensor).use { outputTensor ->
                    FloatArray(outputTensor.floatBuffer.remaining()).also { array ->
                        outputTensor.floatBuffer.get(array)
                    }
                }
            }
        }.map { value ->
            // Rescale output from model (assumed output range is [-1,1]) to [0,1]
            ((value + 1f) / 2f).coerceIn(0f, 1f)
        }.toFloatArray()

        // Close the intermediate tensors.
        matteResults.close()
        rgbTensor.close()
        matteTensor.close()

        matteSession.close()
        removalSession.close()
        img.release()

        env.close()

        ProgressManager.getInstance().nextTask()

        val outputBitmap = convertToBitmap(shadowRemovedData)

        // Resize the result back to the original size
        return outputBitmap.scale(originalWidth, originalHeight)
    }


    private fun loadAndResizeFromAssetsTest(size: Size, filename: String): Pair<Size, Mat> {
        val inputStream: InputStream = try {
            context.assets.open("test/shadow/${filename}")
        } catch (e: Exception) {
            throw IllegalArgumentException("Image not found in assets", e)
        }
        val byteArray = inputStream.readBytes()
        val matOfByte = MatOfByte(*byteArray)
        val img = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR)
        if (img.empty()) {
            throw IllegalArgumentException("Image decoding failed")
        }
        val imSize = Size(img.cols().toDouble(), img.rows().toDouble())

        // Convert BGR -> RGB, resize, and rotate to match the Python pipeline
        Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2RGB)
        Imgproc.resize(img, img, size)
        Core.rotate(img, img, Core.ROTATE_90_COUNTERCLOCKWISE)
        return Pair(imSize, img)
    }

    fun removeShadowTest(bitmap: Bitmap, filename: String): Bitmap {
        // Initialize the ONNX Runtime environment.
        val env = OrtEnvironment.getEnvironment()
        val sessionOptions = OrtSession.SessionOptions().apply {
            setMemoryPatternOptimization(true)
            addConfigEntry("session.use_device_memory_mapping", "1")
            addConfigEntry("session.enable_stream_execution", "1")
        }

        // Load and preprocess the input image from assets.
        val (_, img) = loadAndResizeFromAssetsTest(Size(TARGET_WIDTH.toDouble(), TARGET_HEIGHT.toDouble()), filename)
        val rgbTensor = preprocess(img, env)

        // Load and run the shadow matte model.
        val matteSession = loadModelFromAssets(env, sessionOptions, "model/shadow_matte.onnx")
        val matteResults = matteSession.run(
            mapOf(matteSession.inputNames.first() to rgbTensor)
        )
        val matteTensor = matteResults.get(0) as OnnxTensor

        // Process input by concatenating the RGB tensor and shadow matte.
        val shadowInput = processInput(env, rgbTensor, matteTensor)

        // Load and run the shadow removal model.
        val removalSession = loadModelFromAssets(env, sessionOptions, "model/shadow_removal.onnx")
        val shadowRemovedData = shadowInput.use { input ->
            removalSession.run(
                mapOf(removalSession.inputNames.first() to input)
            ).use { outputs ->
                // Assuming the output tensor is the first output.
                (outputs.get(0) as OnnxTensor).use { outputTensor ->
                    FloatArray(outputTensor.floatBuffer.remaining()).also { array ->
                        outputTensor.floatBuffer.get(array)
                    }
                }
            }
        }.map { value ->
            // Rescale output from model (assumed output range is [-1,1]) to [0,1]
            ((value + 1f) / 2f).coerceIn(0f, 1f)
        }.toFloatArray()

        // Close the intermediate tensors.
        matteResults.close()
        rgbTensor.close()
        matteTensor.close()

        matteSession.close()
        removalSession.close()
        img.release()

        return convertToBitmap(shadowRemovedData)
    }
}