package com.wangGang.eagleEye.ui.utils

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.wangGang.eagleEye.constants.ImageEnhancementType

class ProgressManager private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: ProgressManager? = null

        // Constants
        private val TASKS_SUPER_RESOLUTION = 9
        private val TASKS_DEHAZE = 15

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
    private var totalTasks = 1

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

    fun resetProgress() {
        completedTasks = 0
        _progress.postValue(0)
    }

    fun resetProgress(newTotalTasks: Int) {
        totalTasks = newTotalTasks
        resetProgress()
    }

    fun resetProgress(type: ImageEnhancementType) {
        Log.d("ProgressManager", "Resetting progress for type: $type")
        val newTotalTasks = when (type) {
            ImageEnhancementType.SUPER_RESOLUTION -> TASKS_SUPER_RESOLUTION
            ImageEnhancementType.DEHAZE -> TASKS_DEHAZE
        }
        totalTasks = newTotalTasks
        resetProgress()
    }



    private fun updateProgress() {
        val progressValue = (completedTasks.toFloat() / totalTasks.toFloat() * 100).toInt()
        _progress.postValue(progressValue)
    }
}
