package com.bgcoding.camera2api.processing.multiple.alignment

import com.bgcoding.camera2api.io.FileImageWriter
import com.bgcoding.camera2api.io.ImageFileAttribute
import com.bgcoding.camera2api.thread.FlaggingThread
import com.bgcoding.camera2api.model.AttributeHolder
import org.opencv.core.Mat
import org.opencv.photo.Photo
import java.util.concurrent.Semaphore

/**
 * Performs MTB median alignment. Converts the images into median threshold bitmaps (1 for above median luminance threshold, 0 otherwise).
 * It is aligned by BIT operations.
 * Created by NeilDG on 12/17/2016.
 */
class MedianAlignmentOperator(private val imageSequenceList: Array<Mat>, private val resultNames: Array<String>) {

    companion object {
        private const val TAG = "ExposureAlignmentOperator"
    }

    fun perform() {
        val mtbAligner = Photo.createAlignMTB()
        val processMatList = imageSequenceList.toList()

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