package com.wangGang.eagleEye

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wangGang.eagleEye.constants.ParameterConfig
import com.wangGang.eagleEye.io.DirectoryStorage
import com.wangGang.eagleEye.io.FileImageReader
import com.wangGang.eagleEye.io.FileImageWriter
import com.wangGang.eagleEye.io.ImageFileAttribute
import com.wangGang.eagleEye.model.AttributeHolder
import com.wangGang.eagleEye.processing.shadow_remove.SynthShadowRemoval
import com.wangGang.eagleEye.ui.viewmodels.CameraViewModel
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc


@RunWith(AndroidJUnit4::class)
class ShadowTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    //private lateinit var cameraViewModel: CameraViewModel
    private lateinit var synthShadowRemoval: SynthShadowRemoval
    private val imageFileNames = mutableListOf<String>()

    @Before
    fun setUp() {
        //cameraViewModel = CameraViewModel()
        synthShadowRemoval = SynthShadowRemoval(context)

        val assetManager = context.assets
        val assetImages = assetManager.list("test/shadow") ?: throw AssertionError("No images found in assets/test_images")

        imageFileNames.addAll(assetImages.toList())
    }

    private fun loadBitmapFromAssets(fileName: String): Bitmap {
        return context.assets.open("test/shadow/$fileName").use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        } ?: throw IllegalArgumentException("Failed to decode bitmap from assets: $fileName")
    }

    private fun loadOrigBitmapFromAssets(fileName: String): Bitmap {
        return context.assets.open("$fileName").use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        } ?: throw IllegalArgumentException("Failed to decode bitmap from assets: $fileName")
    }

//    @Test
//    fun testSingleShadow() {
//        if (!OpenCVLoader.initDebug()) {
//            Log.e("OpenCV", "Initialization Failed")
//        } else {
//            Log.d("OpenCV", "Initialization Successful")
//        }
//        DirectoryStorage.getSharedInstance().createDirectory()
//        FileImageWriter.initialize(context)
//        FileImageReader.initialize(context)
//        ParameterConfig.initialize(context)
//        AttributeHolder.initialize(context)
//
//        val fileName = imageFileNames.firstOrNull() ?: throw IllegalStateException("No images found")
//        val bitmap = loadBitmapFromAssets(fileName)
//        val result = synthShadowRemoval.removeShadow(bitmap)
//
//        assertNotNull(result)
//    }

    @Test
    fun testMultipleShadow() {
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Initialization Failed")
        } else {
            Log.d("OpenCV", "Initialization Successful")
        }
        DirectoryStorage.getSharedInstance().createDirectory()
        FileImageWriter.initialize(context)
        FileImageReader.initialize(context)
        ParameterConfig.initialize(context)
        AttributeHolder.initialize(context)

        for (fileName in imageFileNames) {
            val bitmap = loadBitmapFromAssets(fileName)

            val startTime = System.currentTimeMillis()
            val resultBitmap = synthShadowRemoval.removeShadowTest(bitmap, fileName)
            val endTime = System.currentTimeMillis()
            Log.d("ShadowRemovalTiming", "$fileName: ${endTime - startTime}")

            FileImageWriter.getInstance()?.saveBitmapImage(resultBitmap, fileName, ImageFileAttribute.FileType.PNG)
            assertNotNull(resultBitmap)
        }
    }
}