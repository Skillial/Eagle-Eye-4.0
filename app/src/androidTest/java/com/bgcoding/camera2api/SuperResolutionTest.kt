package com.bgcoding.camera2api

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bgcoding.camera2api.constants.ParameterConfig
import com.bgcoding.camera2api.io.DirectoryStorage
import com.bgcoding.camera2api.io.FileImageReader
import com.bgcoding.camera2api.io.FileImageWriter
import com.bgcoding.camera2api.model.AttributeHolder
import com.bgcoding.camera2api.processing.ConcreteSuperResolution
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.File
import java.io.FileOutputStream
import androidx.test.rule.GrantPermissionRule;
import com.bgcoding.camera2api.metrics.ImageMetrics
import org.junit.Rule

@RunWith(AndroidJUnit4::class)
class SuperResolutionTest {
    @Rule
    @JvmField
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    )

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val imageInputMap = mutableListOf<String>()
    private val concreteSuperResolution = ConcreteSuperResolution()

    @Before
    fun setUp() {
        val internalDir = File(context.filesDir, "test_images")
        val assetManager = context.assets
        val assetImages = assetManager.list("test_images") ?: throw AssertionError("No images found in assets/test_images")

        if (!internalDir.exists()) {
            internalDir.mkdirs()
        }

        for (imageName in assetImages) {
            assetManager.open("test_images/$imageName").use { inputStream ->
                val outFile = File(internalDir, imageName)

                if (!outFile.exists()) {
                    FileOutputStream(outFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                imageInputMap.add(outFile.absolutePath)
            }
        }

        if (imageInputMap.size != 5) {
            throw AssertionError("Insufficient images for super resolution. Found: ${imageInputMap.size}")
        }
    }



    @Test
    fun testSuperResolution() {
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Initialization Failed")
        } else {
            Log.d("OpenCV", "Initialization Successful")
        }
        // Initialize AVD context
        DirectoryStorage.getSharedInstance().createDirectory()
        FileImageWriter.initialize(context)
        FileImageReader.initialize(context)
        ParameterConfig.initialize(context)
        AttributeHolder.initialize(context)

        // Load images from assets and save temporary files
        val assetManager = context.assets
        val assetImages = assetManager.list("test_images") ?: throw AssertionError("No images found in assets/test_images")
        val tempImagePaths = mutableListOf<String>()

        for (imageName in assetImages) {
            val inputStream = assetManager.open("test_images/$imageName")
            val tempFile = File(context.cacheDir, imageName)
            tempFile.outputStream().use { inputStream.copyTo(it) }
            tempImagePaths.add(tempFile.absolutePath)
        }

        if (tempImagePaths.size != 5) {
            throw AssertionError("Insufficient images for super resolution. Found: ${tempImagePaths.size}")
        }

        // Process images with super resolution
        concreteSuperResolution.superResolutionImage(tempImagePaths) // Pass file paths

        // Load Result Mat
        val resultMat = concreteSuperResolution.getFinalMat()
        assertNotNull("Super resolution output is null", resultMat)

        if (resultMat != null) {
            Log.d("ImageMetrics", "ResultMat: rows=${resultMat.rows()}, cols=${resultMat.cols()}, type=${resultMat.type()}")
        }

        // Load ground truth directly from assets
        val groundTruthInputStream = assetManager.open("ground_truth/ground_truth.jpg")
        val groundTruthByteArray = groundTruthInputStream.readBytes()
        groundTruthInputStream.close()
        val groundTruthMatOfByte = org.opencv.core.MatOfByte(*groundTruthByteArray)
        val groundTruthMat = org.opencv.imgcodecs.Imgcodecs.imdecode(groundTruthMatOfByte, org.opencv.imgcodecs.Imgcodecs.IMREAD_COLOR)
        groundTruthMatOfByte.release()
        if (groundTruthMat.empty()) {
            throw AssertionError("Failed to load ground truth image ${assetImages[0]}")
        }

        // Calculate metrics
        val psnr = ImageMetrics.getPSNR(resultMat!!, groundTruthMat!!)
        Log.d("PSNR", "PSNR: $psnr")
        assert(psnr > 30) { "PSNR is too low, indicating poor quality." }

        // Clean up
        resultMat.release()
        groundTruthMat.release()
    }
}
