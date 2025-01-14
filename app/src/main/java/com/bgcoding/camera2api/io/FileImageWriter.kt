    package com.bgcoding.camera2api.io

    import android.app.Activity
    import android.content.Context
    import android.graphics.Bitmap
    import android.media.MediaScannerConnection
    import android.net.Uri
    import android.os.Environment
    import android.util.Log
    import android.widget.Toast
    import org.opencv.core.CvType
    import org.opencv.core.Mat
    import org.opencv.imgcodecs.Imgcodecs
    import java.io.File
    import java.io.FileOutputStream
    import java.io.IOException
    import java.text.SimpleDateFormat
    import java.util.*

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
            val imageFile = File(proposedPath, "$fileName${ImageFileAttribute.getFileExtension(fileType)}")
            Imgcodecs.imwrite(imageFile.absolutePath, mat)
            Log.d(TAG, "Saved ${imageFile.absolutePath}")
        }



        @Synchronized
        fun debugSaveMatrixToImage(mat: Mat, fileName: String, fileType: ImageFileAttribute.FileType) {
            val imageFile = File(proposedPath, "$fileName${ImageFileAttribute.getFileExtension(fileType)}")
            Imgcodecs.imwrite(imageFile.absolutePath, mat)
            Log.d(TAG, "Saved ${imageFile.absolutePath}")
        }

        @Synchronized
        fun saveMatrixToImage(mat: Mat, directory: String, fileName: String, fileType: ImageFileAttribute.FileType) {
            val dirFile = File(proposedPath + "/$directory")
            if (!dirFile.mkdirs()) {
                dirFile.mkdir()
            }
            val imageFile = File(dirFile.path, "$fileName${ImageFileAttribute.getFileExtension(fileType)}")
            Imgcodecs.imwrite(imageFile.absolutePath, mat)
        }

        @Synchronized
        fun saveHRResultToUserDir(mat: Mat, fileType: ImageFileAttribute.FileType) {
            val albumDir = getAlbumStorageDir(ALBUM_EXTERNAL_NAME)
            val timeStamp = SimpleDateFormat("yyyyMMdd'T'HHmmss").format(Date())
            val imageFileName = "EagleEyeHD_$timeStamp"
            val imageFile = File(albumDir.path, "$imageFileName${ImageFileAttribute.getFileExtension(fileType)}")
            Imgcodecs.imwrite(imageFile.absolutePath, mat)

            val message = "Super HD image saved at: ${imageFile.path}"
            (context as Activity).runOnUiThread {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }

            refreshImageGallery(imageFile)
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
    }
