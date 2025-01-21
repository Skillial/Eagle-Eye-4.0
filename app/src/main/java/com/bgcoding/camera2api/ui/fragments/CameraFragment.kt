package com.bgcoding.camera2api.ui.fragments

import androidx.fragment.app.Fragment
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
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContentProviderCompat.requireContext
import com.bgcoding.camera2api.R
import com.bgcoding.camera2api.camera.CameraController
import com.bgcoding.camera2api.io.ImageReaderManager
import com.bgcoding.camera2api.processing.ConcreteSuperResolution

class CameraFragment : Fragment() {
    private lateinit var cameraController: CameraController
    private lateinit var imageReaderManager: ImageReaderManager
    private lateinit var concreteSuperResolution: ConcreteSuperResolution

    private lateinit var textureView: TextureView
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    private lateinit var loadingBox: LinearLayout
    private val imageInputMap: MutableList<String> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_camera, container, false)
        textureView = view.findViewById(R.id.textureView)
        progressBar = view.findViewById(R.id.progressBar)
        loadingText = view.findViewById(R.id.loadingText)
        loadingBox = view.findViewById(R.id.loadingBox)

        initializeCamera()

        view.findViewById<Button>(R.id.capture)?.apply {
            setOnClickListener {
                captureImages()
            }
        }

        view.findViewById<Button>(R.id.button)?.apply {
            setOnClickListener {
                showPopupMenu()
            }
        }

        return view
    }

    private fun initializeCamera() {
        cameraController = CameraController(requireContext())
        cameraController.initializeCamera()

        concreteSuperResolution = ConcreteSuperResolution()

        imageReaderManager = ImageReaderManager(requireContext(), cameraController, imageInputMap, concreteSuperResolution, loadingBox)
        imageReaderManager.initializeImageReader()

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                cameraController.openCamera(textureView)
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    private fun captureImages() {
        val sharedPreferences = requireContext().getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE)
        val isSuperResolutionEnabled = sharedPreferences.getBoolean("super_resolution_enabled", false)
        val totalCaptures = if (isSuperResolutionEnabled) 5 else 1
        val captureList = mutableListOf<CaptureRequest>()

        for (i in 0 until totalCaptures) {
            val captureRequest = cameraController.getCameraDevice().createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureRequest.addTarget(cameraController.getImageReader().surface)
            captureList.add(captureRequest.build())
        }

        cameraController.playShutterSound()

        requireActivity().runOnUiThread {
            loadingBox.visibility = View.VISIBLE
        }

        cameraController.getCameraCaptureSession().captureBurst(
            captureList,
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    super.onCaptureCompleted(session, request, result)
                    Log.d("BurstCapture", "Capture completed")
                }
            },
            null
        )
    }

    private fun showPopupMenu() {
        val inflater = requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.popup_menu, null)
        val popupWindow = PopupWindow(popupView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true)
        popupWindow.showAsDropDown(view?.findViewById(R.id.button), 0, 0)

        val switch1: Switch = popupView.findViewById(R.id.switch1)
        val sharedPreferences = requireContext().getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE)
        switch1.isChecked = sharedPreferences.getBoolean("super_resolution_enabled", false)

        switch1.setOnCheckedChangeListener { _, isChecked ->
            val editor = sharedPreferences.edit()
            editor.putBoolean("super_resolution_enabled", isChecked)
            editor.apply()
        }
    }
}