package com.wangGang.eagleEye.io

import android.os.Environment
import android.util.Log
import java.io.File

class DirectoryStorage private constructor() {

    companion object {
        private const val TAG = "DirectoryStorage"
        const val ROOT_ALBUM_NAME_PREFIX = "/EagleEye"
        const val DEBUG_FILE_PREFIX = "/DEBUG"
        const val RESULT_ALBUM_NAME_PREFIX = "/Results"
        const val SR_ALBUM_NAME_PREFIX = "/SR"

        private val SUB_ALBUM_NAME_PREFIX: List<String> = listOf(
            RESULT_ALBUM_NAME_PREFIX,
            SR_ALBUM_NAME_PREFIX
        )

        private var sharedInstance: DirectoryStorage? = null

        @JvmStatic
        fun getSharedInstance(): DirectoryStorage {
            if (sharedInstance == null) {
                sharedInstance = DirectoryStorage()
            }
            return sharedInstance!!
        }
    }

    private var startingAlbum = 0
    var proposedPath: String? = null
    private fun isAlbumDirExisting(albumNumber: Int): Boolean {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + FileImageWriter.ROOT_ALBUM_NAME_PREFIX + albumNumber)
        return file.isDirectory && file.exists()
    }

    private fun identifyDir() {
        refreshProposedPath()
        /*if (isAlbumDirExisting(startingAlbum)) {
            FileImageWriter.recreateDirectory(proposedPath!!)
        }*/

        // only recreate subdirectories except for "/Results"
        for (subAlbum in SUB_ALBUM_NAME_PREFIX) {
            val subDir = File(proposedPath + subAlbum)
            if (subDir.exists()) {

                if (subAlbum == RESULT_ALBUM_NAME_PREFIX) {
                    continue
                }
                Log.d(TAG, "recreating subdirectory: ${subDir.absolutePath}")
                FileImageWriter.recreateDirectory(subDir.absolutePath)
            }
        }
    }

    fun createDirectory() {
        identifyDir()
        createRootDirectory()
        createSubDirectories()
    }

    private fun createRootDirectory() {
        val filePath = File(proposedPath)
        filePath.mkdirs()
        Log.i(TAG, "Image storage is set to: $proposedPath")
    }

    private fun createSubDirectories() {
        for (subAlbum in SUB_ALBUM_NAME_PREFIX) {
            val filePath = File(proposedPath + subAlbum)
            filePath.mkdirs()
            Log.i(TAG, "Sub directory created: ${filePath.absolutePath}")
        }
    }

    private fun refreshProposedPath() {
        proposedPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + ROOT_ALBUM_NAME_PREFIX + startingAlbum
    }

    fun getResultFilePath(): String {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + ROOT_ALBUM_NAME_PREFIX + startingAlbum + RESULT_ALBUM_NAME_PREFIX
    }

    fun getDebugFilePath(): String {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + DEBUG_FILE_PREFIX
    }
}