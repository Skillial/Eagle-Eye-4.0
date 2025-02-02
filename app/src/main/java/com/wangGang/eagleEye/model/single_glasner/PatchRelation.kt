package com.wangGang.eagleEye.model.single_glasner

/**
 * Created by NeilDG on 5/10/2016.
 */
class PatchRelation(
    private val lrAttrib: PatchAttribute, private val hrAttrib: PatchAttribute,
    val similarity: Double
) {

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