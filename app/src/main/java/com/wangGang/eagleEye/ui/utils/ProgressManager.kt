package com.wangGang.eagleEye.ui.utils

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.wangGang.eagleEye.constants.ParameterConfig
import com.wangGang.eagleEye.processing.commands.ProcessingCommand
import com.wangGang.eagleEye.ui.viewmodels.CameraViewModel

class ProgressManager private constructor(private val viewModel: CameraViewModel) {
    val TAG = "ProgressManager"

    companion object {
        @Volatile
        private var INSTANCE: ProgressManager? = null

        fun initialize(viewModel: CameraViewModel) {
            synchronized(this) {
                if (INSTANCE == null) {
                    INSTANCE = ProgressManager(viewModel)
                }
            }
        }

        fun getInstance(): ProgressManager {
            return INSTANCE ?: throw IllegalStateException("ProgressManager is not initialized. Call initialize() first.")
        }

        fun destroyInstance() {
            INSTANCE = null // Allows GC to clean it up
        }
    }

    /* === CONSTANTS === */
    // Observers
    private val _progress = MutableLiveData<Int>()
    val progress: LiveData<Int> get() = _progress

    // Track progress
    private var completedTasks: Int
    private var totalTasks: Int
    private var taskList: List<String>

    init {
        _progress.value = 0
        completedTasks = 0
        totalTasks = 0
        taskList = emptyList()
    }

    fun incrementProgress(debugMessage: String) {
        incrementProgress()
        Log.d("ProgressBar", "Finished: $debugMessage")
        Log.d("ProgressBar", "Progress: $completedTasks/$totalTasks")
    }

    private fun resetValues() {
        completedTasks = 0
        taskList = emptyList()
        _progress.postValue(0)
    }

    fun resetProgress() {
        resetValues()
        calculateTotalTasks()
        addTasks()
        showLoadingText()
        debugPrint()
    }

    fun nextTask() {
        debugPrint()
        incrementProgress()
        showLoadingText()
    }

    private fun debugPrint() {
        Log.d(TAG, "debugPrint()")
        Log.d(TAG, "Progress: $completedTasks/$totalTasks")
        Log.d(TAG, "Current Task Index: $completedTasks")
        Log.d(TAG, "Current Task: ${taskList[completedTasks]}")
        if (completedTasks < totalTasks) {
            Log.d(TAG, "Next Task: ${taskList[completedTasks + 1]}")
        }
    }

    private fun showLoadingText() {
        val ind = completedTasks
        if (ind in taskList.indices) {
            viewModel.updateLoadingText(taskList[ind])
        } else {
            Log.w(TAG, "updateLoadingText - Index out of bounds: $ind (taskList size: ${taskList.size})")
        }
    }

    private fun addTasks() {
        val order = ParameterConfig.getProcessingOrder()

        for (item in order) {
            if (ProcessingCommand.fromDisplayName(item) != null) {
                taskList += ProcessingCommand.fromDisplayName(item)!!.tasks
            }
        }

        Log.d(TAG, "addTasks - taskList: $taskList")
    }

    private fun calculateTotalTasks(): Int {
        val order = ParameterConfig.getProcessingOrder()
        var total = 0
        for (item in order) {
            if (ProcessingCommand.fromDisplayName(item) != null) {
                total += ProcessingCommand.fromDisplayName(item)!!.calculate()
            }
        }

        Log.d(TAG, "calculateTotalTasks - total: $total")
        return total
    }

    private fun updateProgress() {
        val progressValue = (completedTasks.toFloat() / totalTasks.toFloat() * 100).toInt()
        _progress.postValue(progressValue)
    }

    private fun incrementProgress() {
        if (completedTasks < totalTasks) {
            completedTasks++
            updateProgress()
        }
    }
}
