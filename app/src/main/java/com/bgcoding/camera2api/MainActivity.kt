package com.bgcoding.camera2api


import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.bgcoding.camera2api.constants.ParameterConfig
import com.bgcoding.camera2api.io.DirectoryStorage
import com.bgcoding.camera2api.io.FileImageReader
import com.bgcoding.camera2api.io.FileImageWriter
import com.bgcoding.camera2api.model.AttributeHolder
import com.bgcoding.camera2api.permissions.PermissionsHandler
import com.bgcoding.camera2api.ui.fragments.CameraFragment
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
        supportFragmentManager.commit {
            replace(R.id.fragment_container, CameraFragment())
        }
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