package com.wangGang.eagleEye.io

/**
 * Created by NeilDG on 4/21/2016.
 */
object ImageFileAttribute {

    private const val JPEG_EXT = ".jpg"
    private const val PNG_EXT = ".png"

    enum class FileType {
        JPEG,
        PNG
    }

    fun getFileExtension(fileType: FileType): String {
        return when (fileType) {
            FileType.JPEG -> JPEG_EXT
            FileType.PNG -> PNG_EXT
        }
    }
}
