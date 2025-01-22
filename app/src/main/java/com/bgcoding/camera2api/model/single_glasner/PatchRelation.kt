package com.bgcoding.camera2api.model.single_glasner

/**
 * Created by NeilDG on 5/10/2016.
 */
class PatchRelation(
    lrAttrib: PatchAttribute, hrAttrib: PatchAttribute,
    val similarity: Double
) {
    private val lrAttrib: PatchAttribute = lrAttrib
    private val hrAttrib: PatchAttribute = hrAttrib

    fun getLrAttrib(): PatchAttribute {
        return this.lrAttrib
    }

    fun getHrAttrib(): PatchAttribute {
        return this.hrAttrib
    }

    companion object {
        private const val TAG = "PatchRelation"
    }
}