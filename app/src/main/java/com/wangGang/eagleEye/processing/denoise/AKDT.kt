package com.wangGang.eagleEye.processing.denoise

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.wangGang.eagleEye.processing.imagetools.ImageOperator.bitmapToMat
import com.wangGang.eagleEye.ui.utils.ProgressManager
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.InputStream
import java.nio.FloatBuffer

class AKDT(private val context: Context) {
    private val ortEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val ortSessionOptions by lazy {
        OrtSession.SessionOptions().apply {
            setMemoryPatternOptimization(true)
            addConfigEntry("session.use_device_memory_mapping", "1")
            addConfigEntry("session.enable_stream_execution", "1")
        }
    }

    fun release() {
        ortEnvironment.close()
    }

    private fun loadFromAssets(): Mat {
        var img: Mat? = null
        try {
            val inputStream: InputStream = context.assets.open("test/denoise/input.png")
            val byteArray = inputStream.readBytes()
            val matOfByte = MatOfByte(*byteArray)
            img = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR)
            matOfByte.release()
            if (img.empty()) {
                throw IllegalArgumentException("Image decoding failed")
            }

            val rgbImg = Mat()
            Imgproc.cvtColor(img, rgbImg, Imgproc.COLOR_BGR2RGB)
            img.release()
            img = rgbImg

            val rotatedImg = Mat()
            Core.rotate(img, rotatedImg, Core.ROTATE_90_COUNTERCLOCKWISE)
            img.release()
            img = rotatedImg
            return img
        } catch (e: Exception) {
            img?.release()
            throw IllegalArgumentException("Image not found in assets or decoding failed", e)
        }
    }

    private fun loadModelFromAssets(modelPath: String): OrtSession {
        return context.assets.open(modelPath).use { inputStream ->
            val modelBytes = inputStream.readBytes()
            ortEnvironment.createSession(modelBytes, ortSessionOptions)
        }
    }

    fun denoiseImage(bitmap: Bitmap): Bitmap {
        val patchWithOverlap = 512
        val overlap = 28
        val validPatchSize = 512 - overlap * 2

        var mat: Mat = Mat()
        var imagePadded: Mat? = null
        var outputImage: Mat? = null
        var denoiseSession: OrtSession? = null
        var finalOutputImage: Mat? = null
        var outputBgr: Mat? = null
        var outputBitmap: Bitmap? = null

        try {
            Utils.bitmapToMat(bitmap, mat)
            if (mat.channels() == 4) {
                val tmp = Mat()
                Imgproc.cvtColor(mat, tmp, Imgproc.COLOR_RGBA2RGB)
                mat.release()
                mat = tmp
            }
            
            mat.convertTo(mat, CvType.CV_32F, 1.0 / 255.0)

            val h = mat.rows()
            val w = mat.cols()
            // val c = mat.channels()

            val padH = (validPatchSize - (h % validPatchSize)) % validPatchSize
            val padW = (validPatchSize - (w % validPatchSize)) % validPatchSize

            imagePadded = Mat()
            Core.copyMakeBorder(
                mat, imagePadded, overlap, padH + overlap, overlap, padW + overlap,
                Core.BORDER_REFLECT
            )
            mat.release()

            val paddedH = imagePadded.rows()
            val paddedW = imagePadded.cols()
            outputImage = Mat.zeros(imagePadded.size(), imagePadded.type())

            ProgressManager.getInstance().nextTask()

            denoiseSession = loadModelFromAssets("model/akdt.onnx")
            val inputName = denoiseSession.inputNames.iterator().next()
            // val outputName = denoiseSession.outputNames.iterator().next()

            val totalPatches = ((paddedH - 2 * overlap) / validPatchSize) * ((paddedW - 2 * overlap) / validPatchSize)
            var patchCount = 0

            ProgressManager.getInstance().nextTask()
            Log.d("AKDT", "denoiseImage - Denoising Image")

            for (i in overlap until paddedH - overlap step validPatchSize) {
                for (j in overlap until paddedW - overlap step validPatchSize) {
                    patchCount++
                    val progress = (patchCount.toDouble() / totalPatches * 100).toInt()
                    if (patchCount % 5 == 0 || patchCount == totalPatches) {
                        Log.d("DenoiseImage", "Processing patch $patchCount of $totalPatches ($progress%)")
                    }

                    var iStart = i - overlap
                    var jStart = j - overlap

                    var iEnd = (iStart + patchWithOverlap).coerceAtMost(paddedH)
                    var jEnd = (jStart + patchWithOverlap).coerceAtMost(paddedW)

                    if (iEnd - iStart < patchWithOverlap) {
                        iStart = iEnd - patchWithOverlap
                    }
                    if (jEnd - jStart < patchWithOverlap) {
                        jStart = jEnd - patchWithOverlap
                    }

                    val patch = imagePadded.submat(iStart, iEnd, jStart, jEnd)

                    val inputShape = longArrayOf(1, patch.channels().toLong(), patch.rows().toLong(), patch.cols().toLong())
                    val patchData = FloatArray(patch.rows() * patch.cols() * patch.channels())
                    patch.get(0, 0, patchData)

                    val chwData = FloatArray(patch.channels() * patch.rows() * patch.cols())
                    val channels = patch.channels()
                    val rows = patch.rows()
                    val cols = patch.cols()

                    for (r in 0 until rows) {
                        for (cl in 0 until cols) {
                            for (ch in 0 until channels) {
                                chwData[ch * rows * cols + r * cols + cl] =
                                    patchData[r * cols * channels + cl * channels + ch]
                            }
                        }
                    }

                    var inputTensor: OnnxTensor? = null
                    var results: OrtSession.Result? = null
                    var outputOnnxTensor: OnnxTensor? = null
                    var outputMat: Mat? = null
                    var destSubmat: Mat? = null
                    var srcSubmat: Mat? = null

                    try {
                        inputTensor = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(chwData), inputShape)
                        results = denoiseSession.run(mapOf(inputName to inputTensor))
                        outputOnnxTensor = results.get(0) as OnnxTensor
                        val outputFloatBuffer = outputOnnxTensor.floatBuffer
                        val outputShape = outputOnnxTensor.info.shape

                        val outChannels = outputShape[1].toInt()
                        val outH = outputShape[2].toInt()
                        val outW = outputShape[3].toInt()

                        val outputHwc = FloatArray(outChannels * outH * outW)

                        for (ch in 0 until outChannels) {
                            for (r in 0 until outH) {
                                for (cl in 0 until outW) {
                                    outputHwc[r * outW * outChannels + cl * outChannels + ch] =
                                        outputFloatBuffer.get(ch * outH * outW + r * outW + cl)
                                }
                            }
                        }

                        outputMat = Mat(outH, outW, CvType.CV_32FC3)
                        outputMat.put(0, 0, outputHwc)

                        Core.max(outputMat, Scalar(0.0, 0.0, 0.0), outputMat)
                        Core.min(outputMat, Scalar(1.0, 1.0, 1.0), outputMat)

                        val destRoiRowsStart = i
                        val destRoiRowsEnd = (i + validPatchSize).coerceAtMost(paddedH - overlap)
                        val destRoiColsStart = j
                        val destRoiColsEnd = (j + validPatchSize).coerceAtMost(paddedW - overlap)

                        val srcRoiRowsStart = overlap
                        val srcRoiRowsEnd = overlap + (destRoiRowsEnd - destRoiRowsStart)
                        val srcRoiColsStart = overlap
                        val srcRoiColsEnd = overlap + (destRoiColsEnd - destRoiColsStart)

                        destSubmat = outputImage.submat(destRoiRowsStart, destRoiRowsEnd, destRoiColsStart, destRoiColsEnd)
                        srcSubmat = outputMat.submat(srcRoiRowsStart, srcRoiRowsEnd, srcRoiColsStart, srcRoiColsEnd)

                        srcSubmat.copyTo(destSubmat)

                    } finally {
                        inputTensor?.close()
                        outputOnnxTensor?.close()
                        results?.close()
                        outputMat?.release()
                        destSubmat?.release()
                        srcSubmat?.release()
                    }
                }
            }

            finalOutputImage = outputImage.submat(overlap, h + overlap, overlap, w + overlap)
            outputImage.release()

            outputBgr = Mat()
            finalOutputImage.convertTo(outputBgr, CvType.CV_8U, 255.0)
//            Imgproc.cvtColor(outputBgr, outputBgr, Imgproc.COLOR_RGB2BGR)

            outputBitmap = Bitmap.createBitmap(outputBgr.cols(), outputBgr.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(outputBgr, outputBitmap)

            Log.d("AKDT", "Denoising completed successfully")
            Log.d("AKDT", "Output image size: ${outputBitmap.width}x${outputBitmap.height}")

            ProgressManager.getInstance().nextTask()

            return outputBitmap

        } finally {
            mat?.release()
            imagePadded?.release()
            outputImage?.release()
            denoiseSession?.close()
            finalOutputImage?.release()
            outputBgr?.release()
        }
    }

    fun denoiseImageTest(bitmap: Bitmap, filename: String): Bitmap {
        val patchWithOverlap = 512
        val overlap = 28
        val validPatchSize = 512 - overlap * 2

        var mat: Mat? = null
        var imagePadded: Mat? = null
        var outputImage: Mat? = null
        var denoiseSession: OrtSession? = null
        var finalOutputImage: Mat? = null
        var outputBgr: Mat? = null
        var outputBitmap: Bitmap? = null

        try {
            mat = loadAndResizeFromAssetsTest(filename)
            mat.convertTo(mat, CvType.CV_32F, 1.0 / 255.0)

            val h = mat.rows()
            val w = mat.cols()
            // val c = mat.channels()

            val padH = (validPatchSize - (h % validPatchSize)) % validPatchSize
            val padW = (validPatchSize - (w % validPatchSize)) % validPatchSize

            imagePadded = Mat()
            Core.copyMakeBorder(
                mat, imagePadded, overlap, padH + overlap, overlap, padW + overlap,
                Core.BORDER_REFLECT
            )
            mat.release()

            val paddedH = imagePadded.rows()
            val paddedW = imagePadded.cols()
            outputImage = Mat.zeros(imagePadded.size(), imagePadded.type())

            denoiseSession = loadModelFromAssets("model/akdt.onnx")
            val inputName = denoiseSession.inputNames.iterator().next()
            // val outputName = denoiseSession.outputNames.iterator().next()

            val totalPatches = ((paddedH - 2 * overlap) / validPatchSize) * ((paddedW - 2 * overlap) / validPatchSize)
            var patchCount = 0

            for (i in overlap until paddedH - overlap step validPatchSize) {
                for (j in overlap until paddedW - overlap step validPatchSize) {
                    patchCount++
                    val progress = (patchCount.toDouble() / totalPatches * 100).toInt()
                    if (patchCount % 5 == 0 || patchCount == totalPatches) {
                        Log.d("DenoiseImage", "Processing patch $patchCount of $totalPatches ($progress%)")
                    }

                    var iStart = i - overlap
                    var jStart = j - overlap

                    var iEnd = (iStart + patchWithOverlap).coerceAtMost(paddedH)
                    var jEnd = (jStart + patchWithOverlap).coerceAtMost(paddedW)

                    if (iEnd - iStart < patchWithOverlap) {
                        iStart = iEnd - patchWithOverlap
                    }
                    if (jEnd - jStart < patchWithOverlap) {
                        jStart = jEnd - patchWithOverlap
                    }

                    val patch = imagePadded.submat(iStart, iEnd, jStart, jEnd)

                    val inputShape = longArrayOf(1, patch.channels().toLong(), patch.rows().toLong(), patch.cols().toLong())
                    val patchData = FloatArray(patch.rows() * patch.cols() * patch.channels())
                    patch.get(0, 0, patchData)

                    val chwData = FloatArray(patch.channels() * patch.rows() * patch.cols())
                    val channels = patch.channels()
                    val rows = patch.rows()
                    val cols = patch.cols()

                    for (r in 0 until rows) {
                        for (cl in 0 until cols) {
                            for (ch in 0 until channels) {
                                chwData[ch * rows * cols + r * cols + cl] =
                                    patchData[r * cols * channels + cl * channels + ch]
                            }
                        }
                    }

                    var inputTensor: OnnxTensor? = null
                    var results: OrtSession.Result? = null
                    var outputOnnxTensor: OnnxTensor? = null
                    var outputMat: Mat? = null
                    var destSubmat: Mat? = null
                    var srcSubmat: Mat? = null

                    try {
                        inputTensor = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(chwData), inputShape)
                        results = denoiseSession.run(mapOf(inputName to inputTensor))
                        outputOnnxTensor = results.get(0) as OnnxTensor
                        val outputFloatBuffer = outputOnnxTensor.floatBuffer
                        val outputShape = outputOnnxTensor.info.shape

                        val outChannels = outputShape[1].toInt()
                        val outH = outputShape[2].toInt()
                        val outW = outputShape[3].toInt()

                        val outputHwc = FloatArray(outChannels * outH * outW)

                        for (ch in 0 until outChannels) {
                            for (r in 0 until outH) {
                                for (cl in 0 until outW) {
                                    outputHwc[r * outW * outChannels + cl * outChannels + ch] =
                                        outputFloatBuffer.get(ch * outH * outW + r * outW + cl)
                                }
                            }
                        }

                        outputMat = Mat(outH, outW, CvType.CV_32FC3)
                        outputMat.put(0, 0, outputHwc)

                        Core.max(outputMat, Scalar(0.0, 0.0, 0.0), outputMat)
                        Core.min(outputMat, Scalar(1.0, 1.0, 1.0), outputMat)

                        val destRoiRowsStart = i
                        val destRoiRowsEnd = (i + validPatchSize).coerceAtMost(paddedH - overlap)
                        val destRoiColsStart = j
                        val destRoiColsEnd = (j + validPatchSize).coerceAtMost(paddedW - overlap)

                        val srcRoiRowsStart = overlap
                        val srcRoiRowsEnd = overlap + (destRoiRowsEnd - destRoiRowsStart)
                        val srcRoiColsStart = overlap
                        val srcRoiColsEnd = overlap + (destRoiColsEnd - destRoiColsStart)

                        destSubmat = outputImage.submat(destRoiRowsStart, destRoiRowsEnd, destRoiColsStart, destRoiColsEnd)
                        srcSubmat = outputMat.submat(srcRoiRowsStart, srcRoiRowsEnd, srcRoiColsStart, srcRoiColsEnd)

                        srcSubmat.copyTo(destSubmat)

                    } finally {
                        inputTensor?.close()
                        outputOnnxTensor?.close()
                        results?.close()
                        outputMat?.release()
                        destSubmat?.release()
                        srcSubmat?.release()
                    }
                }
            }

            finalOutputImage = outputImage.submat(overlap, h + overlap, overlap, w + overlap)
            outputImage.release()

            outputBgr = Mat()
            finalOutputImage.convertTo(outputBgr, CvType.CV_8U, 255.0)

            outputBitmap = Bitmap.createBitmap(outputBgr.cols(), outputBgr.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(outputBgr, outputBitmap)

            Log.d("AKDT", "Denoising completed successfully")
            Log.d("AKDT", "Output image size: ${outputBitmap.width}x${outputBitmap.height}")
            return outputBitmap

        } finally {
            mat?.release()
            imagePadded?.release()
            outputImage?.release()
            denoiseSession?.close()
            finalOutputImage?.release()
            outputBgr?.release()
        }
    }

    private fun loadAndResizeFromAssetsTest(filename: String): Mat {
        var img: Mat? = null
        try {
            val inputStream: InputStream = context.assets.open("test/denoise/${filename}")
            val byteArray = inputStream.readBytes()
            val matOfByte = MatOfByte(*byteArray)
            img = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR)
            matOfByte.release()
            require(!img.empty()) { "Image not found or decoding failed for test/${filename}" }

            val rgbImg = Mat()
            Imgproc.cvtColor(img, rgbImg, Imgproc.COLOR_BGR2RGB)
            img.release()
            img = rgbImg
            return img
        } catch (e: Exception) {
            img?.release()
            throw IllegalArgumentException("Image not found in assets or decoding failed", e)
        }
    }
}