package com.wangGang.eagleEye.constants

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.util.Log
import android.hardware.camera2.CaptureRequest
import com.wangGang.eagleEye.processing.commands.Dehaze
import com.wangGang.eagleEye.processing.commands.Denoising
import com.wangGang.eagleEye.processing.commands.ProcessingCommand
import com.wangGang.eagleEye.processing.commands.ShadowRemoval
import com.wangGang.eagleEye.processing.commands.SuperResolution
import com.wangGang.eagleEye.processing.commands.Upscale

/**
 * Created by NeilDG on 3/5/2016.
 */
class ParameterConfig private constructor(appContext: Context) {

    companion object {
        private const val TAG = "ParameterConfig"
        private var sharedInstance: ParameterConfig? = null
        private const val PARAMETER_PREFS = "parameter_config"
        private const val SCALE_KEY = "scale"
        private const val TIMER_DURATION_KEY = "timer_duration"
        private const val FLASH_ENABLED_KEY = "flash_enabled"
        private const val HDR_ENABLED_KEY = "hdr_enabled"
        private const val WHITE_BALANCE_MODE_KEY = "white_balance_mode"
        private const val EXPOSURE_COMPENSATION_KEY = "exposure_compensation"

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
            val defaultOrder = "${SuperResolution.displayName},${Dehaze.displayName},${Upscale.displayName},${ShadowRemoval.displayName},${Denoising.displayName}"
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
            Log.d(TAG, "isSuperResolutionEnabled: ${getProcessingOrder().contains(SuperResolution.displayName)}")
            return getProcessingOrder().contains(SuperResolution.displayName)
        }

        @JvmStatic
        fun isDehazeEnabled(): Boolean {
            Log.d(TAG, "isDehazeEnabled: ${getProcessingOrder().contains(Dehaze.displayName)}")
            return getProcessingOrder().contains(Dehaze.displayName)
        }

        @JvmStatic
        fun isShadowRemovalEnabled(): Boolean {
            Log.d(TAG, "isShadowRemovalEnabled: ${getProcessingOrder().contains(ShadowRemoval.displayName)}")
            return getProcessingOrder().contains(ShadowRemoval.displayName)
        }

        @JvmStatic
        fun isDenoisingEnabled(): Boolean {
            Log.d(TAG, "isDenoisingEnabled: ${getProcessingOrder().contains("Denoising")}")
            return getProcessingOrder().contains(Denoising.displayName)
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

        @JvmStatic
        fun setTimerDuration(duration: Int) {
            sharedInstance?.editorPrefs?.putInt(TIMER_DURATION_KEY, duration)?.apply()
            Log.d(TAG, "Timer duration set to: ${getTimerDuration()}s")
        }

        @JvmStatic
        fun getTimerDuration(): Int {
            return sharedInstance?.sharedPrefs?.getInt(TIMER_DURATION_KEY, 0) ?: 0
        }

        @JvmStatic
        fun setFlashEnabled(enabled: Boolean) {
            setPrefs(FLASH_ENABLED_KEY, enabled)
        }

        @JvmStatic
        fun isFlashEnabled(): Boolean {
            return getPrefsBoolean(FLASH_ENABLED_KEY, false)
        }

        @JvmStatic
        fun setHdrEnabled(enabled: Boolean) {
            setPrefs(HDR_ENABLED_KEY, enabled)
        }

        @JvmStatic
        fun isHdrEnabled(): Boolean {
            return getPrefsBoolean(HDR_ENABLED_KEY, false)
        }

        @JvmStatic
        fun setWhiteBalanceMode(mode: Int) {
            sharedInstance?.editorPrefs?.putInt(WHITE_BALANCE_MODE_KEY, mode)?.apply()
            Log.d(TAG, "White balance mode set to: $mode")
        }

        @JvmStatic
        fun getWhiteBalanceMode(): Int {
            return sharedInstance?.sharedPrefs?.getInt(WHITE_BALANCE_MODE_KEY, CaptureRequest.CONTROL_AWB_MODE_AUTO) ?: CaptureRequest.CONTROL_AWB_MODE_AUTO
        }

        @JvmStatic
        fun setExposureCompensation(value: Float) {
            sharedInstance?.editorPrefs?.putFloat(EXPOSURE_COMPENSATION_KEY, value)?.apply()
            Log.d(TAG, "Exposure compensation set to: $value")
        }

        @JvmStatic
        fun getExposureCompensation(): Float {
            return sharedInstance?.sharedPrefs?.getFloat(EXPOSURE_COMPENSATION_KEY, 0f) ?: 0f
        }
    }

    private val sharedPrefs: SharedPreferences = appContext.getSharedPreferences(PARAMETER_PREFS, Context.MODE_PRIVATE)
    private val editorPrefs: SharedPreferences.Editor = sharedPrefs.edit()
}