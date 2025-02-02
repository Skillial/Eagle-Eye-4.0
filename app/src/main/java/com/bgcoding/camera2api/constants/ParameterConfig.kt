package com.bgcoding.camera2api.constants

import android.content.Context
import android.content.SharedPreferences
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
            return sharedInstance?.sharedPrefs?.getInt(SCALE_KEY, 2) ?: 2
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
    }

    enum class SRTechnique {
        SINGLE,
        MULTIPLE
    }

    private var currentTechnique: SRTechnique = SRTechnique.MULTIPLE
    private val sharedPrefs: SharedPreferences = appContext.getSharedPreferences(PARAMETER_PREFS, Context.MODE_PRIVATE)
    private val editorPrefs: SharedPreferences.Editor = sharedPrefs.edit()
}