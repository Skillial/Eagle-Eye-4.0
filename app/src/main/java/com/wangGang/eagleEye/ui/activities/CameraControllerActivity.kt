package com.wangGang.eagleEye.ui.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Observer
import com.bumptech.glide.Glide
import com.wangGang.eagleEye.R
import com.wangGang.eagleEye.camera.CameraController
import com.wangGang.eagleEye.constants.ImageEnhancementType
import com.wangGang.eagleEye.constants.ParameterConfig
import com.wangGang.eagleEye.databinding.ActivityCameraControllerBinding
import com.wangGang.eagleEye.databinding.PopupMenuBinding
import com.wangGang.eagleEye.io.FileImageWriter
import com.wangGang.eagleEye.io.FileImageWriter.Companion.OnImageSavedListener
import com.wangGang.eagleEye.io.ImageReaderManager
import com.wangGang.eagleEye.processing.ConcreteSuperResolution
import com.wangGang.eagleEye.ui.utils.ProgressManager
import com.wangGang.eagleEye.ui.viewmodels.CameraViewModel

class CameraControllerActivity : AppCompatActivity(), OnImageSavedListener {
    private lateinit var imageReaderManager: ImageReaderManager
    private lateinit var concreteSuperResolution: ConcreteSuperResolution

    // UI
    private lateinit var activityCameraControllerBinding: ActivityCameraControllerBinding
    private lateinit var activityPopupMenuBinding: PopupMenuBinding
    private lateinit var algoIndicatorLayout: LinearLayout
    private lateinit var textureView: TextureView
    private lateinit var loadingText: TextView
    private lateinit var loadingBox: LinearLayout
    private lateinit var thumbnailPreview: ImageView
    private lateinit var captureButton: ImageButton
    private lateinit var popupButton: Button
    private lateinit var switchCameraButton: ImageButton
    private lateinit var progressManager: ProgressManager
    private lateinit var progressBar: ProgressBar

    private var thumbnailUri: Uri? = null

    private val viewModel: CameraViewModel by viewModels()

    /* ===== Lifecycle Methods ===== */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityCameraControllerBinding = ActivityCameraControllerBinding.inflate(layoutInflater)
        setContentView(activityCameraControllerBinding.root)

        progressManager = ProgressManager.getInstance()

        assignViews()
        initializeCamera()
        addEventListeners()
        setBackground()
        setupObservers()
        updateScreenBorder()
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

    override fun onStop() {
        super.onStop()
        Log.d("CameraControllerActivity", "onStop")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("CameraControllerActivity", "onDestroy")
        CameraController.getInstance().closeCamera()
        ProgressManager.destroyInstance() // Cleanup
    }

