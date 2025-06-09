package com.wangGang.eagleEye

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
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
//        setContentView(R.layout.activity_main)

        initializeOpenCV()
        loadComponents()

        permissionHandler = PermissionsHandler(this)
        requestPermissions()
    }

    private fun requestPermissions() {
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
        finish()
    }

    private fun loadComponents() {
        DirectoryStorage.getSharedInstance().createDirectory()
        FileImageWriter.initialize(this)
        FileImageReader.initialize(this)
        ParameterConfig.initialize(this)
        AttributeHolder.initialize(this)
    }
}
