package com.bgcoding.camera2api.model.single_gaussian

import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Similar to an image patch implementation but this time, the mat is a submat of an original mat
 * Created by NeilDG on 5/23/2016.
 */
class LoadedImagePatch(
    parentMat: Mat,
    patchSize: Int,
    colStart: Int,
    rowStart: Int
) {
    companion object {
        private const val TAG = "LoadedImagePatch"
    }

    private var patchMat: Mat
    private var colStart: Int
    private var rowStart: Int
    private var rowEnd: Int
    private var colEnd: Int

    init {
        val point = Point(colStart.toDouble(), rowStart.toDouble())
        val size = Size(patchSize.toDouble(), patchSize.toDouble())

        this.patchMat = Mat.zeros(size, parentMat.type())
        Imgproc.getRectSubPix(parentMat, size, point, this.patchMat)

        this.colStart = colStart
        this.rowStart = rowStart
        this.rowEnd = this.rowStart + patchSize
        this.colEnd = this.colStart + patchSize
    }

    fun getColStart(): Int = this.colStart

    fun getRowEnd(): Int = this.rowEnd

    fun getRowStart(): Int = this.rowStart

    fun getColEnd(): Int = this.colEnd

    fun getPatchMat(): Mat = this.patchMat

    fun release() {
        patchMat.release()
        patchMat = Mat()
    }
}
