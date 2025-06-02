package com.wangGang.eagleEye.processing.denoise

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.wangGang.eagleEye.processing.imagetools.ImageOperator.bitmapToMat
import com.wangGang.eagleEye.ui.viewmodels.CameraViewModel
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

class AKDT(private val context: Context, private val viewModel: CameraViewModel) {
    private val ortEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private val ortSessionOptions by lazy {
        OrtSession.SessionOptions().apply {
            setMemoryPatternOptimization(true)
            addConfigEntry("session.use_device_memory_mapping", "1")
            addConfigEntry("session.enable_stream_execution", "1")
        }
    }
    private fun loadFromAssets(): Mat {
        val inputStream: InputStream = try {
            context.assets.open("test/denoise/input.png")
        } catch (e: Exception) {
            throw IllegalArgumentException("Image not found in assets", e)
        }
        val byteArray = inputStream.readBytes()
        val matOfByte = MatOfByte(*byteArray)
        val img = Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR)
        if (img.empty()) {
            throw IllegalArgumentException("Image decoding failed")
        }

        Imgproc.cvtColor(img, img, Imgproc.COLOR_BGR2RGB)
        return img
    }
    private fun loadModelFromAssets(modelPath: String): OrtSession {
        return context.assets.open(modelPath).use { inputStream ->
            val modelBytes = inputStream.readBytes()
            ortEnvironment.createSession(modelBytes, ortSessionOptions)
        }
    }

    fun denoiseImage(bitmap: Bitmap): Bitmap {
        val patchWithOverlap = 512;
        val overlap = 28
        val validPatchSize = 512 - overlap * 2
//        val mat = bitmapToMat(bitmap)
        val mat = loadFromAssets();
        mat.convertTo(mat, CvType.CV_32F, 1.0 / 255.0)
        val h = mat.rows()
        val w = mat.cols()
        val c = mat.channels()
        val padH = (validPatchSize - (h % validPatchSize)) % validPatchSize
        val padW = (validPatchSize - (w % validPatchSize)) % validPatchSize
        val imagePadded = Mat()
        Core.copyMakeBorder(
            mat, imagePadded, overlap, padH + overlap, overlap, padW + overlap,
            Core.BORDER_REFLECT
        )
        val paddedH = imagePadded.rows()
        val paddedW = imagePadded.cols()
        val outputImage = Mat.zeros(imagePadded.size(), imagePadded.type())
        val denoiseSession = loadModelFromAssets("model/akdt.onnx")
        val inputName = denoiseSession.inputNames.iterator().next()
        val outputName = denoiseSession.outputNames.iterator().next()
        for (i in overlap until paddedH - overlap step validPatchSize) {
            for (j in overlap until paddedW - overlap step validPatchSize) {
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

                // Prepare input tensor for ONNX Runtime (NCHW format)
                // The shape will be [1, Channels, Height, Width]
                val inputShape = longArrayOf(1, patch.channels().toLong(), patch.rows().toLong(), patch.cols().toLong())
                val patchData = FloatArray(patch.rows() * patch.cols() * patch.channels())
                patch.get(0, 0, patchData) // Get HWC data from Mat

                // Convert HWC to CHW for ONNX Runtime
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

                val inputTensor = OnnxTensor.createTensor(ortEnvironment, FloatBuffer.wrap(chwData), inputShape)

                // ONNX inference
                val results = denoiseSession.run(mapOf(inputName to inputTensor))

                // Process output
                val outputOnnxTensor = results.get(0) as OnnxTensor
                val outputFloatBuffer = outputOnnxTensor.floatBuffer
                val outputShape = outputOnnxTensor.info.shape // This will be [1, C, H, W]

                val outChannels = outputShape[1].toInt()
                val outH = outputShape[2].toInt()
                val outW = outputShape[3].toInt()

                val outputHwc = FloatArray(outChannels * outH * outW)

                // Convert CHW output back to HWC
                for (ch in 0 until outChannels) {
                    for (r in 0 until outH) {
                        for (cl in 0 until outW) {
                            outputHwc[r * outW * outChannels + cl * outChannels + ch] =
                                outputFloatBuffer.get(ch * outH * outW + r * outW + cl)
                        }
                    }
                }

                // Create a Mat from the processed output (HWC)
                val outputMat = Mat(outH, outW, CvType.CV_32FC3)
                outputMat.put(0, 0, outputHwc)

                // Clamp values to [0, 1]
                Core.max(outputMat, Scalar(0.0, 0.0, 0.0), outputMat) // Equivalent to numpy.clip lower bound
                Core.min(outputMat, Scalar(1.0, 1.0, 1.0), outputMat) // Equivalent to numpy.clip upper bound


                // Place the denoised valid region back into the output_image
                val destRoiRowsStart = i
                val destRoiRowsEnd = (i + validPatchSize).coerceAtMost(paddedH - overlap)
                val destRoiColsStart = j
                val destRoiColsEnd = (j + validPatchSize).coerceAtMost(paddedW - overlap)

                val srcRoiRowsStart = overlap
                val srcRoiRowsEnd = overlap + (destRoiRowsEnd - destRoiRowsStart)
                val srcRoiColsStart = overlap
                val srcRoiColsEnd = overlap + (destRoiColsEnd - destRoiColsStart)

                val destSubmat = outputImage.submat(destRoiRowsStart, destRoiRowsEnd, destRoiColsStart, destRoiColsEnd)
                val srcSubmat = outputMat.submat(srcRoiRowsStart, srcRoiRowsEnd, srcRoiColsStart, srcRoiColsEnd)

                srcSubmat.copyTo(destSubmat)

                // Close resources for current patch
                inputTensor.close()
                outputOnnxTensor.close()
                results.close() // Close the result collection
            }
        }
        val finalOutputImage = outputImage.submat(overlap, h + overlap, overlap, w + overlap)

        // Convert back to BGR uint8
        val outputBgr = Mat()
        finalOutputImage.convertTo(outputBgr, CvType.CV_8U, 255.0)
        Imgproc.cvtColor(outputBgr, outputBgr, Imgproc.COLOR_RGB2BGR)
        // Convert Mat to Bitmap
        val outputBitmap = Bitmap.createBitmap(outputBgr.cols(), outputBgr.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outputBgr, outputBitmap)
        return outputBitmap
    }
}