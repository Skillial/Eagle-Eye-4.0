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

    fun resetProgress() {
        resetValues()
        calculateTotalTasks()
        addTasks()
        debugPrint()
    }

    fun showFirstTask() {
        Log.d(TAG, "showFirstTask()")
        if (taskList.isNotEmpty()) {
            viewModel.updateLoadingText(taskList[0])
        }
    }

    fun nextTask() {
        incrementProgress()
        debugPrint()
        showLoadingText()
        onAllTasksCompleted()
    }

    private fun debugPrint() {
        Log.d(TAG, "debugPrint()")
        Log.d(TAG, "Progress: $completedTasks/$totalTasks")
        Log.d(TAG, "Current Task Index: $completedTasks")
        if (completedTasks < totalTasks && taskList.isNotEmpty()) {
            Log.d(TAG, "Current Task: ${taskList[completedTasks]}")

            if (completedTasks + 1 < totalTasks) {
                Log.d(TAG, "Next Task: ${taskList[completedTasks + 1]}")
            }
        }
    }

    fun getCurrentTask(): String? {
        return if (completedTasks < taskList.size) {
            taskList[completedTasks]
        } else {
            Log.w(TAG, "getCurrentTask - Index out of bounds: $completedTasks (taskList size: ${taskList.size})")
            null
        }
    }

    private fun showLoadingText() {
        val ind = completedTasks
        if (ind in taskList.indices) {
            Log.d(TAG, "updateLoadingText - Current Task: ${taskList[ind]} at index $ind")
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

    private fun onAllTasksCompleted() {
        if (completedTasks >= totalTasks) {
            Log.d(TAG, "onAllTasksCompleted - All tasks completed")
            viewModel.setLoadingBoxVisible(false)
            debugPrint()
            resetValues()
        }
    }

    private fun calculateTotalTasks() {
        val order = ParameterConfig.getProcessingOrder()
        var total = 0
        for (item in order) {
            if (ProcessingCommand.fromDisplayName(item) != null) {
                total += ProcessingCommand.fromDisplayName(item)!!.calculate()
            }
        }

        Log.d(TAG, "calculateTotalTasks - total: $total")
        totalTasks = total
    }

    private fun updateProgress() {
        Log.d(TAG, "updateProgress()")
        val progressValue = (completedTasks.toFloat() / totalTasks.toFloat() * 100).toInt()
        _progress.postValue(progressValue)
    }

    private fun incrementProgress() {
        if (completedTasks < totalTasks) {
            completedTasks++
            updateProgress()
        }
    }

    private fun resetValues() {
        completedTasks = 0
        taskList = emptyList()
        _progress.postValue(0)
        Log.d(TAG, "===== resetValues() =====")
        Log.d(TAG, "values = completedTasks: $completedTasks, totalTasks: $totalTasks, taskList: $taskList")
    }
}
