package com.wangGang.eagleEye.processing.multiple.enhancement
import com.wangGang.eagleEye.io.DirectoryStorage
import com.wangGang.eagleEye.io.FileImageWriter
import com.wangGang.eagleEye.io.ImageFileAttribute
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class UnsharpMaskOperator(private val inputMat: Mat, private val index: Int)  {
    private lateinit var outputMat: Mat

    fun perform() {
        val blurMat = Mat()
        this.outputMat = Mat()
        Imgproc.blur(this.inputMat, blurMat, Size(25.0, 25.0))

        Core.addWeighted(this.inputMat, 2.25, blurMat, -1.25, 0.0, this.outputMat, CvType.CV_8UC(this.inputMat.channels()))
        FileImageWriter.getInstance()?.debugSaveMatrixToImage(this.outputMat,
            DirectoryStorage.SR_ALBUM_NAME_PREFIX,
            "sharpen_$index", ImageFileAttribute.FileType.JPEG)

        blurMat.release()
        this.inputMat.release()
    }

    fun getResult(): Mat {
        return this.outputMat
    }
}