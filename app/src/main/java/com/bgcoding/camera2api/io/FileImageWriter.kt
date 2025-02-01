package com.bgcoding.camera2api.io

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.bgcoding.camera2api.camera.CameraController
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.imgcodecs.Imgcodecs
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FileImageWriter private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SR_ImageWriter"
        private var sharedInstance: FileImageWriter? = null
        const val ALBUM_NAME_PREFIX = "/SR"
        private const val ALBUM_EXTERNAL_NAME = "EagleEye Results"

        fun getInstance(): FileImageWriter? {
            return sharedInstance
        }

        fun initialize(context: Context) {
            if (sharedInstance == null) {
                sharedInstance = FileImageWriter(context)
                FileImageReader.initialize(context)
            }
        }

        fun destroy() {
            sharedInstance = null
            FileImageReader.destroy()
        }

        @JvmStatic
        fun recreateDirectory(path: String) {
            val dirFile = File(path)
            deleteRecursive(dirFile)
            if (!dirFile.mkdirs()) {
                dirFile.mkdir()
            }
        }

        internal fun deleteRecursive(fileOrDirectory: File) {
            if (fileOrDirectory.isDirectory) {
                fileOrDirectory.listFiles()?.forEach { deleteRecursive(it) }
            }
            fileOrDirectory.delete()
        }
    }

    private val proposedPath = DirectoryStorage.getSharedInstance().proposedPath

    fun saveImage(imageData: ByteArray?, fileName: String, fileType: ImageFileAttribute.FileType) {
        try {
            if (imageData != null) {
                val processedImageFile = File(proposedPath, "$fileName${ImageFileAttribute.getFileExtension(fileType)}")
                FileOutputStream(processedImageFile).use { fos ->
                    fos.write(imageData)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error writing image: ${e.message}")
        }
    }

    fun saveBitmapImage(bitmap: Bitmap, fileName: String, fileType: ImageFileAttribute.FileType) {
        try {
            val processedImageFile = File(proposedPath, "$fileName${ImageFileAttribute.getFileExtension(fileType)}")
            FileOutputStream(processedImageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            Log.d(TAG, "Saved: ${processedImageFile.absolutePath}")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun saveBitmapImage(bitmap: Bitmap, directory: String, fileName: String, fileType: ImageFileAttribute.FileType) {
        try {
            val dirFile = File(proposedPath + "/$directory")
            dirFile.mkdirs()
            val processedImageFile = File(dirFile.path, "$fileName${ImageFileAttribute.getFileExtension(fileType)}")
            FileOutputStream(processedImageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            Log.d(TAG, "Saved: ${processedImageFile.absolutePath}")
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun saveMatrixToImage(mat: Mat, fileName: String, fileType: ImageFileAttribute.FileType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use MediaStore for API 29+
            val contentResolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg") // Adjust MIME type as needed
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.Images.Media.IS_PENDING, 1) // Mark as pending
            }

            val uri: Uri? = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                try {
                    val outputStream = contentResolver.openOutputStream(uri)
                    if (outputStream != null) {
                        val buffer = MatOfByte()
                        Imgcodecs.imencode(ImageFileAttribute.getFileExtension(fileType), mat, buffer)
                        outputStream.write(buffer.toArray())
                        outputStream.close()
                    }
                    // Mark as complete
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                    Log.d(TAG, "Saved using MediaStore: $fileName")
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to save image: ${e.message}")
                }
            }
        } else {
            // Use traditional file system for API < 29
            val imageFile = File(proposedPath, "$fileName${ImageFileAttribute.getFileExtension(fileType)}")
            Imgcodecs.imwrite(imageFile.absolutePath, mat)
            Log.d(TAG, "Saved ${imageFile.absolutePath}")
        }
    }

    @Synchronized
    fun debugSaveMatrixToImage(mat: Mat, fileName: String, fileType: ImageFileAttribute.FileType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use MediaStore for API 29+
            val contentResolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg") // Adjust MIME type as needed
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SR0")
                put(MediaStore.Images.Media.IS_PENDING, 1) // Mark as pending
            }

            val uri: Uri? = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                try {
                    val outputStream = contentResolver.openOutputStream(uri)
                    if (outputStream != null) {
                        val buffer = MatOfByte()
                        Imgcodecs.imencode(ImageFileAttribute.getFileExtension(fileType), mat, buffer)
                        outputStream.write(buffer.toArray())
                        outputStream.close()
                    }
                    // Mark as complete
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                    Log.d(TAG, "Debug saved using MediaStore: $fileName")
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to save debug image: ${e.message}")
                }
            }
        } else {
            // Use traditional file system for API < 29
            val imageFile = File(proposedPath, "$fileName${ImageFileAttribute.getFileExtension(fileType)}")
            Imgcodecs.imwrite(imageFile.absolutePath, mat)
            Log.d(TAG, "Debug saved ${imageFile.absolutePath}")
        }
    }

    @Synchronized
    fun saveMatrixToImage(mat: Mat, directory: String, fileName: String, fileType: ImageFileAttribute.FileType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use MediaStore for API 29+
            val contentResolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg") // Adjust MIME type as needed
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SR0/$directory")
                put(MediaStore.Images.Media.IS_PENDING, 1) // Mark as pending
            }

            val uri: Uri? = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                try {
                    val outputStream = contentResolver.openOutputStream(uri)
                    if (outputStream != null) {
                        val buffer = MatOfByte()
                        Imgcodecs.imencode(ImageFileAttribute.getFileExtension(fileType), mat, buffer)
                        outputStream.write(buffer.toArray())
                        outputStream.close()
                    }
                    // Mark as complete
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                    Log.d(TAG, "Saved using MediaStore: $fileName in $directory")
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to save image: ${e.message}")
                }
            }
        } else {
            // Use traditional file system for API < 29
            val dirFile = File(proposedPath + "/$directory")
            if (!dirFile.mkdirs()) {
                dirFile.mkdir()
            }
            val imageFile = File(dirFile.path, "$fileName${ImageFileAttribute.getFileExtension(fileType)}")
            Imgcodecs.imwrite(imageFile.absolutePath, mat)
            Log.d(TAG, "Saved ${imageFile.absolutePath}")
        }
    }

    @Synchronized
    fun saveHRResultToUserDir(mat: Mat, fileType: ImageFileAttribute.FileType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentResolver = context.contentResolver
            val values = ContentValues().apply {
                val timeStamp = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.getDefault()).format(Date())
                put(MediaStore.Images.Media.DISPLAY_NAME, "EagleEyeHD_$timeStamp${ImageFileAttribute.getFileExtension(fileType)}")
                put(MediaStore.Images.Media.MIME_TYPE, (ImageFileAttribute.getFileExtension(fileType)))
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/EagleEyeHD")
                put(MediaStore.Images.Media.IS_PENDING, 1) // Mark the file as pending
            }

            val uri: Uri? = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                try {
                    val fileDescriptor = contentResolver.openFileDescriptor(uri, "w")
                    if (fileDescriptor != null) {
                        val fos = FileOutputStream(fileDescriptor.fileDescriptor)
                        val buffer = MatOfByte()
                        Imgcodecs.imencode(ImageFileAttribute.getFileExtension(fileType), mat, buffer)
                        fos.write(buffer.toArray())
                        fos.close()
                    }
                    // Mark as complete
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                } catch (e: IOException) {
                    e.printStackTrace()
                    Log.d("SaveHRResult", "Failed to save image: ${e.message}")
                }
            }
        } else {
            val albumDir = getAlbumStorageDir(ALBUM_EXTERNAL_NAME)
            val timeStamp = SimpleDateFormat("yyyyMMdd'T'HHmmss").format(Date())
            val imageFileName = "EagleEyeHD_$timeStamp"
            val imageFile = File(albumDir.path, "$imageFileName${ImageFileAttribute.getFileExtension(fileType)}")
            Imgcodecs.imwrite(imageFile.absolutePath, mat)

            val message = "Super HD image saved at: ${imageFile.path}"
//            (context as Activity).runOnUiThread {
//                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
//            }

            refreshImageGallery(imageFile)
        }
    }

    private fun refreshImageGallery(srFile: File) {
        MediaScannerConnection.scanFile(
            context,
            arrayOf(srFile.toString()), null
        ) { path, uri ->
            Log.i("ExternalStorage", "Scanned $path")
            Log.i("ExternalStorage", "-> uri=$uri")
        }
    }

    private fun getAlbumStorageDir(albumName: String): File {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), albumName)
        if (!file.mkdirs()) {
            Log.d(TAG, "Directory not created")
        }
        return file
    }

    @Synchronized
    fun deleteImage(fileName: String, fileType: ImageFileAttribute.FileType) {
        val imageFile = File(proposedPath, "$fileName${ImageFileAttribute.getFileExtension(fileType)}")
        imageFile.delete()
    }

    @Synchronized
    fun deleteImage(fileName: String, directory: String, fileType: ImageFileAttribute.FileType) {
        val dirFile = File(proposedPath + "/$directory")
        val imageFile = File(dirFile.path, "$fileName${ImageFileAttribute.getFileExtension(fileType)}")
        imageFile.delete()
    }

    @Synchronized
    fun deleteWorkspace() {
        val dirFile = File(proposedPath)
        deleteRecursive(dirFile)
    }

    fun getFilePath(): String? {
        return proposedPath
    }

    fun deleteRecursive(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory) {
            fileOrDirectory.listFiles()?.forEach { deleteRecursive(it) }
        }
        fileOrDirectory.delete()
    }

    // New Methods
    fun saveImageToStorage(bitmap: Bitmap): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentResolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "image_${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MyApp/Images")
                put(MediaStore.Images.Media.IS_PENDING, 1) // Mark the file as pending to avoid incomplete scans
            }

            val uri: Uri? = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                try {
                    val outputStream = contentResolver.openOutputStream(uri)
                    if (outputStream != null) {
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                        outputStream.close()
                    }
                    // Mark as complete
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                    return uri.toString()
                } catch (e: IOException) {
                    e.printStackTrace()
                    Log.d("SaveImage", "Failed to save image: ${e.message}")
                }
            }

        } else {
            val helper = Helper()
            val adjustedBitmap = helper.adjustImageOrientation(bitmap)

            val folder = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "MyApp/Images")
            if (!folder.exists()) {
                folder.mkdirs() // Create directory if it doesn't exist
            }

            val fileName = "image_${System.currentTimeMillis()}.jpg"
            val file = File(folder, fileName)

            try {
                val fos = FileOutputStream(file)
                adjustedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
                fos.close()
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                return file.absolutePath // Return the absolute path
            } catch (e: IOException) {
                e.printStackTrace()
                Log.d("SaveImage", "Failed to save image: ${e.message}")
                return ""
            }
        }
        return ""
    }

    inner class Helper {
        fun adjustImageOrientation(image: Bitmap): Bitmap {
            val sensorOrientation = CameraController.getInstance().getSensorOrientation()
            val matrix = Matrix()

            when (sensorOrientation) {
                90 -> matrix.postRotate(90f)
                180 -> matrix.postRotate(180f)
                270 -> matrix.postRotate(270f)
            }

            return Bitmap.createBitmap(image, 0, 0, image.width, image.height, matrix, true)
        }
    }
}