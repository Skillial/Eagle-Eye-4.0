package com.wangGang.eagleEye.ui.activities

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.bumptech.glide.Glide
import com.wangGang.eagleEye.R
import com.wangGang.eagleEye.camera.CameraController
import com.wangGang.eagleEye.databinding.ActivityCameraControllerBinding
import com.wangGang.eagleEye.io.FileImageWriter
import com.wangGang.eagleEye.io.FileImageWriter.Companion.OnImageSavedListener
import com.wangGang.eagleEye.io.ImageReaderManager
import com.wangGang.eagleEye.processing.ConcreteSuperResolution
import com.wangGang.eagleEye.ui.viewmodels.CameraViewModel

class CameraControllerActivity : AppCompatActivity(), OnImageSavedListener {
    private lateinit var imageReaderManager: ImageReaderManager
    private lateinit var concreteSuperResolution: ConcreteSuperResolution

    // UI
    private lateinit var binding: ActivityCameraControllerBinding
    private lateinit var algoIndicatorLayout: LinearLayout
    private lateinit var textureView: TextureView
    private lateinit var loadingText: TextView
    private lateinit var loadingBox: LinearLayout
    private lateinit var thumbnailPreview: ImageView
    private lateinit var captureButton: ImageButton
    private lateinit var popupButton: Button
    private lateinit var switchCameraButton: ImageButton

    private var thumbnailUri: Uri? = null

    private enum class Algo {
        SR, DEHAZE
    }

    private val viewModel: CameraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraControllerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        assignViews()
        initializeCamera()
        addEventListeners()
        setBackground()
        FileImageWriter.setOnImageSavedListener(this)

        viewModel.loadingText.observe(this, Observer { text ->
            loadingText.text = text
        })

        viewModel.thumbnailUri.observe(this, Observer { uri ->
            Log.d("CameraControllerActivity", "thumbnailUri: $uri")
            uri?.let {
                try {
                    val inputStream = contentResolver.openInputStream(it)
                    if (inputStream != null) {
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        Log.d("CameraControllerActivity", "onViewCreated bitmap: $bitmap")
                        thumbnailUri = uri
                        updateThumbnail()
                    } else {
                        Log.e("CameraControllerActivity", "Failed to open input stream for URI: $uri")
                    }
                } catch (e: Exception) {
                    Log.e("CameraControllerActivity", "Error decoding bitmap from URI: $uri", e)
                }
            }
        })

        viewModel.loadingBoxVisible.observe(this, Observer { visible ->
            loadingBox.visibility = if (visible) View.VISIBLE else View.GONE

            if (visible) {
                Log.d("CameraControllerActivity", "Disabling UI interactivity")
                disableUIInteractivity()
            } else {
                Log.d("CameraControllerActivity", "Enabling UI interactivity")
                enableUIInteractivity()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        Log.d("CameraControllerActivity", "onResume")

        if (textureView.isAvailable) {
            CameraController.getInstance().setPreview(textureView)
            CameraController.getInstance().openCamera()
            imageReaderManager.setImageReaderListener()
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    CameraController.getInstance().setPreview(textureView)
                    CameraController.getInstance().openCamera()
                    imageReaderManager.setImageReaderListener()
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        }
    }

    private fun disableUIInteractivity() {
        captureButton.isEnabled = false
        popupButton.isEnabled = false
        switchCameraButton.isEnabled = false
        textureView.isEnabled = false
        thumbnailPreview.isEnabled = false
    }

    private fun enableUIInteractivity() {
        captureButton.isEnabled = true
        popupButton.isEnabled = true
        switchCameraButton.isEnabled = true
        textureView.isEnabled = true
        thumbnailPreview.isEnabled = true
    }

    override fun onStop() {
        super.onStop()
        Log.d("CameraControllerActivity", "onStop")

        CameraController.getInstance().closeCamera()
    }

    private fun assignViews() {
        algoIndicatorLayout = binding.algoIndicatorLayout
        textureView = binding.textureView
        loadingText = binding.loadingText
        loadingBox = binding.loadingBox
        thumbnailPreview = binding.thumbnailPreview
        captureButton = binding.capture
        popupButton = binding.button
        switchCameraButton = binding.switchCamera
    }

    private fun setBackground() {
        val sharedPreferences = getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE)
        val superResolutionEnabled = sharedPreferences.getBoolean("super_resolution_enabled", false)
        val dehazeEnabled = sharedPreferences.getBoolean("dehaze_enabled", false)

        val activeAlgos = mutableListOf<Algo>()

        if (superResolutionEnabled) {
            activeAlgos.add(Algo.SR)
        }

        if (dehazeEnabled) {
            activeAlgos.add(Algo.DEHAZE)
        }

        updateAlgoIndicators(activeAlgos)
    }

    private fun updateAlgoIndicators(activeAlgos: List<Algo>) {
        algoIndicatorLayout.removeAllViews()

        for (algo in activeAlgos) {
            val imageView = ImageView(this)
            imageView.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 8.dpToPx()
            }
            imageView.setImageResource(getAlgoIcon(algo))
            algoIndicatorLayout.addView(imageView)
        }
    }

