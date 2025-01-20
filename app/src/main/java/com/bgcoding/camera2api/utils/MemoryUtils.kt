package com.bgcoding.camera2api.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Debug

object MemoryUtils {
    fun getAppMemoryUsage(context: Context): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val memoryInfoArray = arrayOf(Debug.MemoryInfo())
        Debug.getMemoryInfo(memoryInfoArray[0])

        val usedMemory = memoryInfoArray[0].getTotalPss() * 1024L // in bytes
        return usedMemory
    }
}