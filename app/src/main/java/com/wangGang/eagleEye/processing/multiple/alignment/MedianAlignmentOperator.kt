package com.wangGang.eagleEye.processing.multiple.alignment

import com.wangGang.eagleEye.io.FileImageReader
import com.wangGang.eagleEye.io.FileImageWriter
import com.wangGang.eagleEye.io.ImageFileAttribute
import com.wangGang.eagleEye.model.AttributeHolder
import com.wangGang.eagleEye.thread.FlaggingThread
import org.opencv.core.Mat
import org.opencv.photo.Photo
import java.util.concurrent.Semaphore

/**
 * Performs MTB median alignment. Converts the images into median threshold bitmaps (1 for above median luminance threshold, 0 otherwise).
 * It is aligned by BIT operations.
 * Created by NeilDG on 12/17/2016.
 */
class MedianAlignmentOperator(private val imageSequenceList: Array<String>, private val resultNames: Array<String>) {

    companion object {
        private const val TAG = "ExposureAlignmentOperator"
    }

    fun perform() {
        val mtbAligner = Photo.createAlignMTB()
        val processMatList = imageSequenceList.toList()
            .map { FileImageReader.getInstance()!!.imReadFullPath(it) }
            .toMutableList()
        mtbAligner.process(processMatList, processMatList)

        for (i in 1 until processMatList.size) {
            FileImageWriter.getInstance()!!.saveMatrixToImage(
                processMatList[i], resultNames[i - 1], ImageFileAttribute.FileType.JPEG
            )
        }

        AttributeHolder.getSharedInstance()!!.putValue("WARPED_IMAGES_LENGTH_KEY", processMatList.size - 1)
    }

    private inner class MedianAlignWorker(
        semaphore: Semaphore,
        private val referenceMat: Mat,
        private val comparingMat: Mat
    ) : FlaggingThread(semaphore) {

        private var resultMat: Mat? = null

        override fun run() {
            val mtbAligner = Photo.createAlignMTB()
            val processMatList = mutableListOf(referenceMat, comparingMat)

            mtbAligner.process(processMatList, processMatList)

            resultMat = processMatList[1]
            finishWork()
        }

        fun getAlignedMat(): Mat? {
            return resultMat
        }
    }
}