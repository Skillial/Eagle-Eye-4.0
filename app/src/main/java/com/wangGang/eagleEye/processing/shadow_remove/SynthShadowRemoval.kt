package com.wangGang.eagleEye.processing.shadow_remove

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
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
        private const val TAG = "SynthShadowRemoval"
        private const val TARGET_DIMENSION = 512
        private const val MODEL_SHADOW_MATTE = "model/shadow_matte.onnx"
        private const val MODEL_SHADOW_REMOVAL = "model/shadow_removal.onnx"
    }

    private val ortEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val ortSessionOptions by lazy {
        OrtSession.SessionOptions().apply {
            setMemoryPatternOptimization(true)
            addConfigEntry("session.use_device_memory_mapping", "1")
            addConfigEntry("session.enable_stream_execution", "1")
        }
    }

    private fun loadAndResize(bitmap: Bitmap, size: Size): Pair<Size, Mat> {
        val img = Mat()
        Utils.bitmapToMat(bitmap, img)
        require(!img.empty()) { "Bitmap to Mat conversion failed." }

        if (img.channels() == 4) {
            Imgproc.cvtColor(img, img, Imgproc.COLOR_BGRA2BGR)
        }

        val originalSize = Size(img.cols().toDouble(), img.rows().toDouble())
        Imgproc.resize(img, img, size, 0.0, 0.0, Imgproc.INTER_AREA)
        return Pair(originalSize, img)
    }

    private fun padToMultiple512(mat: Mat): Mat {
        val rows = mat.rows()
        val cols = mat.cols()
        val padRows = (TARGET_DIMENSION - (rows % TARGET_DIMENSION)) % TARGET_DIMENSION
        val padCols = (TARGET_DIMENSION - (cols % TARGET_DIMENSION)) % TARGET_DIMENSION

        val paddedMat = Mat()
        Core.copyMakeBorder(
            mat, paddedMat,
            0, padRows,
            0, padCols,
            Core.BORDER_CONSTANT,
            Scalar(0.0, 0.0, 0.0)
        )
        return paddedMat
    }

    private fun loadAndPad(bitmap: Bitmap): Mat {
        val img = Mat()
        Utils.bitmapToMat(bitmap, img)
        require(!img.empty()) { "Bitmap to Mat conversion failed." }

        if (img.channels() == 4) {
            Imgproc.cvtColor(img, img, Imgproc.COLOR_RGBA2RGB)

            if (img.channels() == 4 && img.type() == CvType.CV_8UC4) {
                Imgproc.cvtColor(img, img, Imgproc.COLOR_BGRA2BGR)
            } else if (img.channels() == 4) {
                Imgproc.cvtColor(img, img, Imgproc.COLOR_RGBA2RGB)
            }
        }

        return padToMultiple512(img)
    }

    private fun loadFromAssets(assetPath: String): Mat {
        val inputStream: InputStream = context.assets.open(assetPath)
        val byteArray = inputStream.readBytes()
        val img = Imgcodecs.imdecode(MatOfByte(*byteArray), Imgcodecs.IMREAD_COLOR)
        require(!img.empty()) { "Image decoding from assets failed for $assetPath." }

        Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2RGB)
        return padToMultiple512(img)
    }

    private fun preprocess(img: Mat, env: OrtEnvironment): OnnxTensor {
        val imgFloat = Mat()
        img.convertTo(imgFloat, CvType.CV_32FC3, 1.0 / 255.0)

        Core.subtract(imgFloat, Scalar(0.5, 0.5, 0.5), imgFloat)
        Core.divide(imgFloat, Scalar(0.5, 0.5, 0.5), imgFloat)

        val channels = List(img.channels()) { Mat() }
        Core.split(imgFloat, channels)

        val chwData = FloatArray(img.height() * img.width() * img.channels())
        var offset = 0
        for (c in 0 until img.channels()) {
            val channelBuffer = FloatArray(img.height() * img.width())
            channels[c].get(0, 0, channelBuffer)
            System.arraycopy(channelBuffer, 0, chwData, offset, channelBuffer.size)
            offset += channelBuffer.size
        }

        imgFloat.release()
        channels.forEach { it.release() }

        val inputShape = longArrayOf(1, img.channels().toLong(), img.height().toLong(), img.width().toLong())
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(chwData), inputShape)
    }

    private fun processInput(
        env: OrtEnvironment,
        rgbTensor: OnnxTensor,
        matteTensor: OnnxTensor
    ): OnnxTensor {
        val rgbData = rgbTensor.floatBuffer.array()
        val matteData = matteTensor.floatBuffer.array()

        val height = rgbTensor.info.shape[2].toInt()
        val width = rgbTensor.info.shape[3].toInt()
        val numChannelsRGB = rgbTensor.info.shape[1].toInt()
        val numChannelsMatte = matteTensor.info.shape[1].toInt()

        val combinedData = FloatArray((numChannelsRGB + numChannelsMatte) * height * width)
        System.arraycopy(rgbData, 0, combinedData, 0, numChannelsRGB * height * width)
        System.arraycopy(matteData, 0, combinedData, numChannelsRGB * height * width, numChannelsMatte * height * width)

        val combinedShape = longArrayOf(1, (numChannelsRGB + numChannelsMatte).toLong(), height.toLong(), width.toLong())
        return OnnxTensor.createTensor(env, FloatBuffer.wrap(combinedData), combinedShape)
    }

    private fun convertToBitmap(
        shadowRemoved: FloatArray,
        height: Int,
        width: Int
    ): Bitmap {
        val channelSize = height * width
        val intArray = IntArray(channelSize)

        for (i in 0 until channelSize) {
            val r = (shadowRemoved[i] * 255).coerceIn(0f, 255f).toInt()
            val g = (shadowRemoved[i + channelSize] * 255).coerceIn(0f, 255f).toInt()
            val b = (shadowRemoved[i + 2 * channelSize] * 255).coerceIn(0f, 255f).toInt()
            intArray[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(intArray, 0, width, 0, 0, width, height)
        }
    }

    private fun loadModelFromAssets(modelPath: String): OrtSession {
        return context.assets.open(modelPath).use { inputStream ->
            val modelBytes = inputStream.readBytes()
            ortEnvironment.createSession(modelBytes, ortSessionOptions)
        }
    }

    private fun onnxTensorToMat(tensor: OnnxTensor): Mat {
        val shape = tensor.info.shape
        require(shape[0].toInt() == 1) { "Batch size other than 1 is not supported." }

        val channels = shape[1].toInt()
        val height = shape[2].toInt()
        val width = shape[3].toInt()

        val floatBuffer: FloatBuffer = tensor.floatBuffer
        val data = FloatArray(floatBuffer.remaining())
        floatBuffer.get(data)

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

    private fun matToOnnxTensor(env: OrtEnvironment, mat: Mat): OnnxTensor {
        val height = mat.rows()
        val width = mat.cols()
        val channels = mat.channels()

        val matData = FloatArray(height * width * channels)
        mat.get(0, 0, matData)

        val tensorData = FloatArray(height * width * channels)
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
        val resizedTensor = matToOnnxTensor(env, resizedMat)
        mat.release()
        resizedMat.release()
        return resizedTensor
    }

    private fun padToMultipleReflect(
        env: OrtEnvironment,
        inputTensor: OnnxTensor,
        multiple: Int = TARGET_DIMENSION
    ): Triple<OnnxTensor, Int, Int> {
        val data = inputTensor.floatBuffer

        val batch = inputTensor.info.shape[0].toInt()
        val channels = inputTensor.info.shape[1].toInt()
        val origH = inputTensor.info.shape[2].toInt()
        val origW = inputTensor.info.shape[3].toInt()

        val padH = (ceil(origH.toDouble() / multiple) * multiple).toInt() - origH
        val padW = (ceil(origW.toDouble() / multiple) * multiple).toInt() - origW

        if (padH == 0 && padW == 0) {
            return Triple(inputTensor, origH, origW)
        }

        val newH = origH + padH
        val newW = origW + padW

        val paddedData = FloatArray(batch * channels * newH * newW)

        for (b in 0 until batch) {
            for (c in 0 until channels) {
                for (h in 0 until newH) {
                    val srcH = if (h < origH) h else (origH - 1 - (h - origH)).coerceIn(0, origH - 1)
                    for (w in 0 until newW) {
                        val srcW = if (w < origW) w else (origW - 1 - (w - origW)).coerceIn(0, origW - 1)
                        val originalIndex = b * channels * origH * origW + c * origH * origW + srcH * origW + srcW
                        val paddedIndex = b * channels * newH * newW + c * newH * newW + h * newW + w
                        paddedData[paddedIndex] = data.get(originalIndex)
                    }
                }
            }
        }
        val paddedTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(paddedData), longArrayOf(batch.toLong(), channels.toLong(), newH.toLong(), newW.toLong()))
        return Triple(paddedTensor, origH, origW)
    }

    private fun extractPatches(
        env: OrtEnvironment,
        img: OnnxTensor,
        patchSize: Int = TARGET_DIMENSION
    ): List<Triple<OnnxTensor, Int, Int>> {
        val dataBuffer = img.floatBuffer
        val batch = img.info.shape[0].toInt()
        val c = img.info.shape[1].toInt()
        val h = img.info.shape[2].toInt()
        val w = img.info.shape[3].toInt()

        Log.d(TAG, "Image shape for patching: batch=$batch, channels=$c, height=$h, width=$w")
        val patches = mutableListOf<Triple<OnnxTensor, Int, Int>>()

        for (i in 0 until h step patchSize) {
            val currentPatchH = min(patchSize, h - i)
            for (j in 0 until w step patchSize) {
                val currentPatchW = min(patchSize, w - j)

                val flatPatchData = FloatArray(batch * c * currentPatchH * currentPatchW)
                var idx = 0
                for (b in 0 until batch) {
                    for (ch in 0 until c) {
                        for (row in 0 until currentPatchH) {
                            for (col in 0 until currentPatchW) {
                                val originalIndex = b * c * h * w + ch * h * w + (i + row) * w + (j + col)
                                flatPatchData[idx++] = dataBuffer.get(originalIndex)
                            }
                        }
                    }
                }
                val patchTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(flatPatchData), longArrayOf(batch.toLong(), c.toLong(), currentPatchH.toLong(), currentPatchW.toLong()))
                patches.add(Triple(patchTensor, i, j))
            }
        }
        return patches
    }

    private fun reconstructFromPatches(
        patches: List<Triple<FloatArray, Int, Int>>,
        fullH: Int,
        fullW: Int,
        numChannels: Int = 3
    ): FloatArray {
        val fullImage = FloatArray(numChannels * fullH * fullW)

        for ((patchData, i, j) in patches) {
            val patchHeight = TARGET_DIMENSION
            val patchWidth = TARGET_DIMENSION

            for (c in 0 until numChannels) {
                for (h_patch in 0 until patchHeight) {
                    val currentH = i + h_patch
                    if (currentH >= fullH) continue

                    for (w_patch in 0 until patchWidth) {
                        val currentW = j + w_patch
                        if (currentW >= fullW) continue

                        val patchIdx = c * patchHeight * patchWidth + h_patch * patchWidth + w_patch
                        val fullIdx = c * fullH * fullW + currentH * fullW + currentW
                        fullImage[fullIdx] = patchData[patchIdx]
                    }
                }
            }
        }
        return fullImage
    }

    fun removeShadow(bitmap: Bitmap): Bitmap {
        ProgressManager.getInstance().nextTask()

        val (originalSize, downsampledInput) = loadAndResize(bitmap, Size(TARGET_DIMENSION.toDouble(), TARGET_DIMENSION.toDouble()))
        val originalWidth = originalSize.width.toInt()
        val originalHeight = originalSize.height.toInt()
        Log.d(TAG, "Original image size: ${originalHeight} x ${originalWidth}")

        val downsampledInputTensor = preprocess(downsampledInput, ortEnvironment)
        downsampledInput.release()
        val matteSession = loadModelFromAssets(MODEL_SHADOW_MATTE)
        var smallMatteTensor: OnnxTensor?
        var matteResult: OrtSession.Result?

        try {
            matteResult = matteSession.run(mapOf(matteSession.inputNames.first() to downsampledInputTensor))
            smallMatteTensor = matteResult.get(0) as OnnxTensor
            downsampledInputTensor.close()

            val matteTensor = interpolateOnnxTensorBicubic(ortEnvironment, smallMatteTensor, originalHeight, originalWidth)
            matteResult.close()

            val fullImageMat = loadAndPad(bitmap)
            Log.d(TAG, "Full image mat size for patching: ${fullImageMat.rows()} x ${fullImageMat.cols()}")

            val (paddedMatte, _, _) = padToMultipleReflect(ortEnvironment, matteTensor, TARGET_DIMENSION)
            Log.d(TAG, "Padded matte size: ${paddedMatte.info.shape[2]} x ${paddedMatte.info.shape[3]}")
            val paddedHeight = paddedMatte.info.shape[2].toInt()
            val paddedWidth = paddedMatte.info.shape[3].toInt()
            val mattePatches = extractPatches(ortEnvironment, paddedMatte)
            matteTensor.close()
            paddedMatte.close()

            val removalSession = loadModelFromAssets(MODEL_SHADOW_REMOVAL)
            val processedPatches = mutableListOf<Triple<FloatArray, Int, Int>>()

            for ((mattePatch, i, j) in mattePatches) {
                val imgPatchMat = fullImageMat.submat(
                    i, min(i + TARGET_DIMENSION, fullImageMat.rows()),
                    j, min(j + TARGET_DIMENSION, fullImageMat.cols())
                )
                val rgbPatchTensor = preprocess(imgPatchMat, ortEnvironment)
                imgPatchMat.release()

                val shadowInputTensor = processInput(ortEnvironment, rgbPatchTensor, mattePatch)

                val shadowRemovedPatchData = shadowInputTensor.use { input ->
                    removalSession.run(
                        mapOf(removalSession.inputNames.first() to input)
                    ).use { outputs ->
                        (outputs.get(0) as OnnxTensor).use { outputTensor ->
                            FloatArray(outputTensor.floatBuffer.remaining()).also { array ->
                                outputTensor.floatBuffer.get(array)
                            }.map { value ->
                                ((value + 1f) / 2f).coerceIn(0f, 1f)
                            }.toFloatArray()
                        }
                    }
                }
                rgbPatchTensor.close()
                mattePatch.close()

                processedPatches.add(Triple(shadowRemovedPatchData, i, j))
            }
            fullImageMat.release()

            val fullResult = reconstructFromPatches(processedPatches, paddedHeight, paddedWidth)

            val outputBitmap = convertToBitmap(fullResult, paddedHeight, paddedWidth)
            val croppedBitmap = Bitmap.createBitmap(outputBitmap, 0, 0, originalWidth, originalHeight)

            return croppedBitmap

        } finally {
            matteSession.close()
        }
    }

    fun removeShadowTest(bitmap: Bitmap, filename: String): Bitmap {
        val (originalSize, downsampledInput) = loadAndResizeFromAssetsTest(Size(TARGET_DIMENSION.toDouble(), TARGET_DIMENSION.toDouble()), filename)
        val originalWidth = originalSize.width.toInt()
        val originalHeight = originalSize.height.toInt()
        Log.d(TAG, "Original test image size: ${originalHeight} x ${originalWidth}")

        val downsampledInputTensor = preprocess(downsampledInput, ortEnvironment)
        downsampledInput.release()

        val matteSession = loadModelFromAssets(MODEL_SHADOW_MATTE)
        var smallMatteTensor: OnnxTensor?
        var matteResult: OrtSession.Result?

        val removalSession = loadModelFromAssets(MODEL_SHADOW_REMOVAL)

        try {
            matteResult = matteSession.run(mapOf(matteSession.inputNames.first() to downsampledInputTensor))
            smallMatteTensor = matteResult.get(0) as OnnxTensor
            downsampledInputTensor.close()

            val matteTensor = interpolateOnnxTensorBicubic(ortEnvironment, smallMatteTensor, originalHeight, originalWidth)
            matteResult.close()

            val fullImageMat = loadAndPad(bitmap)
            Log.d(TAG, "Full test image mat size for patching: ${fullImageMat.rows()} x ${fullImageMat.cols()}")

            val (paddedMatte, _, _) = padToMultipleReflect(ortEnvironment, matteTensor, TARGET_DIMENSION)
            Log.d(TAG, "Padded matte size: ${paddedMatte.info.shape[2]} x ${paddedMatte.info.shape[3]}")
            val paddedHeight = paddedMatte.info.shape[2].toInt()
            val paddedWidth = paddedMatte.info.shape[3].toInt()
            val mattePatches = extractPatches(ortEnvironment, paddedMatte)
            matteTensor.close()
            paddedMatte.close()

            val processedPatches = mutableListOf<Triple<FloatArray, Int, Int>>()

            for ((mattePatch, i, j) in mattePatches) {
                val imgPatchMat = fullImageMat.submat(
                    i, min(i + TARGET_DIMENSION, fullImageMat.rows()),
                    j, min(j + TARGET_DIMENSION, fullImageMat.cols())
                )
                val rgbPatchTensor = preprocess(imgPatchMat, ortEnvironment)
                imgPatchMat.release()

                val shadowInputTensor = processInput(ortEnvironment, rgbPatchTensor, mattePatch)

                val shadowRemovedPatchData = shadowInputTensor.use { input ->
                    removalSession.run(
                        mapOf(removalSession.inputNames.first() to input)
                    ).use { outputs ->
                        (outputs.get(0) as OnnxTensor).use { outputTensor ->
                            FloatArray(outputTensor.floatBuffer.remaining()).also { array ->
                                outputTensor.floatBuffer.get(array)
                            }.map { value ->
                                ((value + 1f) / 2f).coerceIn(0f, 1f)
                            }.toFloatArray()
                        }
                    }
                }
                rgbPatchTensor.close()
                mattePatch.close()

                processedPatches.add(Triple(shadowRemovedPatchData, i, j))
            }
            fullImageMat.release()

            val fullResult = reconstructFromPatches(processedPatches, paddedHeight, paddedWidth)

            val outputBitmap = convertToBitmap(fullResult, paddedHeight, paddedWidth)
            val croppedBitmap = Bitmap.createBitmap(outputBitmap, 0, 0, originalWidth, originalHeight)

            return croppedBitmap

        } finally {
            matteSession.close()
            removalSession.close()
        }
    }

    private fun loadAndResizeFromAssetsTest(size: Size, filename: String): Pair<Size, Mat> {
        val inputStream: InputStream = context.assets.open("test/shadow/${filename}")
        val byteArray = inputStream.readBytes()
        val img = Imgcodecs.imdecode(MatOfByte(*byteArray), Imgcodecs.IMREAD_COLOR)
        require(!img.empty()) { "Image not found or decoding failed for test/${filename}" }

        val imSize = Size(img.cols().toDouble(), img.rows().toDouble())
        Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2RGB)
        Imgproc.resize(img, img, size, 0.0, 0.0, Imgproc.INTER_AREA)
        return Pair(imSize, img)
    }
}