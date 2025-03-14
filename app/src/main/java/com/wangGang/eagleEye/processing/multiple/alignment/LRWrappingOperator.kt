package com.wangGang.eagleEye.processing.multiple.alignment

import android.util.Log
import com.wangGang.eagleEye.io.FileImageReader
import com.wangGang.eagleEye.io.FileImageWriter
import com.wangGang.eagleEye.io.ImageFileAttribute
import com.wangGang.eagleEye.model.AttributeHolder
import com.wangGang.eagleEye.thread.FlaggingThread
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import java.util.concurrent.Semaphore

class LRWarpingOperator(
    private val refKeypoint: MatOfKeyPoint,
    private val imagesToWarpList: Array<String>,
    private val resultNames: Array<String>,
    private val goodMatchList: Array<MatOfDMatch?>, // Nullable Array of MatOfDMatch
    private val keyPointList: Array<MatOfKeyPoint?> // Nullable Array of MatOfKeyPoint
) {
    private val warpedMatList: Array<Mat?> = arrayOfNulls(imagesToWarpList.size)

    fun perform() {
        // Multi-threaded Warping
        val warpingWorkers = Array(imagesToWarpList.size) { i ->
            val imageToWarp = FileImageReader.getInstance()!!.imReadFullPath(imagesToWarpList[i])
            WarpingWorker(
                Semaphore(1),  // Semaphore set to 1 to allow single thread execution at a time
                refKeypoint,
                goodMatchList[i],
                keyPointList[i],
                imageToWarp
            )
        }

        // Start warping
        warpingWorkers.forEach { it.startWork() }

        try {
            // Wait for all threads to finish
            warpingWorkers.forEach {
                it.retrieveSemaphore().acquire()  // Acquire the semaphore
                val warpedMat = it.warpedMat

                // Check if warpedMat is not null before calling saveMatrixToImage
                warpedMat?.let { mat ->
                    FileImageWriter.getInstance()!!.saveMatrixToImage(mat, resultNames[warpingWorkers.indexOf(it)], ImageFileAttribute.FileType.JPEG)
                    mat.release()  // Release the matrix after saving
                } ?: run {
                    Log.e(TAG, "Warped matrix is null for worker: ${warpingWorkers.indexOf(it)}")
                }
            }
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        finalizeResult()
    }

    private fun finalizeResult() {
        AttributeHolder.getSharedInstance()!!.putValue("WARPED_IMAGES_LENGTH_KEY", imagesToWarpList.size)
        refKeypoint.release()

        goodMatchList.forEach { it?.release() } // Safe null check for each item in goodMatchList
        keyPointList.forEach { it?.release() } // Safe null check for each item in keyPointList

        goodMatchList.fill(null)
        keyPointList.fill(null)
    }

    fun getWarpedMatList(): Array<Mat?> = warpedMatList

    private fun warpImage(goodMatch: MatOfDMatch?, candidateKeypoint: MatOfKeyPoint?, candidateMat: Mat): Mat {
        val matOfPoint1 = MatOfPoint2f()
        val matOfPoint2 = MatOfPoint2f()

        val keyPoints1 = refKeypoint.toArray()
        val keyPoints2 = candidateKeypoint?.toArray() ?: emptyArray()

        val pointList1 = mutableListOf<Point>()
        val pointList2 = mutableListOf<Point>()

        val dMatchArray = goodMatch?.toArray() ?: emptyArray()

        for (i in dMatchArray.indices) {
//            Log.d(TAG, "DMATCHES${dMatchArray[i]}")
            pointList1.add(keyPoints1[dMatchArray[i].queryIdx].pt)
            pointList2.add(keyPoints2[dMatchArray[i].trainIdx].pt)
        }

        matOfPoint1.fromList(pointList1)
        matOfPoint2.fromList(pointList2)

        Log.d(TAG, "Homography pre info: matOfPoint1 ROWS: ${matOfPoint1.rows()} matOfPoint1 COLS: ${matOfPoint1.cols()}")
        Log.d(TAG, "Homography pre info: matOfPoint2 ROWS: ${matOfPoint2.rows()} matOfPoint2 COLS: ${matOfPoint2.cols()}")

        val homography: Mat = if (matOfPoint1.rows() > 0 && matOfPoint1.cols() > 0 && matOfPoint2.rows() > 0 && matOfPoint2.cols() > 0) {
            Calib3d.findHomography(matOfPoint2, matOfPoint1, Calib3d.RANSAC, 1.0)
        } else {
            Mat()
        }

        Log.d(TAG, "Homography info: ROWS: ${homography.rows()} COLS: ${homography.cols()}")

        matOfPoint1.release()
        matOfPoint2.release()

        pointList1.clear()
        pointList2.clear()

        return performPerspectiveWarping(candidateMat, homography)
    }

    private fun performPerspectiveWarping(inputMat: Mat, homography: Mat): Mat {
        return if (homography.rows() == 3 && homography.cols() == 3) {
            val warpedMat = Mat()
            Imgproc.warpPerspective(inputMat, warpedMat, homography, warpedMat.size(), Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE, Scalar.all(0.0))
            homography.release()
            warpedMat
        } else {
            val warpedMat = Mat()
            inputMat.copyTo(warpedMat)
            homography.release()
            Log.e(TAG, "No homography was found for warp perspective. Returning original mat.")
            warpedMat
        }
    }

    private inner class WarpingWorker(
        semaphore: Semaphore,
        private val refKeypoint: MatOfKeyPoint,
        private val goodMatch: MatOfDMatch?,
        private val candidateKeypoint: MatOfKeyPoint?,
        private val candidateMat: Mat
    ) : FlaggingThread(semaphore) {
        var warpedMat: Mat? = null  // Make warpedMat nullable

        override fun run() {
            warpedMat = warpImage(goodMatch, candidateKeypoint, candidateMat)
            finishWork()
        }

        fun retrieveSemaphore(): Semaphore = semaphore
    }

    companion object {
        private const val TAG = "WarpingOperator"
    }
}
