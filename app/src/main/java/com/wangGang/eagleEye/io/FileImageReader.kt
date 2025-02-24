package com.wangGang.eagleEye.io

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.util.Log
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import java.io.File
import java.io.FileInputStream
import java.io.IOException

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

    private var beforeUri: Uri? = null
    private var afterUri: Uri? = null

    fun setBeforeUri(uri: Uri) {
        Log.d("FileImageReader", "Setting before URI: $uri")
        beforeUri = uri
    }

    fun setAfterUri(uri: Uri) {
        Log.d("FileImageReader", "Setting after URI: $uri")
        afterUri = uri
    }

    fun getBeforeUri(): Uri? {
        Log.d("FileImageReader", "Getting before URI: $beforeUri")
        return beforeUri
    }

    fun getAfterUri(): Uri? {
        Log.d("FileImageReader", "Getting after URI: $afterUri")
        return afterUri
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
     * Reads an image from file and returns its matrix form represented by openCV
     * @param fileName
     * @return
     */
    fun imReadOpenCV(fileName: String, fileType: ImageFileAttribute.FileType): Mat {
        return if (fileName.toLowerCase().contains(".jpg")) {
            Log.d(TAG, "Filepath for imread: $fileName")
            Imgcodecs.imread(fileName)
        } else {
            val completeFilePath = "${FileImageWriter.getInstance()?.getFilePath()}/$fileName${ImageFileAttribute.getFileExtension(fileType)}"
            Log.d(TAG, "Filepath for imread: $completeFilePath")
            Imgcodecs.imread(completeFilePath)
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

    fun getBeforeAndAfterImages(fileType: ImageFileAttribute.FileType): Pair<Bitmap, Bitmap> {
        val before = loadBitmapFromDirectory(DirectoryStorage.getSharedInstance().getResultFilePath(), ResultType.BEFORE.toString(), fileType)
        val after = loadBitmapFromDirectory(DirectoryStorage.getSharedInstance().getResultFilePath(), ResultType.AFTER.toString(), fileType)
        return Pair(before, after)
    }

    private fun loadBitmapFromFile(fileName: String, fileType: ImageFileAttribute.FileType): Bitmap {
        val completeFilePath = "${FileImageWriter.getInstance()?.getFilePath()}/$fileName${ImageFileAttribute.getFileExtension(fileType)}"
        Log.d(TAG, "Filepath for loading bitmap: $completeFilePath")
        return BitmapFactory.decodeFile(completeFilePath)
    }

    private fun loadBitmapFromDirectory(directory: String, fileName: String, fileType: ImageFileAttribute.FileType): Bitmap {
        val completeFilePath = "$directory/$fileName${ImageFileAttribute.getFileExtension(fileType)}"
        Log.d(TAG, "Filepath for loading bitmap: $completeFilePath")
        return BitmapFactory.decodeFile(completeFilePath)
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
