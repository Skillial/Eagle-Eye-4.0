package com.wangGang.eagleEye

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wangGang.eagleEye.constants.ParameterConfig
import com.wangGang.eagleEye.io.DirectoryStorage
import com.wangGang.eagleEye.io.FileImageReader
import com.wangGang.eagleEye.io.FileImageWriter
import com.wangGang.eagleEye.model.AttributeHolder
import com.wangGang.eagleEye.processing.ConcreteSuperResolution
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class SuperResolutionTest {
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
        DirectoryStorage.getSharedInstance().createDirectory()
        FileImageWriter.initialize(context)
        FileImageReader.initialize(context)
        ParameterConfig.initialize(context)
        AttributeHolder.initialize(context)

        val result = concreteSuperResolution.superResolutionImage(imageInputMap)
        assertNotNull("Super resolution output is null", result)
    }
}
