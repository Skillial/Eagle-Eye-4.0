package com.wangGang.eagleEye


import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.wangGang.eagleEye.constants.ParameterConfig
import com.wangGang.eagleEye.io.DirectoryStorage
import com.wangGang.eagleEye.io.FileImageReader
import com.wangGang.eagleEye.io.FileImageWriter
import com.wangGang.eagleEye.model.AttributeHolder
import com.wangGang.eagleEye.permissions.PermissionsHandler
import com.wangGang.eagleEye.ui.activities.CameraControllerActivity
import org.opencv.android.OpenCVLoader


class MainActivity : AppCompatActivity() {
    private lateinit var permissionHandler: PermissionsHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeOpenCV()
        loadComponents()
        requestPermissions()
    }

    private fun requestPermissions() {
        permissionHandler = PermissionsHandler(this)
        permissionHandler.requestPermissions { allPermissionsGranted ->
            if (allPermissionsGranted) {
                initializeApp()
            } else {
                Log.e("Permissions", "Required permissions not granted")
            }
        }
    }

    private fun initializeOpenCV() {
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Initialization Failed")
        } else {
            Log.d("OpenCV", "Initialization Successful")
        }
    }

    private fun initializeApp() {
        val intent = Intent(this, CameraControllerActivity::class.java)
        startActivity(intent)
        finish()    // Disable going back to main activity
    }

    private fun loadComponents() {
        // Initialize components
        DirectoryStorage.getSharedInstance().createDirectory()
        FileImageWriter.initialize(this)
        FileImageReader.initialize(this)
        ParameterConfig.initialize(this)
        AttributeHolder.initialize(this)
    }
}