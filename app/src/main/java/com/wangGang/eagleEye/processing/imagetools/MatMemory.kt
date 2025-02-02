package com.wangGang.eagleEye.processing.imagetools
import org.opencv.core.Mat

/**
 * Created by NeilDG on 9/12/2016.
 */
object MatMemory {

    @JvmStatic
    fun releaseAll(matNames: Array<String?>, forceGC: Boolean) {
        for (i in matNames.indices) {
            if (matNames[i] != null) {
                matNames[i] = null
            }
        }

        if (forceGC) {
            System.gc()
            System.runFinalization()
        }
    }

    @JvmStatic
    fun releaseAll(matList: Array<Mat?>, forceGC: Boolean) {
        for (i in matList.indices) {
            matList[i]?.release()
            matList[i] = null
        }

        if (forceGC) {
            System.gc()
            System.runFinalization()
        }
    }

    @JvmStatic
    fun releaseAll(matList: MutableList<Mat>, forceGC: Boolean) {
        for (i in matList.indices) {
            matList[i].release()
        }

        matList.clear()

        if (forceGC) {
            System.gc()
            System.runFinalization()
        }
    }

    @JvmStatic
    fun cleanMemory() {
        System.gc()
        System.runFinalization()
    }
}
