package com.bgcoding.camera2api


import LRWarpingOperator
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.bgcoding.camera2api.assessment.InputImageEnergyReader
import com.bgcoding.camera2api.camera.CameraController
import com.bgcoding.camera2api.constants.ParameterConfig
import com.bgcoding.camera2api.io.DirectoryStorage
import com.bgcoding.camera2api.io.FileImageReader
import com.bgcoding.camera2api.io.FileImageWriter
import com.bgcoding.camera2api.io.ImageFileAttribute
import com.bgcoding.camera2api.io.ImageReaderManager
import com.bgcoding.camera2api.model.AttributeHolder
import com.bgcoding.camera2api.model.multiple.SharpnessMeasure
import com.bgcoding.camera2api.permissions.PermissionsHandler
import com.bgcoding.camera2api.processing.ConcreteSuperResolution
import com.bgcoding.camera2api.processing.filters.YangFilter
import com.bgcoding.camera2api.processing.imagetools.ImageOperator
import com.bgcoding.camera2api.processing.multiple.alignment.FeatureMatchingOperator
import com.bgcoding.camera2api.processing.multiple.alignment.MedianAlignmentOperator
import com.bgcoding.camera2api.processing.multiple.alignment.WarpResultEvaluator
import com.bgcoding.camera2api.processing.multiple.enhancement.UnsharpMaskOperator
import com.bgcoding.camera2api.processing.multiple.fusion.MeanFusionOperator
import com.bgcoding.camera2api.processing.multiple.refinement.DenoisingOperator
import com.bgcoding.camera2api.processing.process_observer.SRProcessManager
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File
import java.util.concurrent.Semaphore


class MainActivity : ComponentActivity() {
    private lateinit var permissionHandler: PermissionsHandler
    private lateinit var cameraController: CameraController
    private lateinit var imageReaderManager: ImageReaderManager
    private lateinit var concreteSuperResolution: ConcreteSuperResolution

    lateinit var textureView: TextureView
    lateinit var progressBar: ProgressBar
    lateinit var loadingText: TextView
    lateinit var loadingBox: LinearLayout
    val imageInputMap: MutableList<String> = mutableListOf()

    private fun initializeApp() {
        setContentView(R.layout.activity_main) // Set the main content view
        setCameraPreview()

        // Initialize components
        DirectoryStorage.getSharedInstance().createDirectory()
        FileImageWriter.initialize(this)
        FileImageReader.initialize(this)
        ParameterConfig.initialize(this)
        AttributeHolder.initialize(this)

        // initialize camera controller
        cameraController = CameraController(this)
        cameraController.initializeCamera()

        // initialize super resolution
        concreteSuperResolution = ConcreteSuperResolution()

        // initialize image reader manager
        imageReaderManager = ImageReaderManager(this, cameraController, imageInputMap, concreteSuperResolution, loadingBox)
        imageReaderManager.initializeImageReader()

        this.textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                cameraController.openCamera(textureView)
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            }

        }

        findViewById<Button>(R.id.capture).apply {
            setOnClickListener {
                val sharedPreferences = getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE)
                val isSuperResolutionEnabled = sharedPreferences.getBoolean("super_resolution_enabled", false)

                // Set total captures based on SR toggle
                val totalCaptures = if (isSuperResolutionEnabled) 5 else 1

                val captureList = mutableListOf<CaptureRequest>()

                for (i in 0 until totalCaptures) {
                    val captureRequest = cameraController.getCameraDevice().createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                    captureRequest.addTarget(cameraController.getImageReader().surface)
                    captureList.add(captureRequest.build())
                }

                cameraController.playShutterSound()

                runOnUiThread {
                    loadingBox.visibility = View.VISIBLE
                }

                cameraController.getCameraCaptureSession().captureBurst(
                    captureList,
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            super.onCaptureCompleted(session, request, result)
                            Log.d("BurstCapture", "Capture completed")
                            // Handle the result of the capture here
                        }
                    },
                    null
                )
            }
        }

        val button: Button = findViewById(R.id.button)
        button.setOnClickListener {
            val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val popupView = inflater.inflate(R.layout.popup_menu, null)

            val popupWindow = PopupWindow(popupView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true)
            popupWindow.showAsDropDown(button, 0, 0)

            val switch1: Switch = popupView.findViewById(R.id.switch1)
            // Set initial state of switch
            val sharedPreferences = getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE)
            switch1.isChecked = sharedPreferences.getBoolean("super_resolution_enabled", false)

            // Toggle
            switch1.setOnCheckedChangeListener { _, isChecked ->
                val editor = sharedPreferences.edit()
                editor.putBoolean("super_resolution_enabled", isChecked)
                editor.apply()
            }

            // Add logic for other switches here if needed
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Initialization Failed")
        } else {
            Log.d("OpenCV", "Initialization Successful")
        }

        permissionHandler = PermissionsHandler(this)
        permissionHandler.requestPermissions { allPermissionsGranted ->
            if (allPermissionsGranted) {
                // All permissions already granted
                initializeApp()
            } else {
                // Permission was denied, handle this situation
                Log.e("Permissions", "Required permissions not granted")
            }
        }
    }

    private fun setCameraPreview() {
        setContentView(R.layout.activity_main)
        // Initialize UI elements
        this.textureView = findViewById(R.id.textureView)
        this.progressBar = findViewById(R.id.progressBar)
        this.loadingText = findViewById(R.id.loadingText)
        this.loadingBox = findViewById(R.id.loadingBox)
    }
}