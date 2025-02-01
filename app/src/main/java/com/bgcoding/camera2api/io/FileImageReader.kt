package com.bgcoding.camera2api.io

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.imgcodecs.Imgcodecs
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import android.os.Environment

/**
 * Reads images from external dir
 */
class FileImageReader private constructor(private var context: Context?) {

    companion object {
        private const val TAG = "SR_ImageReader"
        private var sharedInstance: FileImageReader? = null

        fun getInstance(): FileImageReader? {
            return sharedInstance
        }

        fun initialize(context: Context) {
            sharedInstance = FileImageReader(context)
        }

        fun destroy() {
            sharedInstance = null
        }
    }

    fun setContext(context: Context) {
        this.context = context
    }

    /**
     * Loads the specified image and returns its byte data
     */
    fun getBytesFromFile(fileName: String, fileType: ImageFileAttribute.FileType): ByteArray? {
        val file = File("${FileImageWriter.getInstance()?.getFilePath()}/${fileName}${ImageFileAttribute.getFileExtension(fileType)}")

        return try {
            if (file.exists()) {
                FileInputStream(file).use { inputStream ->
                    val readBytes = ByteArray(file.length().toInt())
                    inputStream.read(readBytes)
                    readBytes
                }
            } else {
                Log.e(TAG, "$fileName does not exist in ${file.absolutePath} !")
                null
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error reading file ${e.message}")
            null
        }
    }

    /**
     * Reads an image from file and returns its matrix form represented by OpenCV
     * This method now handles Scoped Storage for API 29 and above.
     */
    fun imReadOpenCV(fileName: String, fileType: ImageFileAttribute.FileType): Mat {
        return if (fileName.toLowerCase().contains(".jpg")) {
            Log.d(TAG, "Filepath for imread: $fileName")
            Imgcodecs.imread(fileName)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for API 29+
                val contentResolver = context?.contentResolver
                val projection = arrayOf(MediaStore.Images.Media._ID)
                val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf("$fileName${ImageFileAttribute.getFileExtension(fileType)}")
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

                val cursor = contentResolver?.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )

                cursor?.use {
                    if (it.moveToFirst()) {
                        val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                        val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        val inputStream = contentResolver.openInputStream(uri)
                        if (inputStream != null) {
                            val buffer = inputStream.readBytes()
                            val matOfByte = MatOfByte(*buffer)
                            return Imgcodecs.imdecode(matOfByte, Imgcodecs.IMREAD_COLOR)
                        }
                    }
                }
                Log.e(TAG, "Failed to read image using MediaStore: $fileName")
                Mat() // Return an empty Mat if reading fails
            } else {
                // Fallback to traditional file system for API < 29
                val completeFilePath = "${FileImageWriter.getInstance()?.getFilePath()}/$fileName${ImageFileAttribute.getFileExtension(fileType)}"
                Log.d(TAG, "Filepath for imread: $completeFilePath")
                Imgcodecs.imread(completeFilePath)
            }
        }
    }

    /**
     * Loads a bitmap from file. This method now handles Scoped Storage for API 29 and above.
     */
    fun loadBitmapFromFile(fileName: String, fileType: ImageFileAttribute.FileType): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use MediaStore for API 29+
            val contentResolver = context?.contentResolver
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
            val selectionArgs = arrayOf("$fileName${ImageFileAttribute.getFileExtension(fileType)}")
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            val cursor = contentResolver?.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    return contentResolver.openInputStream(uri)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)
                    } ?: throw IOException("Failed to load bitmap from MediaStore")
                }
            }
            throw IOException("Failed to find image in MediaStore: $fileName")
        } else {
            // Fallback to traditional file system for API < 29
            val completeFilePath = "${FileImageWriter.getInstance()?.getFilePath()}/$fileName${ImageFileAttribute.getFileExtension(fileType)}"
            Log.d(TAG, "Filepath for loading bitmap: $completeFilePath")
            return BitmapFactory.decodeFile(completeFilePath)
        }
    }

    fun imReadFullPath(fullPath: String): Mat {
        Log.d(TAG, "Filepath for imread: $fullPath")
        return Imgcodecs.imread(fullPath)
    }

    fun imReadColor(fileName: String, fileType: ImageFileAttribute.FileType): Mat {
        val completeFilePath = "${FileImageWriter.getInstance()?.getFilePath()}/$fileName${ImageFileAttribute.getFileExtension(fileType)}"
        Log.d(TAG, "Filepath for imread: $completeFilePath")
        return Imgcodecs.imread(completeFilePath, Imgcodecs.IMREAD_COLOR)
    }

    fun doesImageExist(fileName: String, fileType: ImageFileAttribute.FileType): Boolean {
        val file = File("${FileImageWriter.getInstance()?.getFilePath()}/$fileName${ImageFileAttribute.getFileExtension(fileType)}")
        return file.exists()
    }


    fun loadBitmapThumbnail(fileName: String, fileType: ImageFileAttribute.FileType, width: Int, height: Int): Bitmap {
        val resized = ThumbnailUtils.extractThumbnail(loadBitmapFromFile(fileName, fileType), width, height)
        return resized
    }

    fun loadAbsoluteBitmapThumbnail(absolutePath: String, width: Int, height: Int): Bitmap {
        val resized = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(absolutePath), width, height)
        return resized
    }

    fun getDecodedFilePath(fileName: String, fileType: ImageFileAttribute.FileType): String {
        return "${FileImageWriter.getInstance()?.getFilePath()}/$fileName${ImageFileAttribute.getFileExtension(fileType)}"
    }
}