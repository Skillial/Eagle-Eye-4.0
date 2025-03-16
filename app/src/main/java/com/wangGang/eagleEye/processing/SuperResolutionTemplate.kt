// SuperResolutionTemplate.kt
package com.wangGang.eagleEye.processing

import android.graphics.Bitmap
import com.wangGang.eagleEye.io.ImageUtils
import com.wangGang.eagleEye.model.multiple.SharpnessMeasure
import com.wangGang.eagleEye.model.multiple.SharpnessMeasure.SharpnessResult
import com.wangGang.eagleEye.processing.process_observer.SRProcessManager
import com.wangGang.eagleEye.ui.utils.ProgressManager
import org.opencv.core.Mat

abstract class SuperResolutionTemplate {

    // Template method
    fun superResolutionImage(imageInputMap: List<String>): Bitmap {
        val filteredMatList = initialize(imageInputMap)
        return performSuperResolution(filteredMatList, imageInputMap)
//        finalizeProcess()
    }

    open fun initialize(imageInputMap: List<String>): Array<Mat> {

        SharpnessMeasure.initialize()

        val energyInputMatList = readEnergy(imageInputMap)
        ProgressManager.getInstance().nextTask()

        val filteredMatList = applyFilter(energyInputMatList)
        ProgressManager.getInstance().nextTask()

        energyInputMatList.forEach { it.release() }

        return filteredMatList
    }

    protected abstract fun readEnergy(imageInputMap: List<String>): Array<Mat>

    protected abstract fun applyFilter(energyInputMatList: Array<Mat>): Array<Mat>

    protected abstract fun measureSharpness(filteredMatList: Array<Mat>): SharpnessResult

    protected abstract fun performSuperResolution(filteredMatList: Array<Mat>, imageInputMap: List<String>): Bitmap

    protected open fun finalizeProcess() {
        SRProcessManager.getInstance().srProcessCompleted()
    }
}