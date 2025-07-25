package com.wangGang.eagleEye

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wangGang.eagleEye.constants.ParameterConfig
import com.wangGang.eagleEye.io.DirectoryStorage
import com.wangGang.eagleEye.io.FileImageReader
import com.wangGang.eagleEye.io.FileImageWriter
import com.wangGang.eagleEye.io.ImageFileAttribute
import com.wangGang.eagleEye.model.AttributeHolder
import com.wangGang.eagleEye.processing.denoise.AKDT
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader
import java.io.IOException


@RunWith(AndroidJUnit4::class)
class DenoiseTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    //private lateinit var cameraViewModel: CameraViewModel
    private lateinit var AKDT: AKDT
    private val imageFileNames = mutableListOf<String>()

    @Before
    fun setUp() {
        //cameraViewModel = CameraViewModel()
        AKDT = AKDT(context)

        val assetManager = context.assets
        val assetImages = assetManager.list("test/denoise") ?: throw AssertionError("No images found in assets/test_images")

        imageFileNames.addAll(assetImages.toList())
    }

    private fun loadBitmapFromAssets(fileName: String): Bitmap {
        return context.assets.open("test/denoise/$fileName").use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        } ?: throw IllegalArgumentException("Failed to decode bitmap from assets: $fileName")
    }

    private fun loadOrigBitmapFromAssets(fileName: String): Bitmap {
        return context.assets.open("$fileName").use { inputStream ->
            BitmapFactory.decodeStream(inputStream)
        } ?: throw IllegalArgumentException("Failed to decode bitmap from assets: $fileName")
    }

    @Test
    fun testMultipleDenoise() {
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
            val resultBitmap = AKDT.denoiseImageTest(bitmap, fileName)
            val endTime = System.currentTimeMillis()
            Log.d("AKDTTiming", "$fileName: ${endTime - startTime}")

            saveBitmapImage(resultBitmap, fileName, ImageFileAttribute.FileType.PNG)
            assertNotNull(resultBitmap)
        }
    }
}

private fun saveBitmapImage(bitmap: Bitmap, fileName: String, fileType: ImageFileAttribute.FileType) {
    try {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val mimeType = when (fileType) {
            ImageFileAttribute.FileType.PNG -> "image/png"
            ImageFileAttribute.FileType.JPEG, ImageFileAttribute.FileType.JPEG -> "image/jpeg"
        }

        val format = when (fileType) {
            ImageFileAttribute.FileType.PNG -> Bitmap.CompressFormat.PNG
            ImageFileAttribute.FileType.JPEG, ImageFileAttribute.FileType.JPEG -> Bitmap.CompressFormat.JPEG
        }

        val contentValues = ContentValues().apply {
            val nameWithoutExtension = fileName.substringBeforeLast('.')
            put(MediaStore.Images.Media.DISPLAY_NAME, "$nameWithoutExtension${ImageFileAttribute.getFileExtension(fileType)}")
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/eagleEyeTest")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            resolver.openOutputStream(it)?.use { out ->
                bitmap.compress(format, 100, out)
            }
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(it, contentValues, null, null)
            Log.d("saveBitmapImage", "Saved to MediaStore: $uri")
        } ?: Log.e("saveBitmapImage", "Failed to insert MediaStore record.")

    } catch (e: IOException) {
        e.printStackTrace()
    }
}
