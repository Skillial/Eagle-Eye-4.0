
package com.wangGang.eagleEye.processing.shadow_remove

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
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
import kotlin.math.ceil
import kotlin.math.min

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

    private fun load(bitmap: Bitmap): Mat {
        val img = Mat()
        Utils.bitmapToMat(bitmap, img)
        return img
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


    private fun convertToBitmap(shadowRemoved: FloatArray, width: Int, height: Int): Bitmap {
        val intArray = IntArray(width * height)
        val channelCount = shadowRemoved.size / (width * height)

        for (i in 0 until width * height) {
            val r = when (channelCount) {
                1 -> (shadowRemoved[i] * 255).coerceIn(0f, 255f).toInt()
                else -> (shadowRemoved[i] * 255).coerceIn(0f, 255f).toInt()
            }

            val g = when (channelCount) {
                3 -> (shadowRemoved[i + width * height] * 255).coerceIn(0f, 255f).toInt()
                else -> r
            }

            val b = when (channelCount) {
                3 -> (shadowRemoved[i + 2 * width * height] * 255).coerceIn(0f, 255f).toInt()
                else -> r
            }

            intArray[i] = 0xFF shl 24 or (r shl 16) or (g shl 8) or b
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(intArray, 0, width, 0, 0, width, height)
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

    private fun onnxTensorToMat(tensor: OnnxTensor): Mat {
        val shape = tensor.info.shape // [1, C, H, W]
        val batch = shape[0].toInt()
        val channels = shape[1].toInt()
        val height = shape[2].toInt()
        val width = shape[3].toInt()

        require(batch == 1) { "Batch size other than 1 is not supported" }

        // Get tensor data as float array
        val floatBuffer: FloatBuffer = tensor.floatBuffer
        val data = FloatArray(floatBuffer.remaining())
        floatBuffer.get(data)

        // Convert from NCHW (data) to HWC (Mat expects)
        val matData = FloatArray(height * width * channels)
        for (h in 0 until height) {
            for (w in 0 until width) {
                for (c in 0 until channels) {
                    val nchwIndex = c * height * width + h * width + w
                    val hwcIndex = h * width * channels + w * channels + c
                    matData[hwcIndex] = data[nchwIndex]
                }
            }
        }

        val mat = Mat(height, width, CvType.CV_32FC(channels))
        mat.put(0, 0, matData)

        return mat
    }

    private fun resizeMatBicubic(inputMat: Mat, newWidth: Int, newHeight: Int): Mat {
        val outputMat = Mat()
        Imgproc.resize(inputMat, outputMat, Size(newWidth.toDouble(), newHeight.toDouble()), 0.0, 0.0, Imgproc.INTER_CUBIC)
        return outputMat
    }

    /**
     * Convert OpenCV Mat (HWC float) back to OnnxTensor (NCHW float)
     */
    private fun matToOnnxTensor(env: OrtEnvironment, mat: Mat): OnnxTensor {
        val height = mat.rows()
        val width = mat.cols()
        val channels = mat.channels()

        val size = height * width * channels
        val matData = FloatArray(size)
        mat.get(0, 0, matData)

        // Convert from HWC back to NCHW for ONNX
        val tensorData = FloatArray(size)
        for (h in 0 until height) {
            for (w in 0 until width) {
                for (c in 0 until channels) {
                    val hwcIndex = h * width * channels + w * channels + c
                    val nchwIndex = c * height * width + h * width + w
                    tensorData[nchwIndex] = matData[hwcIndex]
                }
            }
        }

        val shape = longArrayOf(1, channels.toLong(), height.toLong(), width.toLong())
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(tensorData), shape)
    }

    private fun interpolateOnnxTensorBicubic(env: OrtEnvironment, matteSmall: OnnxTensor, origH: Int, origW: Int): OnnxTensor {
        val mat = onnxTensorToMat(matteSmall)
        val resizedMat = resizeMatBicubic(mat, origW, origH)
        return matToOnnxTensor(env, resizedMat)
    }

    private fun padToMultipleReflect(
        env: OrtEnvironment,
        inputTensor: OnnxTensor,
        multiple: Int = 512
    ): Triple<OnnxTensor, Int, Int> {
        val data = inputTensor.value as Array<Array<Array<FloatArray>>>

        val batch = data.size
        val channels = data[0].size
        val origH = data[0][0].size
        val origW = data[0][0][0].size

        val padH = (ceil(origH.toDouble() / multiple) * multiple).toInt() - origH
        val padW = (ceil(origW.toDouble() / multiple) * multiple).toInt() - origW

        if (padH == 0 && padW == 0) {
            return Triple(inputTensor, origH, origW)
        }

        val newH = origH + padH
        val newW = origW + padW

        // Create padded array
        val padded = Array(batch) {
            Array(channels) {
                Array(newH) {
                    FloatArray(newW)
                }
            }
        }

        // Copy original data
        for (b in 0 until batch) {
            for (c in 0 until channels) {
                for (h in 0 until origH) {
                    for (w in 0 until origW) {
                        padded[b][c][h][w] = data[b][c][h][w]
                    }
                }
            }
        }

        // Reflect pad bottom rows
        for (b in 0 until batch) {
            for (c in 0 until channels) {
                for (padRow in 0 until padH) {
                    val srcRow = origH - 1 - padRow
                    for (w in 0 until origW) {
                        padded[b][c][origH + padRow][w] = data[b][c][srcRow][w]
                    }
                }
            }
        }

        // Reflect pad right columns (including bottom padded rows)
        for (b in 0 until batch) {
            for (c in 0 until channels) {
                for (h in 0 until newH) {
                    for (padCol in 0 until padW) {
                        val srcCol = origW - 1 - padCol
                        padded[b][c][h][origW + padCol] = padded[b][c][h][srcCol]
                    }
                }
            }
        }

        // Flatten 4D array into 1D FloatArray in NCHW order
        val flatData = FloatArray(batch * channels * newH * newW)
        var index = 0
        for (b in 0 until batch) {
            for (c in 0 until channels) {
                for (h in 0 until newH) {
                    for (w in 0 until newW) {
                        flatData[index++] = padded[b][c][h][w]
                    }
                }
            }
        }

        // Wrap flattened array into FloatBuffer
        val floatBuffer = FloatBuffer.wrap(flatData)
        val shape = longArrayOf(batch.toLong(), channels.toLong(), newH.toLong(), newW.toLong())

        // Create new tensor from FloatBuffer and shape
        val paddedTensor = OnnxTensor.createTensor(env, floatBuffer, shape)

        return Triple(paddedTensor, origH, origW)
    }

    private fun extractPatches(
        env: OrtEnvironment,
        img: OnnxTensor,
        patchSize: Int = 512
    ): List<Triple<OnnxTensor, Int, Int>> {
        val data = img.value as Array<Array<Array<FloatArray>>> // shape: [1, c, h, w]
        val batch = data.size // should be 1
        val c = data[0].size
        val h = data[0][0].size
        val w = data[0][0][0].size

        val patches = mutableListOf<Triple<OnnxTensor, Int, Int>>()

        for (i in 0 until h step patchSize) {
            val patchH = minOf(patchSize, h - i)
            for (j in 0 until w step patchSize) {
                val patchW = minOf(patchSize, w - j)

                // Flatten patch data to FloatArray
                val flatPatchData = FloatArray(batch * c * patchH * patchW)
                var idx = 0
                for (b in 0 until batch) {
                    for (ch in 0 until c) {
                        for (row in 0 until patchH) {
                            for (col in 0 until patchW) {
                                flatPatchData[idx++] = data[b][ch][i + row][j + col]
                            }
                        }
                    }
                }

                val floatBuffer = FloatBuffer.wrap(flatPatchData)
                val shape = longArrayOf(batch.toLong(), c.toLong(), patchH.toLong(), patchW.toLong())
                val patchTensor = OnnxTensor.createTensor(env, floatBuffer, shape)

                patches.add(Triple(patchTensor, i, j))
            }
        }

        return patches
    }

    private fun reconstructFromPatches(
        patches: List<Triple<FloatArray, Int, Int>>,
        fullH: Int,
        fullW: Int
    ): FloatArray {
        val fullImage = FloatArray(3 * fullH * fullW)  // Fix: account for 3 channels

        for ((patch, i, j) in patches) {
            val patchHeight = patch[0].toInt()
            val patchWidth = patch[1].toInt()
            val validH = min(patchHeight, (fullH - i))
            val validW = min(patchWidth, (fullW - j))

            for (c in 0 until 3) {
                for (h in 0 until validH) {
                    for (w in 0 until validW) {
                        val patchIdx = c * patchHeight * patchWidth + h * patchWidth + w
                        val fullIdx = c * fullH * fullW + (i + h) * fullW + (j + w)
                        fullImage[fullIdx] = patch[patchIdx]
                    }
                }
            }
        }

        return fullImage
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
        val (_, downImg) = loadAndResize(bitmap, Size(512.0, 512.0))
        //val (_, img) = loadAndResizeFromAssets(Size(TARGET_WIDTH.toDouble(), TARGET_HEIGHT.toDouble()))
        val downTensor = preprocess(downImg, env)

        // Load and run the shadow matte model.
        val matteSession = loadModelFromAssets(env, sessionOptions, "model/shadow_matte.onnx")
        val matteResults = matteSession.run(
            mapOf(matteSession.inputNames.first() to downTensor)
        )
        val smallMatteTensor = matteResults.get(0) as OnnxTensor
        // interpolate tensor to match the input size
        val matteTensor = interpolateOnnxTensorBicubic(env, smallMatteTensor, originalHeight, originalWidth)
        // Process input by concatenating the RGB tensor and shadow matte.
        val img = load(bitmap)
        val rgbTensor = preprocess(img, env)

        val (paddedRgb, paddedH, paddedW) = padToMultipleReflect(env,rgbTensor, 512)
        val (paddedMatte, _, _) = padToMultipleReflect(env, matteTensor, 512)

        val rgbPatches = extractPatches(env, paddedRgb)
        val mattePatches = extractPatches(env, paddedMatte)

        val processedPatch = mutableListOf<Triple<FloatArray, Int, Int>>()
        val removalSession = loadModelFromAssets(env, sessionOptions, "model/shadow_removal.onnx")
        for ((rgbTriple, matteTriple) in rgbPatches.zip(mattePatches)) {
            val (rgbPatch, i, j) = rgbTriple
            val (mattePatch, _, _) = matteTriple
            val shadowInput = processInput(env, rgbPatch, mattePatch)

            val shadowRemovedData = shadowInput.use { input ->
                removalSession.run(
                    mapOf(removalSession.inputNames.first() to input)
                ).use { outputs ->
                    (outputs.get(0) as OnnxTensor).use { outputTensor ->
                        // Convert to FloatArray
                        FloatArray(outputTensor.floatBuffer.remaining()).also { array ->
                            outputTensor.floatBuffer.get(array)
                        }
                    }
                }
            }.map { value ->
                ((value + 1f) / 2f).coerceIn(0f, 1f)
            }.toFloatArray()

            processedPatch.add(Triple(shadowRemovedData,i,j))
        }
        var fullResult = reconstructFromPatches(processedPatch.toList(), originalHeight, originalWidth)

        fullResult = fullResult.copyOfRange(0, paddedH * paddedW)
        // Convert the full result back to a bitmap
        val outputBitmap = convertToBitmap(fullResult, originalWidth, originalHeight)


        matteResults.close()
        rgbTensor.close()
        matteTensor.close()

        matteSession.close()
        removalSession.close()
        img.release()

        env.close()

        ProgressManager.getInstance().nextTask()

        return outputBitmap
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

//    fun removeShadowTest(bitmap: Bitmap, filename: String): Bitmap {
//        // Initialize the ONNX Runtime environment.
//        val env = OrtEnvironment.getEnvironment()
//        val sessionOptions = OrtSession.SessionOptions().apply {
//            setMemoryPatternOptimization(true)
//            addConfigEntry("session.use_device_memory_mapping", "1")
//            addConfigEntry("session.enable_stream_execution", "1")
//        }
//
//        // Load and preprocess the input image from assets.
//        val (_, img) = loadAndResizeFromAssetsTest(Size(TARGET_WIDTH.toDouble(), TARGET_HEIGHT.toDouble()), filename)
//        val rgbTensor = preprocess(img, env)
//
//        // Load and run the shadow matte model.
//        val matteSession = loadModelFromAssets(env, sessionOptions, "model/shadow_matte.onnx")
//        val matteResults = matteSession.run(
//            mapOf(matteSession.inputNames.first() to rgbTensor)
//        )
//        val matteTensor = matteResults.get(0) as OnnxTensor
//
//        // Process input by concatenating the RGB tensor and shadow matte.
//        val shadowInput = processInput(env, rgbTensor, matteTensor)
//
//        // Load and run the shadow removal model.
//        val removalSession = loadModelFromAssets(env, sessionOptions, "model/shadow_removal.onnx")
//        val shadowRemovedData = shadowInput.use { input ->
//            removalSession.run(
//                mapOf(removalSession.inputNames.first() to input)
//            ).use { outputs ->
//                // Assuming the output tensor is the first output.
//                (outputs.get(0) as OnnxTensor).use { outputTensor ->
//                    FloatArray(outputTensor.floatBuffer.remaining()).also { array ->
//                        outputTensor.floatBuffer.get(array)
//                    }
//                }
//            }
//        }.map { value ->
//            // Rescale output from model (assumed output range is [-1,1]) to [0,1]
//            ((value + 1f) / 2f).coerceIn(0f, 1f)
//        }.toFloatArray()
//
//        // Close the intermediate tensors.
//        matteResults.close()
//        rgbTensor.close()
//        matteTensor.close()
//
//        matteSession.close()
//        removalSession.close()
//        img.release()
//
////        return convertToBitmap(shadowRemovedData, )
//    }
}