package com.bgcoding.camera2api.ui.fragments

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.util.Log
import android.view.Gravity
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
import com.bgcoding.camera2api.R
import com.bgcoding.camera2api.camera.CameraController
import com.bgcoding.camera2api.io.FileImageWriter
import com.bgcoding.camera2api.io.FileImageWriter.Companion.OnImageSavedListener
import com.bgcoding.camera2api.io.ImageReaderManager
import com.bgcoding.camera2api.processing.ConcreteSuperResolution
import com.google.android.material.floatingactionbutton.FloatingActionButton

class CameraFragment : Fragment(), OnImageSavedListener {
    private lateinit var imageReaderManager: ImageReaderManager
    private lateinit var concreteSuperResolution: ConcreteSuperResolution

    private lateinit var textureView: TextureView
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    private lateinit var loadingBox: LinearLayout
    private lateinit var thumbnailPreview: ImageView
    private lateinit var captureButton: ImageView
    private lateinit var popupButton: Button
    private lateinit var switchCameraButton: FloatingActionButton
    private val imageInputMap: MutableList<String> = mutableListOf()

    override fun onImageSaved(filePath: String) {
        Log.d("CameraFragment", "onImageSaved: $filePath")
        updateThumbnail(BitmapFactory.decodeFile(filePath))
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_camera, container, false)

        assignViews(view)
        initializeCamera()
        addEventListeners(view)

        // used to update the thumbnail preview
        FileImageWriter.setOnImageSavedListener(this)

        return view
    }

    private fun assignViews(view: View) {
        textureView = view.findViewById(R.id.textureView)
        progressBar = view.findViewById(R.id.progressBar)
        loadingText = view.findViewById(R.id.loadingText)
        loadingBox = view.findViewById(R.id.loadingBox)
        thumbnailPreview = view.findViewById(R.id.thumbnailPreview)
        captureButton = view.findViewById(R.id.capture)
        popupButton = view.findViewById(R.id.button)
        switchCameraButton = view.findViewById(R.id.switchCamera)
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

        captureButton.setOnClickListener {
            CameraController.getInstance().captureImage(loadingBox)
        }

        popupButton.setOnClickListener {
            showPopupMenu()
        }

        switchCameraButton.setOnClickListener {
            CameraController.getInstance().switchCamera(textureView)
        }

        thumbnailPreview.setOnClickListener {
            showPhotoPopup()
        }
    }

    private fun initializeCamera() {
        CameraController.initialize(requireContext())
        concreteSuperResolution = ConcreteSuperResolution()

        imageReaderManager = ImageReaderManager(requireContext(), CameraController.getInstance(), imageInputMap, concreteSuperResolution, loadingBox)
        imageReaderManager.initializeImageReader()
    }

    private fun updateThumbnail(bitmap: Bitmap) {
        activity?.runOnUiThread {
            thumbnailPreview.setImageBitmap(bitmap)
        }
    }

    private fun showPhotoPopup() {
        val inflater = requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.popup_photo, null)
        val popupWindow = PopupWindow(popupView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true)

        val photoView: ImageView? = popupView.findViewById(R.id.photoView)
        photoView?.setImageDrawable(thumbnailPreview.drawable)

        popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0)
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