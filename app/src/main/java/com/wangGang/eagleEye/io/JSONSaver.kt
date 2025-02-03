package com.wangGang.eagleEye.io

import android.util.JsonWriter
import com.wangGang.eagleEye.model.single_glasner.PatchAttribute
import com.wangGang.eagleEye.model.single_glasner.PatchRelationTable
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter

object JSONSaver {
    private const val TAG = "JSONSaver"

    fun writeSimilarPatches(fileName: String, pairwiseTable: HashMap<PatchAttribute, PatchRelationTable.PatchRelationList>) {
        val jsonFile = File(DirectoryStorage.getSharedInstance().proposedPath, "$fileName.json")
        try {
            FileOutputStream(jsonFile).use { out ->
                JsonWriter(OutputStreamWriter(out)).use { jsonWriter ->
                    jsonWriter.setIndent("  ")
                    jsonWriter.beginArray()
                    for ((lrPatchAttrib, patchRelationList) in pairwiseTable) {
                        jsonWriter.beginObject()
                        jsonWriter.name("lr_patch_key").value(lrPatchAttrib.imageName)

                        for (i in 0 until patchRelationList.getCount()) {
                            val patchRelation = patchRelationList.getPatchRelationAt(i)
                            val lrAttrib = patchRelation.getLrAttrib()
                            val hrAttrib = patchRelation.getHrAttrib()

                            jsonWriter.name("$i").beginObject()
                            jsonWriter.name("lr").value(lrAttrib.imageName)
                            jsonWriter.name("hr").value(hrAttrib.imageName)
                            jsonWriter.name("similarity").value(patchRelation.similarity)
                            jsonWriter.endObject()
                        }
                        jsonWriter.endObject()
                    }
                    jsonWriter.endArray()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun mapToJson(data: HashMap<PatchAttribute, PatchAttribute>): JSONObject {
        val jsonObject = JSONObject()
        for ((lrPatchAttrib, hrPatchAttrib) in data) {
            val key = lrPatchAttrib.imageName
            try {
                jsonObject.put(key, hrPatchAttrib.imageName)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        return jsonObject
    }

    fun debugWriteEdgeConsistencyMeasure(
        warpMethod: Int,
        warpedResults: IntArray,
        warpResultDifferences: IntArray,
        warpedMatNames: Array<String>
    ) {
        val fileName = when (warpMethod) {
            0 -> "affine_warp_result"
            1 -> "perspective_warp_result"
            2 -> "exposure_alignment_result"
            else -> "no_warp_specified"
        }

        val jsonFile = File(DirectoryStorage.getSharedInstance().proposedPath, "$fileName.json")
        try {
            FileOutputStream(jsonFile).use { out ->
                JsonWriter(OutputStreamWriter(out)).use { jsonWriter ->
                    jsonWriter.setIndent("  ")
                    jsonWriter.beginArray()
                    for (i in warpedMatNames.indices) {
                        jsonWriter.beginObject()
                        jsonWriter.name(warpedMatNames[i]).value(warpedResults[i])
                        jsonWriter.endObject()
                    }

                    for (i in warpedMatNames.indices) {
                        jsonWriter.beginObject()
                        jsonWriter.name("${warpedMatNames[i]} difference from reference").value(warpResultDifferences[i])
                        jsonWriter.endObject()
                    }
                    jsonWriter.endArray()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
