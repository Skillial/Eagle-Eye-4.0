package com.wangGang.eagleEye.ui.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Toast

object CustomToast {

    private var currentToast: Toast? = null
    private val handler = Handler(Looper.getMainLooper())

    fun show(context: Context, message: String, durationMillis: Long = 500L, yOffset: Int = 0) {
        currentToast?.cancel() // Cancel any currently showing toast

        currentToast = Toast.makeText(context, message, Toast.LENGTH_SHORT).apply {
            setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, yOffset)
            show()
        }

        // Schedule the toast to be hidden after durationMillis
        handler.postDelayed({ currentToast?.cancel() }, durationMillis)
    }
}