package com.wangGang.eagleEye.processing.multiple.alignment

import android.util.Log
import com.wangGang.eagleEye.constants.ParameterConfig
import com.wangGang.eagleEye.io.FileImageReader
import com.wangGang.eagleEye.thread.FlaggingThread
import org.opencv.core.CvType
import org.opencv.core.DMatch
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.FastFeatureDetector
import org.opencv.features2d.ORB
import java.util.concurrent.Semaphore

/**
 * Compare LR reference mat and match features to LR2...LRN.
 * Created by NeilDG on 3/6/2016.
 */
class FeatureMatchingOperator(
    private val referenceMat: Mat,
    private val comparingMatList: Array<String>
) {
    lateinit var refKeypoint: MatOfKeyPoint
        private set
    private var referenceDescriptor: Mat = Mat()

    val lrKeypointsList: Array<MatOfKeyPoint?> = arrayOfNulls(
        comparingMatList.size
    )
    private val lrDescriptorList = arrayOfNulls<Mat>(comparingMatList.size)
    private val dMatchesList = arrayOfNulls<MatOfDMatch>(comparingMatList.size)

    // Change FeatureDetector to FastFeatureDetector
    private val featureDetector: FastFeatureDetector = FastFeatureDetector.create()
    private val orb: ORB = ORB.create() // Use ORB for feature detection and descriptor extraction
    private val matcher: DescriptorMatcher =
        DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)

    fun getdMatchesList(): Array<MatOfDMatch?> {
        return this.dMatchesList
    }

    fun perform() {
        this.detectFeaturesInReference()
        for (i in comparingMatList.indices) {
            val comparingMat = FileImageReader.getInstance()?.imReadFullPath(comparingMatList[i])!!
            // Perform feature matching sequentially
            val featureMatcher = FeatureMatcher(
                Semaphore(1), // Dummy semaphore, not really needed for single-threaded execution
                this.referenceDescriptor,
                comparingMat
            )

            featureMatcher.startWork() // Execute the work synchronously
            featureMatcher.join()
            // Store the results
            dMatchesList[i] = featureMatcher.matches
            lrKeypointsList[i] = featureMatcher.lRKeypoint
        }
    }


    private fun detectFeaturesInReference() {
        // Find features in reference LR image
        this.refKeypoint = MatOfKeyPoint()
        orb.detectAndCompute(this.referenceMat, Mat(), this.refKeypoint, this.referenceDescriptor)
        Log.d(TAG, "Number of keypoints detected in reference: ${refKeypoint.size()}")
    }

    private inner class FeatureMatcher(
        semaphore: Semaphore,
        private val refDescriptor: Mat,
        private val comparingMat: Mat
    ) : FlaggingThread(semaphore) {
        private val featureDetector: FastFeatureDetector = FastFeatureDetector.create()
        private val orb: ORB = ORB.create() // Use ORB for feature detection and descriptor extraction
        private val matcher: DescriptorMatcher =
            DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)

        var lRKeypoint: MatOfKeyPoint? = null
            private set
        private var descriptor: Mat = Mat()
        var matches: MatOfDMatch? = null
            private set

        override fun run() {
            // Detect features in comparing mat
            this.lRKeypoint = MatOfKeyPoint()
            Log.d(TAG, "ComparingMat size for index: ${comparingMat.size()}")
            orb.detectAndCompute(this.comparingMat, Mat(), this.lRKeypoint, this.descriptor)
            Log.d(TAG, "Number of keypoints detected in comparing image: ${lRKeypoint?.size()}")
            this.matches = this.matchFeaturesToReference()
            Log.d(TAG, "Number of matches found: ${matches?.size()}")
            this.finishWork()
            this.descriptor.release()
        }

        private fun matchFeaturesToReference(): MatOfDMatch {
            val initialMatch = MatOfDMatch()
            Log.d(TAG, "Reference descriptor type: ${CvType.typeToString(refDescriptor.type())}")
            Log.d(TAG, "Comparing descriptor type: ${CvType.typeToString(descriptor.type())}")
            Log.d(TAG, "Reference descriptor size: ${refDescriptor.size()}")
            Log.d(TAG, "Comparing descriptor size: ${descriptor.size()}")

            // Check if either descriptor is empty to avoid crashes.
            if (refDescriptor.empty() || descriptor.empty()) {
                Log.e(
                    TAG,
                    "One or both descriptors are empty. " +
                            "Reference descriptor size: ${refDescriptor.size()}, " +
                            "Comparing descriptor size: ${descriptor.size()}"
                )
                // Return an empty MatOfDMatch if there is nothing to match.
                return MatOfDMatch()
            }

            // Perform feature matching.
            this.matcher.match(this.refDescriptor, this.descriptor, initialMatch)

            // Retrieve the minimum match distance threshold from preferences.
            val minDistance: Float = ParameterConfig.getPrefsFloat(
                ParameterConfig.FEATURE_MINIMUM_DISTANCE_KEY,
                999.0f
            )

            // Only select good matches based on the distance threshold.
            val dMatchList = initialMatch.toArray()
            val goodMatchesList: MutableList<DMatch> = ArrayList()
            for (match in dMatchList) {
                if (match.distance < minDistance) {
                    goodMatchesList.add(match)
                }
            }

            initialMatch.release()

            // Convert the filtered list to a MatOfDMatch.
            val goodMatches = MatOfDMatch()
            goodMatches.fromArray(*goodMatchesList.toTypedArray())

            return goodMatches
        }

    }

    companion object {
        private const val TAG = "FeatureMatchingOperator"
    }
}