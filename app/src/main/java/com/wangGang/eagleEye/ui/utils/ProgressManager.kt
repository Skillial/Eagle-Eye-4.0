package com.wangGang.eagleEye.ui.utils

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.wangGang.eagleEye.constants.ImageEnhancementType
import com.wangGang.eagleEye.constants.ParameterConfig
import com.wangGang.eagleEye.processing.commands.ProcessingCommand
import com.wangGang.eagleEye.processing.commands.SuperResolution

class ProgressManager private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: ProgressManager? = null

        fun getInstance(): ProgressManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProgressManager().also { INSTANCE = it }
            }
        }

        fun destroyInstance() {
            INSTANCE = null // Allows GC to clean it up
        }
    }

    // Observers
    private val _progress = MutableLiveData<Int>()
    val progress: LiveData<Int> get() = _progress

    // Track progress
    private var completedTasks = 0
    private var totalTasks = 0

    init {
        _progress.value = 0
    }

    fun incrementProgress() {
        if (completedTasks < totalTasks) {
            completedTasks++
            updateProgress()
        }
    }

    fun incrementProgress(debugMessage: String) {
        incrementProgress()
        Log.d("ProgressBar", "Finished: $debugMessage")
        Log.d("ProgressBar", "Progress: $completedTasks/$totalTasks")
    }

    private fun resetValues() {
        completedTasks = 0
        _progress.postValue(0)
    }

    fun resetProgress() {
        resetValues()
        calculateTotalTasks()
    }


    private fun calculateTotalTasks(): Int {
        val order = ParameterConfig.getProcessingOrder()
        var total = 0
        for (item in order) {
            if (ProcessingCommand.fromDisplayName(item) != null) {
                total += ProcessingCommand.fromDisplayName(item)!!.calculate()
            }
        }
        return total
    }

    private fun updateProgress() {
        val progressValue = (completedTasks.toFloat() / totalTasks.toFloat() * 100).toInt()
        _progress.postValue(progressValue)
    }
}
