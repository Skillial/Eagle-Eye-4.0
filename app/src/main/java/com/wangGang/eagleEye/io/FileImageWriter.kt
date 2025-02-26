    package com.wangGang.eagleEye.io

    import android.app.Activity
    import android.content.Context
    import android.graphics.Bitmap
    import android.graphics.Matrix
    import android.media.MediaScannerConnection
    import android.net.Uri
    import android.os.Environment
    import android.util.Log
    import android.widget.Toast
    import com.wangGang.eagleEye.camera.CameraController
    import org.opencv.core.Mat
    import org.opencv.imgcodecs.Imgcodecs
    import java.io.File
    import java.io.FileOutputStream
    import java.io.IOException
    import java.text.SimpleDateFormat
    import java.util.Date
    import android.view.Gravity

    class FileImageWriter private constructor(private val context: Context) {

        companion object {
            private const val TAG = "SR_ImageWriter"
            private var sharedInstance: FileImageWriter? = null
            private const val ALBUM_EXTERNAL_NAME = "EagleEye Results"

            // Albums
            const val ROOT_ALBUM_NAME_PREFIX = "/EagleEye"
            const val RESULTS_ALBUM_NAME_PREFIX = "/Results"



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

            @JvmStatic
            fun recreateSubDirectory(path: String, subDir: String) {
                val dirFile = File("$path/$subDir")
                deleteRecursive(dirFile)
                if (!dirFile.mkdirs()) {
                    dirFile.mkdir()
                }
            }

            private fun deleteRecursive(fileOrDirectory: File) {
                if (fileOrDirectory.isDirectory) {
                    fileOrDirectory.listFiles()?.forEach { deleteRecursive(it) }
                }
                fileOrDirectory.delete()
            }

            interface OnImageSavedListener {
                fun onImageSaved(uri: Uri)
            }

            private var onImageSavedListener: OnImageSavedListener? = null

            fun setOnImageSavedListener(listener: OnImageSavedListener) {
                this.onImageSavedListener = listener
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
                val dirFile = File("$proposedPath/$directory")
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
        fun getPath(fileNames: Array<String>, fileType: ImageFileAttribute.FileType): Array<String> {
            var list = mutableListOf<String>()
            for (fileName in fileNames){
                val imageFile = File(proposedPath, "$fileName${ImageFileAttribute.getFileExtension(fileType)}")
                list.add(imageFile.absolutePath)
            }
            return list.toTypedArray()
        }

        @Synchronized
        fun saveBitmapToUserDir(bitmap: Bitmap, fileType: ImageFileAttribute.FileType) {
            val albumDir = getAlbumStorageDir(ALBUM_EXTERNAL_NAME)
            val timeStamp = SimpleDateFormat("yyyyMMdd'T'HHmmss").format(Date())
            val imageFileName = "IMG_$timeStamp"
            val imageFile = File(
                albumDir.path,
                "$imageFileName${ImageFileAttribute.getFileExtension(fileType)}"
            )

            try {
                FileOutputStream(imageFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                }
                Log.d(TAG, "Saved: ${imageFile.absolutePath}")
            } catch (e: IOException) {
                e.printStackTrace()
            }

            refreshImageGallery(imageFile)
        }

        @Synchronized
        fun saveMatrixToResultsDir(mat: Mat, fileType: ImageFileAttribute.FileType, resultType: ResultType): Uri? {
            val imageFileName = resultType.toString()
            val imageFile = File("$proposedPath/${DirectoryStorage.RESULT_ALBUM_NAME_PREFIX}", "$imageFileName${ImageFileAttribute.getFileExtension(fileType)}")
            Imgcodecs.imwrite(imageFile.absolutePath, mat)

            Log.d(TAG, "saveMatToResultsDir")
            Log.d(TAG, "Saved: ${imageFile.absolutePath}")

            refreshImageGallery(imageFile)

            return Uri.fromFile(imageFile)
        }

        @Synchronized
        fun saveBitmapToResultsDir(bitmap: Bitmap, fileType: ImageFileAttribute.FileType, resultType: ResultType): Uri? {
            val imageFileName = resultType.toString()
            val imageFile = File("$proposedPath/${DirectoryStorage.RESULT_ALBUM_NAME_PREFIX}", "$imageFileName${ImageFileAttribute.getFileExtension(fileType)}")

            val rotatedBitmap = ImageUtils.rotateBitmap(bitmap, 90f)

            try {
                FileOutputStream(imageFile).use { out ->
                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                }
                Log.d(TAG, "Saved: ${imageFile.absolutePath}")
            } catch (e: IOException) {
                e.printStackTrace()
            }

            refreshImageGallery(imageFile)

            return Uri.fromFile(imageFile)
        }

        @Synchronized
        fun saveMatToUserDir(mat: Mat, fileType: ImageFileAttribute.FileType) {
            val albumDir = getAlbumStorageDir(ALBUM_EXTERNAL_NAME)
            val timeStamp = SimpleDateFormat("yyyyMMdd'T'HHmmss").format(Date())
            val imageFileName = "IMG_$timeStamp"
            val imageFile = File(albumDir.path, "$imageFileName${ImageFileAttribute.getFileExtension(fileType)}")
            Imgcodecs.imwrite(imageFile.absolutePath, mat)

            Log.d(TAG, "saveMatToUserDir")
            Log.d(TAG, "Saved: ${imageFile.absolutePath}")

            refreshImageGallery(imageFile)
        }

        @Synchronized
        fun saveMatrixToImage(mat: Mat, fileName: String, fileType: ImageFileAttribute.FileType) {
            val imageFile = File(proposedPath, "$fileName${ImageFileAttribute.getFileExtension(fileType)}")
            Imgcodecs.imwrite(imageFile.absolutePath, mat)
            Log.d(TAG, "saveMatrixToImage")
            Log.d(TAG, "Saved ${imageFile.absolutePath}")

            refreshImageGallery(imageFile)
        }

        @Synchronized
        fun saveMatrixToImageReturnPath(mat: Mat, fileName: String, fileType: ImageFileAttribute.FileType): String {
            val imageFile = File(proposedPath, "$fileName${ImageFileAttribute.getFileExtension(fileType)}")
            Imgcodecs.imwrite(imageFile.absolutePath, mat)
            Log.d(TAG, "saveMatrixToImage")
            Log.d(TAG, "Saved ${imageFile.absolutePath}")

            if (fileName.equals("result")) {
                refreshImageGallery(imageFile)
            }
            return imageFile.absolutePath
        }

        @Synchronized
        fun debugSaveMatrixToImage(mat: Mat, fileName: String, fileType: ImageFileAttribute.FileType) {
            val imageFile = File(proposedPath, "$fileName${ImageFileAttribute.getFileExtension(fileType)}")
            Imgcodecs.imwrite(imageFile.absolutePath, mat)
            Log.d(TAG, "debugSaveMatrixToImage")
            Log.d(TAG, "Saved ${imageFile.absolutePath}")
        }

        @Synchronized
        fun debugSaveMatrixToImage(mat: Mat, directory: String, fileName: String, fileType: ImageFileAttribute.FileType) {
            val imageFile = File("$proposedPath/$directory", "$fileName${ImageFileAttribute.getFileExtension(fileType)}")
            Imgcodecs.imwrite(imageFile.absolutePath, mat)
            Log.d(TAG, "debugSaveMatrixToImage")
            Log.d(TAG, "Saved ${imageFile.absolutePath}")
        }

        @Synchronized
        fun saveMatrixToImage(mat: Mat, directory: String, fileName: String, fileType: ImageFileAttribute.FileType) {
            val dirFile = File("$proposedPath/$directory")
            if (!dirFile.mkdirs()) {
                dirFile.mkdir()
            }
            val imageFile = File(dirFile.path, "$fileName${ImageFileAttribute.getFileExtension(fileType)}")
            Imgcodecs.imwrite(imageFile.absolutePath, mat)
        }

        @Synchronized
        fun getSharedResultPath(fileType: ImageFileAttribute.FileType): String {
            val imageFileName = ResultType.AFTER.toString()
            val imageFile = File("$proposedPath/${DirectoryStorage.RESULT_ALBUM_NAME_PREFIX}", "$imageFileName${ImageFileAttribute.getFileExtension(fileType)}")
            return imageFile.absolutePath
        }

        @Synchronized
        fun getHRResultPath(fileType: ImageFileAttribute.FileType): String {
            val albumDir = getAlbumStorageDir(ALBUM_EXTERNAL_NAME)
            val timeStamp = SimpleDateFormat("yyyyMMdd'T'HHmmss").format(Date())
            val imageFileName = "EagleEyeHD_$timeStamp"
            val imageFile = File(albumDir.path, "$imageFileName${ImageFileAttribute.getFileExtension(fileType)}")
            return imageFile.absolutePath
        }

        @Synchronized
        fun saveHRResultToUserDir(mat: Mat, fileType: ImageFileAttribute.FileType) {
            val albumDir = getAlbumStorageDir(ALBUM_EXTERNAL_NAME)
            val timeStamp = SimpleDateFormat("yyyyMMdd'T'HHmmss").format(Date())
            val imageFileName = "EagleEyeHD_$timeStamp"
            val imageFile = File(albumDir.path, "$imageFileName${ImageFileAttribute.getFileExtension(fileType)}")
            Imgcodecs.imwrite(imageFile.absolutePath, mat)

            Log.d("FileImageWriter", "Saved HR image: ${imageFile.absolutePath}")

            val message = "Super HD image saved at: ${imageFile.path}"

            (context as Activity).runOnUiThread {
                val toast = Toast.makeText(context, message, Toast.LENGTH_LONG)
                toast.setGravity(Gravity.TOP, 0, 300)
                toast.show()
            }

            refreshImageGallery(imageFile)

            Log.d("FileImageWriter", "Saved thumbnail: ${imageFile.absolutePath}")

        }

        fun refreshThumbnailFolder() {
            val afterUri = FileImageReader.getInstance()!!.getAfterUriDefaultResultsFolder()
            // Extract the proper file path from the URI
            val afterFile = afterUri.path?.let { File(it) }
            if (afterFile != null) {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(afterFile.absolutePath), null
                ) { path, uri ->
                    Log.i("refreshedThumbnailFolder", "Scanned $path")
                    Log.i("refreshedThumbnailFolder", "-> uri=$uri")
                    onImageSavedListener?.onImageSaved(uri)
                }
            }
        }


        private fun refreshImageGallery(imageFile: File) {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(imageFile.toString()), null
            ) { path, uri ->
                Log.i("ExternalStorage", "Scanned $path")
                Log.i("ExternalStorage", "-> uri=$uri")

                if (imageFile.name.contains(ResultType.AFTER.toString())) {
                    Log.d(TAG, "onImageSavedListener + ${ResultType.AFTER}")
                    Log.d(TAG, "onImageSavedListener File: ${imageFile.absolutePath}")
                    onImageSavedListener?.onImageSaved(uri)
                }
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

        fun getMergedFilePath(count: Int): String {
            return context.getExternalFilesDir(null)!!.absolutePath + "/merged_" + count + ".jpg"
        }
        @Synchronized
        fun deleteImage(fileName: String, fileType: ImageFileAttribute.FileType) {
            val imageFile = File(proposedPath, "$fileName${ImageFileAttribute.getFileExtension(fileType)}")
            imageFile.delete()
        }

        @Synchronized
        fun deleteImage(fileName: String, directory: String, fileType: ImageFileAttribute.FileType) {
            val dirFile = File("$proposedPath/$directory")
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

                // Check if the file exists and is not empty
                if (file.exists() && file.length() > 0) {
                    Log.d("SaveImage", "Image saved successfully: ${file.absolutePath}")
                    true
                } else {
                    Log.d("SaveImage", "Failed to save image: File is empty or does not exist")
                    false
                }

                return file.absolutePath // Return the absolute path
            } catch (e: IOException) {
                e.printStackTrace()
                Log.d("SaveImage", "Failed to save image: ${e.message}")
                return ""
            }
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