    private fun getAlgoIcon(algo: Algo): Int {
        return when (algo) {
            Algo.SR -> R.drawable.ic_super_resolution
            Algo.DEHAZE -> R.drawable.ic_dehaze
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun addEventListeners() {
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
        CameraController.initialize(this, viewModel)
        concreteSuperResolution = ConcreteSuperResolution(viewModel)

        imageReaderManager = ImageReaderManager(
            this,
            CameraController.getInstance(),
            concreteSuperResolution,
            viewModel
        )
        imageReaderManager.initializeImageReader()
    }

    private fun updateThumbnail() {
        Log.d("CameraControllerActivity", "updateThumbnail uri: $thumbnailUri")
        Glide.with(this)
            .load(thumbnailUri)
            .fitCenter()
            .into(thumbnailPreview)
    }

    private fun showPhotoActivity() {
        if (thumbnailUri != null) {
            val intent = Intent(this, PhotoActivity::class.java)
            intent.putExtra("imageUri", thumbnailUri.toString())
            startActivity(intent)
        } else {
            Log.e("CameraControllerActivity", "thumbnailUri is null, cannot open PhotoActivity")
        }
    }

    fun updateLoadingText(text: String) {
        loadingText.text = text
    }

    private fun showPopupMenu() {
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView = inflater.inflate(R.layout.popup_menu, null)
        val popupWindow = PopupWindow(popupView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true)

        val switch1: Switch = popupView.findViewById(R.id.switch1)
        val switch2: Switch = popupView.findViewById(R.id.switch2)
        val sharedPreferences = getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE)
        switch1.isChecked = sharedPreferences.getBoolean("super_resolution_enabled", false)
        switch2.isChecked = sharedPreferences.getBoolean("dehaze_enabled", false)
        switch1.setOnCheckedChangeListener { _, isChecked ->
            val editor = sharedPreferences.edit()
            editor.putBoolean("super_resolution_enabled", isChecked)
            if (isChecked) {
                editor.putBoolean("super_resolution_enabled", true)
                editor.putBoolean("dehaze_enabled", false)
                switch2.isChecked = false
                updateAlgoIndicators(listOf(Algo.SR))
            } else {
                editor.putBoolean("super_resolution_enabled", false)
                updateAlgoIndicators(emptyList())
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
                updateAlgoIndicators(listOf(Algo.DEHAZE))
            } else {
                editor.putBoolean("dehaze_enabled", false)
                updateAlgoIndicators(emptyList())
            }
            editor.apply()
        }
        popupWindow.showAsDropDown(findViewById(R.id.button), 0, 0)
    }

    override fun onImageSaved(uri: Uri) {
        Log.d("CameraControllerActivity", "onImageSaved: $uri")
        viewModel.updateThumbnailUri(uri)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("CameraControllerActivity", "onDestroy")
    }
}