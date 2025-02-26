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
import com.wangGang.eagleEye.model.AttributeHolder
import com.wangGang.eagleEye.processing.dehaze.SynthDehaze
import com.wangGang.eagleEye.ui.fragments.CameraViewModel
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader

@RunWith(AndroidJUnit4::class)
class DehazingTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
//    qqprivate lateinit var cameraViewModel: CameraViewModel
    private lateinit var synthDehaze: SynthDehaze
    private val imageFileNames = mutableListOf<String>()

    @Before
    fun setUp() {
//        cameraViewModel = CameraViewModel()
        synthDehaze = SynthDehaze(context)

        val assetManager = context.assets
        val assetImages = assetManager.list("hazy/I-Haze") ?: throw AssertionError("No images found in assets/test_images")

        imageFileNames.addAll(assetImages.toList())
    }

    private fun loadBitmapFromAssets(fileName: String): Bitmap {
        return context.assets.open("hazy/I-Haze/$fileName").use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        } ?: throw IllegalArgumentException("Failed to decode bitmap from assets: $fileName")
    }

    @Test
    fun testSingleDehazing() {
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

        val fileName = imageFileNames.firstOrNull() ?: throw IllegalStateException("No images found")
        val bitmap = loadBitmapFromAssets(fileName)
        val result = synthDehaze.dehazeImage(bitmap)

        assertNotNull(result)
    }

    @Test
    fun testMultipleDehazing() {
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
            val result = synthDehaze.dehazeImage(bitmap)

            assertNotNull(result)
        }
    }
}
