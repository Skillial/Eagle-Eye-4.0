package com.wangGang.eagleEye.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class PermissionsHandler(private val context: Context) {
    private val requiredPermissions = if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.Q) {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    } else {
        arrayOf(
            Manifest.permission.CAMERA
        )
    }

    fun requestPermissions(callback: (Boolean) -> Unit) {
        val allPermissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allPermissionsGranted) {
            callback(true)
        } else {
            val permissionsLauncher = (context as androidx.activity.ComponentActivity).registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                callback(permissions.values.all { it })
            }
            permissionsLauncher.launch(requiredPermissions)
        }
    }
}
