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
import com.wangGang.eagleEye.ui.fragments.CameraViewModel
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
import kotlin.math.max
import kotlin.math.min

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

        return Pair(imSize, img)
    }

    private fun loadAndDivideWithOverlap(bitmap: Bitmap, size: Int, overlap: Int): Triple<Mat, Pair<Int, Int>, List<Mat>> {
        val newSize = size - overlap

        val matrix = Matrix()
        matrix.postRotate(90f)
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        val img = Mat()
        val realImg = Mat()
        Utils.bitmapToMat(rotatedBitmap, img)
        Utils.bitmapToMat(rotatedBitmap, realImg)

        // Get image size as (width, height)
        val imSize = Pair(img.cols(), img.rows())

        val patches = mutableListOf<Mat>()

        // Loop over the image with step newSize
        for (i in 0 until img.rows() step newSize) {
            for (j in 0 until img.cols() step newSize) {
                var rowStart = i - (overlap / 2)
                var rowEnd = i + (overlap / 2) + newSize
                var colStart = j - (overlap / 2)
                var colEnd = j + (overlap / 2) + newSize

                if (i == 0) {
                    rowStart = 0
                    rowEnd = newSize + overlap
                }
                if (j == 0) {
                    colStart = 0
                    colEnd = newSize + overlap
                }
                if (i + newSize + overlap >= img.rows()) {
                    rowStart = img.rows() - newSize - overlap
                    rowEnd = img.rows()
                }
                if (j + newSize + overlap >= img.cols()) {
                    colStart = img.cols() - newSize - overlap
                    colEnd = img.cols()
                }

                // Extract the submatrix (patch)
                val patch = img.submat(rowStart, rowEnd, colStart, colEnd)
                patches.add(patch)
                println("Patch size: ${patch.rows()} x ${patch.cols()}")
            }
        }

        return Triple(realImg, imSize, patches)
    }

    private fun loadAndDivide(bitmap: Bitmap, size: Int): Triple<Mat, Mat, List<Mat>>{
        val matrix = Matrix()
        matrix.postRotate(90f)
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        val img = Mat()
        Utils.bitmapToMat(rotatedBitmap, img)
        val H = img.rows()
        val W = img.cols()
        val patches = mutableListOf<Mat>()

        // Step size for sliding window.

        // Loop over image to extract patches.
        for (i in 0 until H step size) {
            for (j in 0 until W step size) {
                // Calculate end indices ensuring we do not go out of bounds.
                val rowEnd = min(i + size, H)
                val colEnd = min(j + size, W)

                // Extract the submatrix.
                val patchSubMat = img.submat(i, rowEnd, j, colEnd)

                // Resize the patch to (size x size) using cubic interpolation.
                val patch = Mat()
                Imgproc.resize(patchSubMat, patch, Size(size.toDouble(), size.toDouble()), 0.0, 0.0, Imgproc.INTER_CUBIC)
                patches.add(patch)
            }
        }

        // Resize the full original image to (size x size).
        val fullImgResized = Mat()
        Imgproc.resize(img, fullImgResized, Size(size.toDouble(), size.toDouble()), 0.0, 0.0, Imgproc.INTER_CUBIC)

        return Triple(img, fullImgResized, patches)
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

        return Pair(imSize, img)
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

    fun oldDehazeImage(bitmap: Bitmap) {
        val env = OrtEnvironment.getEnvironment()
        val sessionOptions = OrtSession.SessionOptions().apply {
            setMemoryPatternOptimization(true)
            addConfigEntry("session.use_device_memory_mapping", "1")
            addConfigEntry("session.enable_stream_execution", "1")
        }

        viewModel.updateLoadingText("Loading Albedo Model...")
        val ortSessionAlbedo = loadModelFromAssets(env, sessionOptions, "model/512/albedo_model.onnx")


        viewModel.updateLoadingText("Loading and Resizing Image...")
        val (imSize, hazyImg) = loadAndResize(bitmap, Size(512.0, 512.0))
//        val (imSize, hazyImg) = loadAndResizeFromAssets(Size(512.0, 512.0))

        viewModel.updateLoadingText("Preprocessing Image...")
        val hazyInput = preprocess(hazyImg, env)

        viewModel.updateLoadingText("Running Albedo Model...")
        val albedoOutput = hazyInput.use { input ->
            ortSessionAlbedo.run(mapOf("input.1" to input)).use { results ->
                (results.get(0) as OnnxTensor).use { tensor ->
                    FloatArray(tensor.floatBuffer.remaining()).also { tensor.floatBuffer.get(it) }
                }
            }
        }

        // Close the session
        ortSessionAlbedo.close()

        Log.d("dehaze", "Albedo output computed successfully")

        viewModel.updateLoadingText("Loading Transmission Model...")
        val ortSessionTransmission = loadModelFromAssets(env, sessionOptions, "model/512/transmission_model.onnx")

        val transmissionInput = OnnxTensor.createTensor(env, FloatBuffer.wrap(albedoOutput), longArrayOf(1, 3, 512, 512))

        viewModel.updateLoadingText("Running Transmission Model...")
        val transmissionOutput = transmissionInput.use { input ->
            ortSessionTransmission.run(mapOf("input.1" to input)).use { results ->
                (results.get(0) as OnnxTensor).use { tensor ->
                    FloatArray(tensor.floatBuffer.remaining()).also { tensor.floatBuffer.get(it) }
                }
            }
        }

        // Close the session
        ortSessionTransmission.close()

        val T: FloatArray = transmissionOutput.map { (it * 0.5f) + 0.5f }.toFloatArray()

        val TResized = Mat(hazyImg.rows(), hazyImg.cols(), CvType.CV_32F)
        TResized.put(0, 0, T)


        Log.d("dehaze", "Transmission output computed successfully")

        viewModel.updateLoadingText("Resizing Image...")
        val hazyResized = Mat()
        Imgproc.resize(hazyImg, hazyResized, Size(256.0, 256.0), 0.0, 0.0, Imgproc.INTER_CUBIC)

        viewModel.updateLoadingText("Preprocessing Image...")
        val airlightInput = preprocess(hazyResized, env)
        hazyResized.release()

        viewModel.updateLoadingText("Loading Airlight Model...")
        val ortSessionAirlight = loadModelFromAssets(env, sessionOptions, "model/512/airlight_model.onnx")
        sessionOptions.close()

        viewModel.updateLoadingText("Running Airlight Model...")
        val airlightOutput = airlightInput.use { input ->
            ortSessionAirlight.run(mapOf("input.1" to input)).use { results ->
                (results.get(0) as OnnxTensor).floatBuffer.array()
            }
        }

        // Close the session
        ortSessionAirlight.close()

        Log.d("dehaze", "Airlight output computed successfully")

        val airlightRed = airlightOutput[0]
        val airlightGreen = airlightOutput[1]
        val airlightBlue = airlightOutput[2]

        viewModel.updateLoadingText("Normalizing Image...")
        val hazyImgNorm = Mat()
        Core.normalize(hazyImg, hazyImgNorm, 0.0, 1.0, Core.NORM_MINMAX, CvType.CV_32FC3)
        hazyImg.release()

        viewModel.updateLoadingText("Clearing Image...")
        val clearImg = Mat(hazyImgNorm.rows(), hazyImgNorm.cols(), CvType.CV_32FC3)

        viewModel.updateLoadingText("Processing Image...")
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

        viewModel.updateLoadingText("Resizing Image...")
        val clearImgResized = Mat()
        Imgproc.resize(clearImg, clearImgResized, imSize, 0.0, 0.0, Imgproc.INTER_CUBIC)
        clearImg.release()

        viewModel.updateLoadingText("Converting Image...")
        clearImgResized.convertTo(clearImgResized, CvType.CV_8U)
        Imgproc.cvtColor(clearImgResized, clearImgResized, Imgproc.COLOR_RGB2BGR)

        viewModel.updateLoadingText("Saving Image...")
        FileImageWriter.getInstance()!!.saveMatToUserDir(clearImgResized, ImageFileAttribute.FileType.JPEG)
        clearImgResized.release()

    }

    fun slowDehazeImage(bitmap: Bitmap) {
        val size = 256
        val overlap = 150
        val env = OrtEnvironment.getEnvironment()
        val sessionOptions = OrtSession.SessionOptions().apply {
            setMemoryPatternOptimization(true)
            addConfigEntry("session.use_device_memory_mapping", "1")
            addConfigEntry("session.enable_stream_execution", "1")
        }

        viewModel.updateLoadingText("Loading Albedo Model...")

        val (img,imSize, hazyImgList) = loadAndDivideWithOverlap(bitmap, size, overlap)
        viewModel.updateLoadingText("Preprocessing Image...")
        val processedPatches = mutableListOf<Mat>()
        val ortSessionAlbedo = loadModelFromAssets(env, sessionOptions, "model/albedo_model.onnx")
        val ortSessionTransmission = loadModelFromAssets(env, sessionOptions, "model/transmission_model.onnx")
        for (hazyImg in hazyImgList) {
            val hazyInput = preprocess(hazyImg, env)
            val albedoOutput = hazyInput.use { input ->
                ortSessionAlbedo.run(mapOf("input.1" to input)).use { results ->
                    val tensor = results.get(0) as OnnxTensor
                    tensor.use { t ->
                        FloatArray(t.floatBuffer.remaining()).also { t.floatBuffer.get(it) }
                    }
                }
            }
            val transmissionInput = OnnxTensor.createTensor(env, FloatBuffer.wrap(albedoOutput), longArrayOf(1, 3, 256, 256))
            val transmissionOutput = transmissionInput.use { input ->
                ortSessionTransmission.run(mapOf("input.1" to input)).use { results ->
                    val tensor = results.get(0) as OnnxTensor
                    tensor.use { t ->
                        FloatArray(t.floatBuffer.remaining()).also { t.floatBuffer.get(it) }
                    }
                }
            }
            val reshapedTransmission = Array(size) { FloatArray(size) }
            for (h in 0 until size) {
                for (w in 0 until size) {
                    reshapedTransmission[h][w] = transmissionOutput[h * size + w]
                }
            }
            val mat = Mat(size, size, CvType.CV_32F)
            for (h in 0 until size) {
                for (w in 0 until size) {
                    mat.put(h, w, floatArrayOf(reshapedTransmission[h][w]))  // Wrap the value in a FloatArray
                }
            }
            processedPatches.add(mat)
            Log.d("dehaze", "Processed patch ${processedPatches.size}/${hazyImgList.size}")
        }
        ortSessionAlbedo.close()
        ortSessionTransmission.close()
        val finalImage = reconstructImage(processedPatches, imSize, size, overlap)
        val T = Mat()
        Core.multiply(finalImage, Scalar(0.5), T)
        Core.add(T, Scalar(0.5), T)
        val hazyResized = Mat()
        Imgproc.resize(img, hazyResized, Size(128.0, 128.0), 0.0, 0.0, Imgproc.INTER_CUBIC)
        val airlightInput = preprocess(hazyResized, env)
        hazyResized.release()
        val ortSessionAirlight = loadModelFromAssets(env, sessionOptions, "model/airlight_model.onnx")
        sessionOptions.close()
        val airlightOutput = airlightInput.use { input ->
            ortSessionAirlight.run(mapOf("input.1" to input)).use { results ->
                (results.get(0) as OnnxTensor).floatBuffer.array()
            }
        }
        ortSessionAirlight.close()
        val airlightRed = airlightOutput[0]
        val airlightGreen = airlightOutput[1]
        val airlightBlue = airlightOutput[2]
        val hazyImgNorm = Mat()
        Core.normalize(img, hazyImgNorm, 0.0, 1.0, Core.NORM_MINMAX, CvType.CV_32FC3)
        img.release()

        viewModel.updateLoadingText("Clearing Image...")
        val clearImg = Mat(hazyImgNorm.rows(), hazyImgNorm.cols(), CvType.CV_32FC3)

        viewModel.updateLoadingText("Processing Image...")
        for (i in 0 until hazyImgNorm.rows()) {
            for (j in 0 until hazyImgNorm.cols()) {
                val tVal = T.get(i, j)[0]
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
        clearImg.convertTo(clearImg, CvType.CV_8U)
        Imgproc.cvtColor(clearImg, clearImg, Imgproc.COLOR_RGB2BGR)
        FileImageWriter.getInstance()!!.saveMatToUserDir(clearImg, ImageFileAttribute.FileType.JPEG)
    }

    private fun reconstructImage(patches: List<Mat>, imSize: Pair<Int, Int>, size: Int, overlap: Int): Mat {
        val newSize = size - overlap  // e.g. 256 when size == 512 and overlap == 256
        val W = imSize.first   // width
        val H = imSize.second  // height

        // Create output Mat (reconstructed) and a count Mat for averaging.
        val rec = Mat.zeros(H, W, CvType.CV_32F)
        val count = Mat.zeros(H, W, CvType.CV_32F)

        var patchIndex = 0

        val newOverlap = overlap - 22;

        for (i in 0 until H step newSize) {
            for (j in 0 until W step newSize) {
                // Determine the region where the current patch should be placed.
                val (rowStart, rowEnd) = when {
                    i == 0 -> Pair(0, newSize + newOverlap)
                    i + newSize + newOverlap >= H -> Pair(H - newSize - newOverlap, H)
                    else -> Pair(i - newOverlap / 2, i - newOverlap / 2 + newSize + newOverlap)
                }
                val (colStart, colEnd) = when {
                    j == 0 -> Pair(0, newSize + newOverlap)
                    j + newSize + newOverlap >= W -> Pair(W - newSize - newOverlap, W)
                    else -> Pair(j - newOverlap / 2, j - newOverlap / 2 + newSize + newOverlap)
                }

                // Retrieve the patch. It is assumed to be a single-channel CV_32F Mat.
                val patch = patches[patchIndex]
                val patchH = rowEnd - rowStart
                val patchW = colEnd - colStart

                // For each pixel in the region, add the patch value and update the count.
                for (m in 0 until patchH) {
                    for (n in 0 until patchW) {
                        // Get current value from the reconstructed image.
                        val recVal = rec.get(rowStart + m, colStart + n)[0]
                        // Get the corresponding patch value.
                        val patchVal = patch.get(m, n)[0]
                        // Sum the patch value into the reconstruction.
                        rec.put(rowStart + m, colStart + n, recVal + patchVal)

                        // Update the count.
                        val cntVal = count.get(rowStart + m, colStart + n)[0]
                        count.put(rowStart + m, colStart + n, cntVal + 1.0)
                    }
                }
                patchIndex++
            }
        }

        // Ensure no division by zero: replace any count values less than 1 with 1.
        val ones = Mat.ones(count.size(), count.type())
        Core.max(count, ones, count)
        // Element-wise division: rec = rec / count
        Core.divide(rec, count, rec)
        return rec
    }

    fun fastDehazeImage(bitmap: Bitmap){
        val gamma = 1.0f
        val T_min = 0.2
        val brightness_offset = 0.05f
        val size = 512
        val env = OrtEnvironment.getEnvironment()
        val sessionOptions = OrtSession.SessionOptions().apply {
            setMemoryPatternOptimization(true)
            addConfigEntry("session.use_device_memory_mapping", "1")
            addConfigEntry("session.enable_stream_execution", "1")
        }
        viewModel.updateLoadingText("Loading Albedo Model...")
        val (img, fullImgResized, hazyImgList) = loadAndDivide(bitmap, size)
        val fullImgInput = preprocess(fullImgResized, env)
        val ortSessionAlbedo = loadModelFromAssets(env, sessionOptions, "model/512/albedo_model.onnx")
        val ortSessionTransmission = loadModelFromAssets(env, sessionOptions, "model/512/transmission_model.onnx")
        val albedoOutput = fullImgInput.use { input ->
            ortSessionAlbedo.run(mapOf("input.1" to input)).use { results ->
                (results.get(0) as OnnxTensor).use { tensor ->
                    FloatArray(tensor.floatBuffer.remaining()).also { tensor.floatBuffer.get(it) }
                }
            }
        }
        ortSessionAlbedo.close()
        val transmissionInput = OnnxTensor.createTensor(env, FloatBuffer.wrap(albedoOutput), longArrayOf(1, 3, 512, 512))

        viewModel.updateLoadingText("Running Transmission Model...")
        val transmissionOutput = transmissionInput.use { input ->
            ortSessionTransmission.run(mapOf("input.1" to input)).use { results ->
                (results.get(0) as OnnxTensor).use { tensor ->
                    FloatArray(tensor.floatBuffer.remaining()).also { tensor.floatBuffer.get(it) }
                }
            }
        }
        ortSessionTransmission.close()
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
        Imgproc.resize(transmissionMat, TResized, img.size(), 0.0, 0.0, Imgproc.INTER_CUBIC)
        TResized.convertTo(TResized, CvType.CV_32F)
        Imgproc.bilateralFilter(TResized, TResized, 9, 75.0, 75.0)
        Core.min(TResized, Scalar(1.0), TResized)
        Core.max(TResized, Scalar(T_min), TResized)
        val processedPatches = mutableListOf<FloatArray>()
        val ortSessionAirlight = loadModelFromAssets(env, sessionOptions, "model/512/airlight_model.onnx")

        for (hazyImg in hazyImgList){
            val hazyResizedPatch = Mat()
            Imgproc.resize(hazyImg, hazyResizedPatch, Size(256.0,256.0), 0.0, 0.0, Imgproc.INTER_CUBIC)
            val hazyInput = preprocess(hazyResizedPatch, env)
            val airlightOutputPatched = hazyInput.use { input ->
                ortSessionAirlight.run(mapOf("input.1" to input)).use { results ->
                    (results.get(0) as OnnxTensor).floatBuffer.array()
                }
            }
            processedPatches.add(airlightOutputPatched)
        }
        val hazyResized = Mat()
        Imgproc.resize(img, hazyResized, Size(256.0, 256.0), 0.0, 0.0, Imgproc.INTER_CUBIC)
        val airlightInput = preprocess(hazyResized, env)
        val airlightOutput = airlightInput.use { input ->
            ortSessionAirlight.run(mapOf("input.1" to input)).use { results ->
                (results.get(0) as OnnxTensor).floatBuffer.array()
            }
        }
        ortSessionAirlight.close()
        val airlightOutputNormalize: FloatArray = airlightOutput.map { (it * 0.5f) + 0.5f }.toFloatArray()
        val airlightRed = airlightOutputNormalize[0]
        val airlightGreen = airlightOutputNormalize[1]
        val airlightBlue = airlightOutputNormalize[2]
        val hazyImgNorm = Mat()
        Core.normalize(img, hazyImgNorm, 0.0, 1.0, Core.NORM_MINMAX, CvType.CV_32FC3)
        img.release()

        val eps = 0.001f
        val clearImg = Mat(hazyImgNorm.rows(), hazyImgNorm.cols(), CvType.CV_32FC3)
        for (i in 0 until hazyImgNorm.rows()) {
            for (j in 0 until hazyImgNorm.cols()) {
                val pixel = hazyImgNorm.get(i, j)
                val Tval = TResized.get(i, j)[0].toFloat()
                val red = ((pixel[0].toFloat() - airlightRed * (1 - Tval)) / max(Tval, eps)).coerceIn(0f, 1f)
                val green = ((pixel[1].toFloat() - airlightGreen * (1 - Tval)) / max(Tval, eps)).coerceIn(0f, 1f)
                val blue = ((pixel[2].toFloat() - airlightBlue * (1 - Tval)) / max(Tval, eps)).coerceIn(0f, 1f)
                clearImg.put(i, j, red.toDouble() * 255, green.toDouble()* 255, blue.toDouble()* 255)
            }
        }
        clearImg.convertTo(clearImg, CvType.CV_8U)
        Imgproc.cvtColor(clearImg, clearImg, Imgproc.COLOR_RGB2BGR)

        FileImageWriter.getInstance()!!.saveMatToUserDir(clearImg, ImageFileAttribute.FileType.JPEG)
        clearImg.release()

    }
}