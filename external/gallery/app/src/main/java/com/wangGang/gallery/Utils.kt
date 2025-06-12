package com.wangGang.gallery

import android.annotation.SuppressLint
import android.app.RecoverableSecurityException
import android.content.*
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns.*
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import java.io.File
import java.util.*
import android.os.Bundle

val TAG = "GalleryUtils"

val imageExtensions = arrayOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
val videoExtensions = arrayOf("mp4", "mkv", "avi", "wmv", "mov", "flv", "webm", "ogg", "ogv")
val fileExtensions = imageExtensions.plus(videoExtensions)
var selectable = false

var albums: HashMap<File, List<File>>? = null
var itemsList = mutableListOf<Photo>()

fun sortImagesByFolder(files: List<File>): Map<File, List<File>> {
    val resultMap = mutableMapOf<File, MutableList<File>>()
    for (file in files) {
        if(file.totalSpace != 0L){
            (!resultMap.containsKey(file.parentFile!!)).let { resultMap.put(file.parentFile!!, mutableListOf()) }
            resultMap[file.parentFile!!]?.add(file)
        }
    }
    return resultMap
}

fun getImagesFromAlbum(folder: String): List<Photo> {
    return File(folder)
        .listFiles { file -> file.isFile && fileExtensions.contains(file.extension.lowercase()) }
        ?.sortedWith(compareByDescending { it.lastModified() })
        ?.map { file -> Photo(path = file.absolutePath, position = 0, selected = false) }
        ?: emptyList()
}

fun getAllImages(context: Context): List<File> {
    val sortOrder = MediaStore.Images.Media.DATE_TAKEN + " ASC"
    val sortOrderVideos = MediaStore.Video.Media.DATE_TAKEN + " ASC"

    val imageList = queryUri(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null, sortOrder)
        .use { it?.getResultsFromCursor() ?: listOf() }
    val videoList = queryUri(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null, null, sortOrderVideos)
        .use { it?.getResultsFromCursor() ?: listOf() }
    return videoList + imageList
}

/*fun getAllImagesAndVideosSortedByRecent(context: Context): List<Photo> {
    Log.d(TAG, "getAllImagesAndVideosSortedByRecent() called")

    val sortOrder = MediaStore.Images.Media.DATE_TAKEN + " DESC"
    val sortOrderVideos = MediaStore.Video.Media.DATE_TAKEN + " DESC"

    val imageList = queryUri(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null, sortOrder)
        .use { it?.getResultsFromCursor() ?: listOf() }
    val videoList = queryUri(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null, null, sortOrderVideos)
        .use { it?.getResultsFromCursor() ?: listOf() }

    Log.d(TAG, "Images before filter: ${imageList.size}")
    Log.d(TAG, "Videos before filter: ${videoList.size}")

    val filtered = (imageList + videoList)
        .filterNot { file ->
            val path = file.absolutePath
            path.contains("/Pictures/EagleEye0/") ||
                    path.contains("/Pictures/SR0/")
        }
        .sortedByDescending { it.lastModified() }

    return filtered.map { file ->
        Log.d(TAG, "Found media: ${file.absolutePath}")
        Photo(path = file.absolutePath, position = 0, selected = false)
    }

    *//*val resultList = (imageList + videoList).sortedWith(compareByDescending { it.lastModified() })
    return resultList.map { file -> Photo(path = file.absolutePath, position = 0, selected = false) }*//*
}*/

fun getAllImagesAndVideosSortedByRecent(context: Context): List<Photo> {
    Log.d(TAG, "▶ getAllImagesAndVideosSortedByRecent() called")

    val sortOrderImages = "${MediaStore.Images.Media.DATE_TAKEN} DESC"
    val sortOrderVideos = "${MediaStore.Video.Media.DATE_TAKEN} DESC"

    val imageList = queryUri(
        context,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        null, null,
        sortOrderImages
    ).use { it?.getResultsFromCursor() ?: emptyList() }

    val videoList = queryUri(
        context,
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        null, null,
        sortOrderVideos
    ).use { it?.getResultsFromCursor() ?: emptyList() }

    Log.d(TAG, "Images before filter: ${imageList.size}")
    Log.d(TAG, "Videos before filter: ${videoList.size}")

    // Re-enable the filterNot to drop anything under EagleEye0 or SR0
    val picturesRoot = Environment
        .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        .absolutePath

    val filtered = (imageList + videoList)
        .filterNot { file ->
            val path = file.absolutePath
            // if it lives in /Pictures/EagleEye0 or /Pictures/SR0, drop it
            path.startsWith("$picturesRoot/EagleEye0") ||
                    path.startsWith("$picturesRoot/SR0")
        }
        .sortedByDescending { it.lastModified() }

    Log.d(TAG, "Total after filter: ${filtered.size}")
    filtered.forEach { file ->
        Log.d(TAG, "  ✔️ keep: ${file.absolutePath}")
    }

    return filtered.map { file ->
        Photo(path = file.absolutePath, position = 0, selected = false)
    }
}


fun getImagesFromPage(page: Int, data: List<Photo>): List<Photo> {
    val startIndex = (page - 1) * 100
    val endIndex = startIndex + 100

    if (startIndex >= data.size) {
        return emptyList()
    }

    val end = if (endIndex > data.size) data.size else endIndex

    return data.subList(startIndex, end)
}