    override fun onImageSaved(uri: Uri) {
        Log.d("CameraControllerActivity", "onImageSaved: $uri")
        viewModel.updateThumbnailUri(uri)
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

    /* ===== UI Methods ===== */
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

    private fun getAlgoIcon(algo: ImageEnhancementType): Int {
        return when (algo) {
            ImageEnhancementType.SUPER_RESOLUTION -> R.drawable.ic_super_resolution
            ImageEnhancementType.DEHAZE -> R.drawable.ic_dehaze
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun addEventListeners() {
        captureButton.setOnClickListener {
            resetProgressBarBasedOnImageEnhancementType()
            CameraController.getInstance().captureImage()
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

        FileImageWriter.setOnImageSavedListener(this)
    }

    private fun assignViews() {
        algoIndicatorLayout = activityCameraControllerBinding.algoIndicatorLayout
        textureView = activityCameraControllerBinding.textureView
        loadingText = activityCameraControllerBinding.loadingText
        loadingBox = activityCameraControllerBinding.loadingBox
        thumbnailPreview = activityCameraControllerBinding.thumbnailPreview
        captureButton = activityCameraControllerBinding.capture
        popupButton = activityCameraControllerBinding.button
        switchCameraButton = activityCameraControllerBinding.switchCamera
        progressBar = activityCameraControllerBinding.progressBar
    }

    private fun setBackground() {
        val sharedPreferences = getSharedPreferences("MySharedPrefs", Context.MODE_PRIVATE)
        val superResolutionEnabled = sharedPreferences.getBoolean("super_resolution_enabled", false)
        val dehazeEnabled = sharedPreferences.getBoolean("dehaze_enabled", false)

        val activeImageEnhancementTechniques = mutableListOf<ImageEnhancementType>()

        if (superResolutionEnabled) {
            activeImageEnhancementTechniques.add(ImageEnhancementType.SUPER_RESOLUTION)
        }

        if (dehazeEnabled) {
            activeImageEnhancementTechniques.add(ImageEnhancementType.DEHAZE)
        }

        updateAlgoIndicators(activeImageEnhancementTechniques)
    }

    private fun updateScreenBorder() {
        val rootView = activityCameraControllerBinding.root

        val srEnabled = ParameterConfig.isSuperResolutionEnabled()
        val dehazeEnabled = ParameterConfig.isDehazeEnabled()

        // TODO: update this once chaining is supported
        val borderColor = when {
            /*srEnabled && dehazeEnabled -> {
                ColorUtils.blendARGB(Color.GREEN, Color.YELLOW, 0.5f)
            }*/
            srEnabled -> Color.GREEN
            dehazeEnabled -> Color.YELLOW
            else -> Color.BLACK
        }

        val borderDrawable = GradientDrawable().apply {
            setColor(Color.BLACK)
            setStroke(4.dpToPx(), borderColor)
        }
        rootView.background = borderDrawable
    }

    private fun updateAlgoIndicators(activeImageEnhancementTechniques: List<ImageEnhancementType>) {
        algoIndicatorLayout.removeAllViews()

        for (algo in activeImageEnhancementTechniques) {
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

    @SuppressLint("InflateParams")
    private fun showPopupMenu() {
        val popupView = LayoutInflater.from(this).inflate(R.layout.popup_menu, null)
        val popupWindow = PopupWindow(popupView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true)

        // setup views
        activityPopupMenuBinding = PopupMenuBinding.bind(popupView)
        val switch1: SwitchCompat = activityPopupMenuBinding.switchSuperResolution
        val switch2: SwitchCompat = activityPopupMenuBinding.switchDehaze

        // Use ParameterConfig to get preferences
        switch1.isChecked = ParameterConfig.isSuperResolutionEnabled()
        switch2.isChecked = ParameterConfig.isDehazeEnabled()

        switch1.setOnCheckedChangeListener { _, isChecked ->
            ParameterConfig.setSuperResolutionEnabled(isChecked)
            if (isChecked) {
                ParameterConfig.setDehazeEnabled(false)
                switch2.isChecked = false
                updateAlgoIndicators(listOf(ImageEnhancementType.SUPER_RESOLUTION))
            } else {
                updateAlgoIndicators(emptyList())
            }

            updateScreenBorder()
        }

        switch2.setOnCheckedChangeListener { _, isChecked ->
            ParameterConfig.setDehazeEnabled(isChecked)
            if (isChecked) {
                ParameterConfig.setSuperResolutionEnabled(false)
                switch1.isChecked = false
                updateAlgoIndicators(listOf(ImageEnhancementType.DEHAZE))
            } else {
                updateAlgoIndicators(emptyList())
            }

            updateScreenBorder()
        }

        popupWindow.showAsDropDown(findViewById(R.id.button), 0, 0)
    }

    // Other methods
    private fun resetProgressBarBasedOnImageEnhancementType() {
        if (ParameterConfig.isSuperResolutionEnabled()) {
            progressManager.resetProgress(ImageEnhancementType.SUPER_RESOLUTION)
        } else if (ParameterConfig.isDehazeEnabled()) {
            progressManager.resetProgress(ImageEnhancementType.DEHAZE)
        } else {
            progressManager.resetProgress()
        }
    }

    private fun setupObservers() {
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
                        Log.e(
                            "CameraControllerActivity",
                            "Failed to open input stream for URI: $uri"
                        )
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

        progressManager.progress.observe(this, Observer { progress ->
            Log.d("ProgressBar", "New Progress: $progress")
            if (progress > 0) {
                progressBar.visibility = View.VISIBLE
                progressBar.progress = progress
            }
        })
    }
}