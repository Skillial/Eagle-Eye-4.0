package com.wangGang.eagleEye.io

import android.os.Environment
import android.util.Log
import java.io.File

class DirectoryStorage private constructor() {

    companion object {
        private const val TAG = "DirectoryStorage"
        private const val ALBUM_NAME_PREFIX = "/SR"
        private const val DEBUG_FILE_PREFIX = "/DEBUG"

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
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + FileImageWriter.ALBUM_NAME_PREFIX + albumNumber)
        return file.isDirectory && file.exists()
    }

    private fun identifyDir() {
        // Identify directory index first
        refreshProposedPath()
        if (isAlbumDirExisting(startingAlbum)) {
            FileImageWriter.recreateDirectory(proposedPath!!)
        }
    }

    fun createDirectory() {
        identifyDir()
        val filePath = File(proposedPath)
        filePath.mkdirs()
        Log.i(TAG, "Image storage is set to: $proposedPath")
    }

    private fun refreshProposedPath() {
        proposedPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + ALBUM_NAME_PREFIX + startingAlbum
    }


    fun getDebugFilePath(): String {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + DEBUG_FILE_PREFIX
    }
}