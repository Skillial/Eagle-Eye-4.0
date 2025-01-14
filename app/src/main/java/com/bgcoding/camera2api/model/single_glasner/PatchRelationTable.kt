package com.bgcoding.camera2api.model.single_glasner

import android.util.Log
import com.bgcoding.camera2api.io.JSONSaver
import java.util.*
import kotlin.collections.HashMap

/**
 * Holds the HR-LR patch dictionary pairing
 * Created by NeilDG on 5/10/2016.
 */
class PatchRelationTable private constructor() {

    companion object {
        private const val TAG = "PatchRelationTable"

        private var sharedInstance: PatchRelationTable? = null

        fun getSharedInstance(): PatchRelationTable? {
            return sharedInstance
        }

        fun initialize() {
            sharedInstance = PatchRelationTable()
        }

        fun destroy() {
            sharedInstance = null
        }
    }

    private val pairwiseTable: HashMap<PatchAttribute, PatchRelationList> = HashMap()
    private val pairList: MutableList<PatchRelationList> = mutableListOf()

    fun addPairwisePatch(lrAttrib: PatchAttribute, hrAttrib: PatchAttribute, similarity: Double) {
        if (!pairwiseTable.containsKey(lrAttrib)) {
            val patchRelationList = PatchRelationList()
            patchRelationList.addPatchRelation(lrAttrib, hrAttrib, similarity)
            pairwiseTable[lrAttrib] = patchRelationList
            pairList.add(patchRelationList)
        } else {
            val patchRelationList = pairwiseTable[lrAttrib]
            patchRelationList?.addPatchRelation(lrAttrib, hrAttrib, similarity)
            Log.e(TAG, "Pairwise table of ${lrAttrib.imageName} and its HR match ${hrAttrib.imageName} already exists. Adding to list")
        }
    }

    fun sort() {
        pairList.forEach { it.sort() }
    }

    fun hasHRAttribute(lrAttrib: PatchAttribute): Boolean {
        return pairwiseTable.containsKey(lrAttrib)
    }

    fun getHRAttribute(lrAttrib: PatchAttribute, index: Int): PatchAttribute? {
        return if (hasHRAttribute(lrAttrib)) {
            val patchRelation = pairwiseTable[lrAttrib]?.getPatchRelationAt(index)
            patchRelation?.getHrAttrib()
        } else {
            Log.e(TAG, "LR attribute ${lrAttrib.imageName} does not exist.")
            null
        }
    }

    fun saveMapToJSON() {
        JSONSaver.writeSimilarPatches("patch_table", pairwiseTable)
    }

    fun getPairCount(): Int {
        return pairwiseTable.size
    }

    fun getPatchRelationAt(index: Int): PatchRelationList {
        return pairList[index]
    }

    inner class PatchRelationList : Comparator<PatchRelation> {
        private val patchRelations: MutableList<PatchRelation> = mutableListOf()

        fun addPatchRelation(lrAttrib: PatchAttribute, hrAttrib: PatchAttribute, similarity: Double) {
            val patchRelation = PatchRelation(lrAttrib, hrAttrib, similarity)
            patchRelations.add(patchRelation)
        }

        fun deleteHRPatch(patchRelation: PatchRelation) {
            patchRelations.remove(patchRelation)
        }

        fun clear() {
            patchRelations.clear()
        }

        fun getCount(): Int {
            return patchRelations.size
        }

        fun hasPatches(): Boolean {
            return getCount() > 0
        }

        fun sort() {
            patchRelations.sortWith(this)
        }

        override fun compare(t0: PatchRelation, t1: PatchRelation): Int {
            return when {
                t0.similarity < t1.similarity -> -1
                t0.similarity > t1.similarity -> 1
                else -> 0
            }
        }

        fun getPatchRelationAt(index: Int): PatchRelation {
            return patchRelations[index]
        }
    }
}
