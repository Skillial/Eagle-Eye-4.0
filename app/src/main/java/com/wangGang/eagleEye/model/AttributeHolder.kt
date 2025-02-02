package com.wangGang.eagleEye.model

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Created by NeilDG on 5/4/2016.
 */
class AttributeHolder private constructor(appContext: Context) {

    companion object {
        private const val TAG = "AttributeHolder"
        private const val ATTRIBUTE_PREFS = "attribute_holder_prefs"
        private var sharedInstance: AttributeHolder? = null

        @JvmStatic
        fun getSharedInstance(): AttributeHolder? {
            return sharedInstance
        }

        @JvmStatic
        fun initialize(appContext: Context) {
            if (sharedInstance == null) {
                sharedInstance = AttributeHolder(appContext)
            }
        }
    }

    private val sharedPrefs: SharedPreferences = appContext.getSharedPreferences(ATTRIBUTE_PREFS, Context.MODE_PRIVATE)
    private val editorPrefs: SharedPreferences.Editor = sharedPrefs.edit()

    fun putValue(key: String, value: Int) {
        editorPrefs.putInt(key, value)
        editorPrefs.apply() // Use apply() instead of commit for efficiency
        Log.d(TAG, "Value added: ${getValue(key, -1)}")
    }

    fun putValue(key: String, value: Float) {
        editorPrefs.putFloat(key, value)
        editorPrefs.apply() // Use apply() instead of commit for efficiency
        Log.d(TAG, "Value added: ${getValueFloat(key, -1.0f)}")
    }

    fun getValue(key: String, defaultValue: Int): Int {
        return sharedPrefs.getInt(key, defaultValue)
    }

    private fun getValueFloat(key: String, defaultValue: Float): Float {
        return sharedPrefs.getFloat(key, defaultValue)
    }

    fun reset() {
        editorPrefs.clear()
        editorPrefs.apply()
    }
}
