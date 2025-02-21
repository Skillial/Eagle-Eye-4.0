// SuperResolutionTemplate.kt
package com.wangGang.eagleEye.processing

import com.wangGang.eagleEye.model.multiple.SharpnessMeasure
import com.wangGang.eagleEye.model.multiple.SharpnessMeasure.SharpnessResult
import com.wangGang.eagleEye.processing.process_observer.SRProcessManager
import org.opencv.core.Mat

abstract class SuperResolutionTemplate {

    // Template method
    fun superResolutionImage(imageInputMap: List<String>) {
        val filteredMatList = initialize(imageInputMap)
        performSuperResolution(filteredMatList, imageInputMap)
//        finalizeProcess()
    }

    open fun initialize(imageInputMap: List<String>): Array<Mat> {
        SharpnessMeasure.initialize()
        val energyInputMatList = readEnergy(imageInputMap)
        val filteredMatList = applyFilter(energyInputMatList)
        return filteredMatList
    }

    protected abstract fun readEnergy(imageInputMap: List<String>): Array<Mat>

    protected abstract fun applyFilter(energyInputMatList: Array<Mat>): Array<Mat>

    protected abstract fun measureSharpness(filteredMatList: Array<Mat>): SharpnessResult

    protected abstract fun performSuperResolution(filteredMatList: Array<Mat>, imageInputMap: List<String>)

    protected open fun finalizeProcess() {
        SRProcessManager.getInstance().srProcessCompleted()
    }
}