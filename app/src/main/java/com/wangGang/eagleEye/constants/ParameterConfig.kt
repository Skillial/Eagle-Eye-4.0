package com.wangGang.eagleEye.constants

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.util.Log

/**
 * Created by NeilDG on 3/5/2016.
 */
class ParameterConfig private constructor(appContext: Context) {

    companion object {
        private const val TAG = "ParameterConfig"
        private var sharedInstance: ParameterConfig? = null
        private const val PARAMETER_PREFS = "parameter_config"
        private const val SCALE_KEY = "scale"

        const val FEATURE_MINIMUM_DISTANCE_KEY = "FEATURE_MINIMUM_DISTANCE_KEY"
        const val WARP_CHOICE_KEY = "WARP_CHOICE_KEY"

        @JvmStatic
        fun hasInitialized(): Boolean {
            return sharedInstance != null
        }

        @JvmStatic
        fun initialize(appContext: Context) {
            if (sharedInstance == null) {
                sharedInstance = ParameterConfig(appContext)
            }
        }

        @JvmStatic
        fun setScalingFactor(scale: Int) {
            sharedInstance?.editorPrefs?.putInt(SCALE_KEY, scale)?.apply()
            Log.d(TAG, "Scaling set to in prefs: ${getScalingFactor()}")
        }

        @JvmStatic
        fun getScalingFactor(): Int {
            return sharedInstance?.sharedPrefs?.getInt(SCALE_KEY, 2) ?: 2 // change to 2
        }

        @JvmStatic
        fun setPrefs(key: String, value: Boolean) {
            sharedInstance?.editorPrefs?.putBoolean(key, value)?.apply()
        }

        @JvmStatic
        fun setPrefs(key: String, value: Int) {
            sharedInstance?.editorPrefs?.putInt(key, value)?.apply()
        }

        @JvmStatic
        fun setPrefs(key: String, value: Long) {
            sharedInstance?.editorPrefs?.putLong(key, value)?.apply()
        }

        @JvmStatic
        fun setPrefs(key: String, value: Float) {
            sharedInstance?.editorPrefs?.putFloat(key, value)?.apply()
        }

        @JvmStatic
        fun setPrefs(key: String, value: String) {
            sharedInstance?.editorPrefs?.putString(key, value)?.apply()
        }

        @JvmStatic
        fun getPrefsString(key: String, defaultValue: String): String {
            return sharedInstance?.sharedPrefs?.getString(key, defaultValue) ?: defaultValue
        }

        @JvmStatic
        fun getPrefsBoolean(key: String, defaultValue: Boolean): Boolean {
            return sharedInstance?.sharedPrefs?.getBoolean(key, defaultValue) ?: defaultValue
        }

        @JvmStatic
        fun getPrefsInt(key: String, defaultValue: Int): Int {
            return sharedInstance?.sharedPrefs?.getInt(key, defaultValue) ?: defaultValue
        }

        @JvmStatic
        fun getPrefsLong(key: String, defaultValue: Long): Long {
            return sharedInstance?.sharedPrefs?.getLong(key, defaultValue) ?: defaultValue
        }

        @JvmStatic
        fun getPrefsFloat(key: String, defaultValue: Float): Float {
            return sharedInstance?.sharedPrefs?.getFloat(key, defaultValue) ?: defaultValue
        }

        @JvmStatic
        /*
        * Gets the processing order and returns it as a list of strings
        * */
        fun getProcessingOrder(): List<String> {
            val defaultOrder = "${ProcessingAlgorithm.SUPER_RESOLUTION.displayName},${ProcessingAlgorithm.DEHAZE.displayName}"
            val stored = getPrefsString("algo_order", defaultOrder)
            return stored.split(",")
        }

        @JvmStatic
        /*
        * Sets the processing order by joining the list of strings into a single string
        * */
        fun setProcessingOrder(processingOrderList: List<String>) {
            val processingOrder = processingOrderList.joinToString(",")
            setPrefs("algo_order", processingOrder)
        }

        // TODO: Modify this code when the app supports multiple Super Resolution Blocks
        @JvmStatic
        fun isSuperResolutionEnabled(): Boolean {
            return getProcessingOrder().contains(ProcessingAlgorithm.SUPER_RESOLUTION.displayName)
        }

        // TODO: Modify this code when the app supports multiple Dehaze Blocks
        @JvmStatic
        fun isDehazeEnabled(): Boolean {
            return getProcessingOrder().contains(ProcessingAlgorithm.DEHAZE.displayName)
        }

        @JvmStatic
        fun setGridOverlayEnabled(enabled: Boolean) {
            setPrefs("grid_overlay_enabled", enabled)
        }

        @JvmStatic
        fun isGridOverlayEnabled(): Boolean {
            return getPrefsBoolean("grid_overlay_enabled", false)
        }

        // Helper methods
        fun isScalingFactorGreaterThanOrEqual8(): Boolean {
            return getScalingFactor() >= 8
        }

        // Mix all colors
        fun getColorBasedOnAlgo() {
            val processingOrder = getProcessingOrder()
            val colors = mutableListOf<Int>()
            for (algo in processingOrder) {
                when (algo) {
                    ProcessingAlgorithm.SUPER_RESOLUTION.displayName -> {
                        colors.add(Color.GREEN)
                    }
                    ProcessingAlgorithm.DEHAZE.displayName  -> {
                        colors.add(Color.YELLOW)
                    }
                }
            }

            // mix all colors
            var mixedColor = 0
            for (color in colors) {
                mixedColor = mixedColor or color
            }
        }
    }

    enum class ProcessingAlgorithm(val displayName: String) {
        SUPER_RESOLUTION("Super Resolution"),
        DEHAZE("Dehaze"),
        UPSCALE("Upscale")
    }

    private val sharedPrefs: SharedPreferences = appContext.getSharedPreferences(PARAMETER_PREFS, Context.MODE_PRIVATE)
    private val editorPrefs: SharedPreferences.Editor = sharedPrefs.edit()
}