private fun queryUri(context: Context, uri: Uri, selection: String?, selectionArgs: Array<String>?, sortOrder: String = ""): Cursor? {
    return context.contentResolver.query(
        uri,
        projection,
        selection,
        selectionArgs,
        sortOrder)
}

fun getLatestImageUri(context: Context): Uri? {
    val imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    val projection = arrayOf(
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DATE_MODIFIED,
        MediaStore.MediaColumns.MIME_TYPE
    )

    var latestMediaUri: Uri? = null
    var latestModifiedTime: Long = 0L

    // Define common query arguments for API 30+
    val queryArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Bundle().apply {
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.MediaColumns.DATE_MODIFIED))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, 1) // Correct way to limit
        }
    } else {
        null // For older APIs, we don't use Bundle queryArgs
    }

    // Define sortOrder for older APIs (no LIMIT in string)
    val sortOrderForOldApi = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

    // --- Query images ---
    val imageCursor: Cursor? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.contentResolver.query(imageUri, projection, queryArgs, null)
    } else {
        context.contentResolver.query(imageUri, projection, null, null, sortOrderForOldApi)
    }

    imageCursor?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val mediaId = cursor.getLong(idColumn)
            val modifiedTime = cursor.getLong(dateModifiedColumn) * 1000L // Convert to milliseconds

            latestModifiedTime = modifiedTime
            latestMediaUri = Uri.withAppendedPath(imageUri, mediaId.toString())
        }
    }

    // --- Query videos ---
    val videoCursor: Cursor? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.contentResolver.query(videoUri, projection, queryArgs, null)
    } else {
        context.contentResolver.query(videoUri, projection, null, null, sortOrderForOldApi)
    }

    videoCursor?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val mediaId = cursor.getLong(idColumn)
            val modifiedTime = cursor.getLong(dateModifiedColumn) * 1000L // Convert to milliseconds

            // Compare with the latest found so far (could be an image or a previous video)
            if (modifiedTime > latestModifiedTime) { // This comparison is crucial if you want the *absolute* latest
                latestModifiedTime = modifiedTime
                latestMediaUri = Uri.withAppendedPath(videoUri, mediaId.toString())
            }
        }
    }

    if (latestMediaUri != null) {
        Log.d(TAG, "Found latest media URI: $latestMediaUri (Modified: ${Date(latestModifiedTime)})")
    } else {
        Log.d(TAG, "No media found in gallery.")
    }

    return latestMediaUri
}

private fun Cursor.getResultsFromCursor(): List<File> {
    val results = mutableListOf<File>()

    while (this.moveToNext()) {
        results.add(File(this.getString(this.getColumnIndexOrThrow(DATA))))
    }
    return results
}

fun getImageVideoNumber(parent : File) : Int{
    var imageCount = 0
    var videoCount = 0

    for (file in parent.listFiles()!!) {
        if (file.isFile) {
            val fileExtension = file.extension.lowercase()
            if (imageExtensions.contains(fileExtension)) {
                imageCount++
            } else if (videoExtensions.contains(fileExtension)) {
                videoCount++
            }
        }
    }
    return imageCount + videoCount
}

@SuppressLint("Range")
fun getContentUri(context: Context, file: File): Uri? {
    val filePath = file.absolutePath
    val mimeType: String
    val contentUri: Uri
    val contentValues = ContentValues()

    if(file.extension.lowercase() in imageExtensions) {
        mimeType = "image/*"
        contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
        contentValues.put(MediaStore.Images.Media.MIME_TYPE, mimeType)
    }else{
        mimeType = "video/*"
        contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        contentValues.put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
        contentValues.put(MediaStore.Video.Media.MIME_TYPE, mimeType)
    }

    val cursor: Cursor = context.contentResolver.query(
        contentUri, arrayOf(_ID),
        "$DATA =? ", arrayOf(filePath), null
    )!!

    return if (cursor.moveToFirst()) {
        val id: Int = cursor.getInt(cursor.getColumnIndex(_ID))
        cursor.close()
        Uri.withAppendedPath(contentUri, "" + id)
    } else {
        if (file.exists()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver: ContentResolver = context.contentResolver
                val contentCollection =
                    if (mimeType.startsWith("image/")) MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                    else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/" + UUID.randomUUID().toString())
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 1)
                val finalUri = resolver.insert(contentCollection, contentValues)
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(contentCollection, contentValues, null, null)
                finalUri
            } else {
                contentValues.put(DATA, filePath)
                context.contentResolver.insert(contentUri, contentValues)
            }
        } else {
            null
        }
    }
}

fun deletePhotoFromExternal(context: Context, photoUri: Uri, intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>): Boolean{
    try {
        context.contentResolver.delete(photoUri, null, null)
        return true
    } catch (e: SecurityException) {
        val intentSender = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                MediaStore.createDeleteRequest(context.contentResolver, listOf(photoUri))
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                val recoverableSecurityException = e as? RecoverableSecurityException
                recoverableSecurityException?.userAction?.actionIntent?.intentSender
            }
            else -> null
        }
        intentSender?.let { sender ->
            intentSenderLauncher.launch(
                IntentSenderRequest.Builder(sender as IntentSender).build()
            )
            return true
        }
    }
    return false
}

val projection = arrayOf(
    MediaStore.Files.FileColumns._ID,
    MediaStore.Files.FileColumns.DATA,
    MediaStore.Files.FileColumns.DATE_ADDED,
    MediaStore.Files.FileColumns.MIME_TYPE,
    MediaStore.Files.FileColumns.TITLE
)