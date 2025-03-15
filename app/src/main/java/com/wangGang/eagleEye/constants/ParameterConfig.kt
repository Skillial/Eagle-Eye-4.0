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

        const val DEBUGGING_FLAG_KEY = "DEBUGGING_FLAG_KEY"
        const val DENOISE_FLAG_KEY = "DENOISE_FLAG_KEY"
        const val FEATURE_MINIMUM_DISTANCE_KEY = "FEATURE_MINIMUM_DISTANCE_KEY"
        const val WARP_CHOICE_KEY = "WARP_CHOICE_KEY"
        const val SR_CHOICE_KEY = "SR_CHOICE_KEY"

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
        fun setTechnique(technique: SRTechnique) {
            sharedInstance?.currentTechnique = technique
        }

        @JvmStatic
        fun getCurrentTechnique(): SRTechnique {
            return sharedInstance?.currentTechnique ?: SRTechnique.MULTIPLE
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
            val defaultOrder = "Super Resolution,Dehaze"
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

        @JvmStatic
        fun isSuperResolutionEnabled(): Boolean {
            return getPrefsBoolean("super_resolution_enabled", false)
        }

        @JvmStatic
        fun isDehazeEnabled(): Boolean {
            return getPrefsBoolean("dehaze_enabled", false)
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
                    "Super Resolution"-> {
                        colors.add(Color.GREEN)
                    }
                    "Dehaze" -> {
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

    enum class SRTechnique {
        SINGLE,
        MULTIPLE
    }

    private var currentTechnique: SRTechnique = SRTechnique.MULTIPLE
    private val sharedPrefs: SharedPreferences = appContext.getSharedPreferences(PARAMETER_PREFS, Context.MODE_PRIVATE)
    private val editorPrefs: SharedPreferences.Editor = sharedPrefs.edit()
}