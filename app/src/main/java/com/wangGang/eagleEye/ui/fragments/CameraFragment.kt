package com.wangGang.eagleEye.ui.fragments

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.wangGang.eagleEye.R
import com.wangGang.eagleEye.camera.CameraController
import com.wangGang.eagleEye.io.ImageReaderManager
import com.wangGang.eagleEye.processing.ConcreteSuperResolution
import com.google.android.material.floatingactionbutton.FloatingActionButton

class CameraFragment : Fragment() {
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

        assignViews(view)
        initializeCamera()
        addEventListeners(view)

        return view
    }

    private fun assignViews(view: View) {
        textureView = view.findViewById(R.id.textureView)
        progressBar = view.findViewById(R.id.progressBar)
        loadingText = view.findViewById(R.id.loadingText)
        loadingBox = view.findViewById(R.id.loadingBox)
    }

    private fun addEventListeners(view: View) {
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                CameraController.getInstance().openCamera(textureView)
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }

        view.findViewById<ImageView>(R.id.capture)?.apply {
            setOnClickListener {
                CameraController.getInstance().captureImage(loadingBox)
            }
        }

        view.findViewById<Button>(R.id.button)?.apply {
            setOnClickListener {
                showPopupMenu()
            }
        }

        view.findViewById<FloatingActionButton>(R.id.switchCamera)?.apply {
            setOnClickListener {
                CameraController.getInstance().switchCamera(textureView)
            }
        }
    }

    private fun initializeCamera() {
        CameraController.initialize(requireContext())
        concreteSuperResolution = ConcreteSuperResolution()

        imageReaderManager = ImageReaderManager(requireContext(), CameraController.getInstance(), imageInputMap, concreteSuperResolution, loadingBox)
        imageReaderManager.initializeImageReader()
    }

    private fun showPopupMenu() {
        val inflater = requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.popup_menu, null)
        val popupWindow = PopupWindow(popupView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true)

        val switch1: Switch = popupView.findViewById(R.id.switch1)
        val switch2: Switch = popupView.findViewById(R.id.switch2)
        val sharedPreferences = requireContext().getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE)
        switch1.isChecked = sharedPreferences.getBoolean("super_resolution_enabled", false)
        switch2.isChecked = sharedPreferences.getBoolean("dehaze_enabled", false)
        switch1.setOnCheckedChangeListener { _, isChecked ->
            val editor = sharedPreferences.edit()
            editor.putBoolean("super_resolution_enabled", isChecked)
            if (isChecked) {
                editor.putBoolean("super_resolution_enabled", true)
                editor.putBoolean("dehaze_enabled", false)
                switch2.isChecked = false
            } else {
                editor.putBoolean("super_resolution_enabled", false)
            }
            editor.apply()
        }
        switch2.setOnCheckedChangeListener { _, isChecked ->
            val editor = sharedPreferences.edit()
            editor.putBoolean("dehaze_enabled", isChecked)
            if (isChecked) {
                editor.putBoolean("dehaze_enabled", true)
                editor.putBoolean("super_resolution_enabled", false)
                switch1.isChecked = false
            } else {
                editor.putBoolean("dehaze_enabled", false)
            }
            editor.apply()
        }
        popupWindow.showAsDropDown(view?.findViewById(R.id.button), 0, 0)
    }
}