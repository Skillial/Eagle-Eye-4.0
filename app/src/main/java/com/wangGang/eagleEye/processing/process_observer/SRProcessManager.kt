package com.wangGang.eagleEye.processing.process_observer

import android.app.Activity
import android.util.Log

/**
 * Class that holds a process listener that informs any activity of the current SR process
 * Created by NeilDG on 4/29/2017.
 */
class SRProcessManager private constructor() {

    companion object {
        private const val TAG = "SRProcessManager"
        private var sharedInstance: SRProcessManager? = null

        @JvmStatic
        fun getInstance(): SRProcessManager {
            if (sharedInstance == null) {
                sharedInstance = SRProcessManager()
            }
            return sharedInstance!!
        }
    }

    private var currentProcessListener: IProcessListener? = null
    private var activity: Activity? = null

    fun setProcessListener(processListener: IProcessListener, activity: Activity) {
        this.currentProcessListener = processListener
        this.activity = activity
    }

    fun initialHRProduced() {
        currentProcessListener?.let {
            activity?.runOnUiThread {
                it.onProducedInitialHR()
            }
        } ?: Log.e(TAG, "No process listener has been assigned!")
    }

    fun srProcessCompleted() {
        currentProcessListener?.let {
            activity?.runOnUiThread {
                it.onProcessCompleted()
            }
        } ?: Log.e(TAG, "No process listener has been assigned!")
    }
}
