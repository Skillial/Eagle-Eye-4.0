package com.wangGang.eagleEye.ui.fragments

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.wangGang.eagleEye.R
import com.wangGang.eagleEye.camera.CameraController
import com.wangGang.eagleEye.io.ImageReaderManager
import com.wangGang.eagleEye.processing.ConcreteSuperResolution
import com.wangGang.eagleEye.io.FileImageWriter
import com.wangGang.eagleEye.io.FileImageWriter.Companion.OnImageSavedListener
import com.wangGang.eagleEye.ui.activities.PhotoActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class CameraFragment : Fragment(), OnImageSavedListener {
    private lateinit var imageReaderManager: ImageReaderManager
    private lateinit var concreteSuperResolution: ConcreteSuperResolution

    private lateinit var textureView: TextureView
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    private lateinit var loadingBox: LinearLayout
    private lateinit var thumbnailPreview: ImageView
    private lateinit var captureButton: ImageButton
    private lateinit var popupButton: Button
    private lateinit var switchCameraButton: FloatingActionButton
    private lateinit var constraintLayout: ConstraintLayout

    private val imageInputMap: MutableList<String> = mutableListOf()
    private var thumbnailUri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_camera, container, false)

        assignViews(view)
        initializeCamera()
        addEventListeners(view)
        setBackground()
        // used to update the thumbnail preview
        FileImageWriter.setOnImageSavedListener(this)

        return view
    }

    override fun onResume() {
        super.onResume()
        Log.d("CameraFragment", "onResume")

        if (textureView.isAvailable) {
            CameraController.getInstance().openCamera(textureView)
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    CameraController.getInstance().openCamera(textureView)
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d("CameraFragment", "onStop")

        CameraController.getInstance().closeCamera()
    }

    private fun assignViews(view: View) {
        constraintLayout = view.findViewById(R.id.constraintLayout)
        textureView = view.findViewById(R.id.textureView)
        progressBar = view.findViewById(R.id.progressBar)
        loadingText = view.findViewById(R.id.loadingText)
        loadingBox = view.findViewById(R.id.loadingBox)
        thumbnailPreview = view.findViewById(R.id.thumbnailPreview)
        captureButton = view.findViewById(R.id.capture)
        popupButton = view.findViewById(R.id.button)
        switchCameraButton = view.findViewById(R.id.switchCamera)
    }

    private fun setBackground() {
        // get preferences of super_resolution_enabled and dehaze_enabled
        val sharedPreferences = requireContext().getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE)
        val superResolutionEnabled = sharedPreferences.getBoolean("super_resolution_enabled", false)
        val dehazeEnabled = sharedPreferences.getBoolean("dehaze_enabled", false)

        if (superResolutionEnabled) {
//            constraintLayout.setBackgroundColor(Color.GREEN);
        } else if (dehazeEnabled) {
//            constraintLayout.setBackgroundColor(Color.BLUE);
        } else {
//            constraintLayout.setBackgroundColor(Color.BLACK);
        }
    }

    private fun addEventListeners(view: View) {
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
            showPhotoActivity()
        }
    }

    private fun initializeCamera() {
        CameraController.initialize(requireContext())
        concreteSuperResolution = ConcreteSuperResolution()

        imageReaderManager = ImageReaderManager(
            requireContext(),
            CameraController.getInstance(),
            imageInputMap,
            concreteSuperResolution,
            loadingBox
        )
        imageReaderManager.initializeImageReader()
    }

    private fun updateThumbnail(bitmap: Bitmap) {
        activity?.runOnUiThread {
            thumbnailPreview.setImageBitmap(bitmap)
        }
    }

    private fun showPhotoActivity() {
        if (thumbnailUri != null) {
            val intent = Intent(requireContext(), PhotoActivity::class.java)
            intent.putExtra("imageUri", thumbnailUri.toString()) // Use stored URI
            startActivity(intent)
        } else {
            Log.e("CameraFragment", "thumbnailUri is null, cannot open PhotoActivity")
        }
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
//                constraintLayout.setBackgroundColor(Color.GREEN);
            } else {
                editor.putBoolean("super_resolution_enabled", false)
//                constraintLayout.setBackgroundColor(Color.BLACK);
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
//                constraintLayout.setBackgroundColor(Color.BLUE);
            } else {
                editor.putBoolean("dehaze_enabled", false)
//                constraintLayout.setBackgroundColor(Color.BLACK);
            }
            editor.apply()
        }
        popupWindow.showAsDropDown(view?.findViewById(R.id.button), 0, 0)
    }

    override fun onImageSaved(uri: Uri) {
        Log.d("CameraFragment", "onImageSaved: $uri")
        val bitmap =
            BitmapFactory.decodeStream(requireContext().contentResolver.openInputStream(uri))
        updateThumbnail(bitmap)
        thumbnailUri = uri
    }
}