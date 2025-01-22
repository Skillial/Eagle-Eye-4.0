package com.bgcoding.camera2api.model.single_glasner

/**
 * Patch attributes are stored here for convenience on retrieving and formulating of image patch matrices.
 * Created by NeilDG on 5/10/2016.
 */
class PatchAttribute(
    pyramidDepth: Int,
    colStart: Int,
    rowStart: Int,
    colEnd: Int,
    rowEnd: Int,
    imageName: String,
    imagePath: String
) {
    var pyramidDepth: Int = 0
    var colStart: Int = 0
    var rowStart: Int = 0
    var colEnd: Int = 0
    var rowEnd: Int = 0
    val imageName: String
    val imagePath: String

    init {
        this.pyramidDepth = pyramidDepth
        this.colStart = colStart
        this.rowStart = rowStart
        this.colEnd = colEnd
        this.rowEnd = rowEnd
        this.imageName = imageName
        this.imagePath = imagePath
    }


    companion object {
        private const val TAG = "PatchAttribute"
    }
}