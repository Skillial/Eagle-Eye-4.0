package com.wangGang.eagleEye.ui.activities

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.TextureView
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.wangGang.eagleEye.R
import com.wangGang.eagleEye.camera.CameraController
import com.wangGang.eagleEye.constants.ImageEnhancementType
import com.wangGang.eagleEye.constants.ParameterConfig
import com.wangGang.eagleEye.databinding.ActivityCameraControllerBinding
import com.wangGang.eagleEye.io.FileImageReader
import com.wangGang.eagleEye.io.FileImageWriter
import com.wangGang.eagleEye.io.FileImageWriter.Companion.OnImageSavedListener
import com.wangGang.eagleEye.io.ImageReaderManager
import com.wangGang.eagleEye.processing.ConcreteSuperResolution
import com.wangGang.eagleEye.ui.utils.ProgressManager
import com.wangGang.eagleEye.ui.viewmodels.CameraViewModel
import com.wangGang.eagleEye.ui.views.GridOverlayView
import androidx.core.view.isVisible

class CameraControllerActivity : AppCompatActivity(), OnImageSavedListener {

    companion object {
        private var instance: CameraControllerActivity? = null

        fun getContext(): Context? {
            return instance?.applicationContext
        }

        fun launchBeforeAndAfterActivity(uriList: List<Uri>) {
            instance?.launchBeforeAndAfterActivity(uriList)
        }
    }

    private lateinit var imageReaderManager: ImageReaderManager
    private lateinit var concreteSuperResolution: ConcreteSuperResolution

    // UI
    private lateinit var activityCameraControllerBinding: ActivityCameraControllerBinding
    private lateinit var gridOverLayView: GridOverlayView
    private lateinit var algoIndicatorLayout: LinearLayout
    private lateinit var textureView: TextureView
    private lateinit var loadingText: TextView
    private lateinit var loadingBox: LinearLayout
    private lateinit var thumbnailPreview: ImageView
    private lateinit var captureButton: ImageButton
    private lateinit var settingsButton: Button
    private lateinit var switchCameraButton: ImageButton
    private lateinit var progressManager: ProgressManager
    private lateinit var progressBar: ProgressBar

    private var thumbnailUri: Uri? = null
    private val viewModel: CameraViewModel by viewModels()

    private var doneSetup: Boolean = false
    private var idleAnimator: ObjectAnimator? = null
    private val idleDelayMillis = 3000L

    /* ===== Lifecycle Methods ===== */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        activityCameraControllerBinding = ActivityCameraControllerBinding.inflate(layoutInflater)
        setContentView(activityCameraControllerBinding.root)

        assignViews()
        initializeCamera()
        addEventListeners()
        setupObservers()
        setupThumbnail()

        doneSetup = true
    }

    private fun setupThumbnail() {
        thumbnailUri = FileImageReader.getInstance()?.getAfterUriDefaultResultsFolder()
        updateThumbnail()
    }

    private fun setGridOverlay() {
        gridOverLayView.visibility = if (ParameterConfig.isGridOverlayEnabled()) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        Log.d("CameraControllerActivity", "onResume")

        setGridOverlay()
        setBackground()
        updateScreenBorder()

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
        Log.d("CameraControllerActivity", "Closing camera")
        CameraController.getInstance().closeCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("CameraControllerActivity", "onDestroy")
        ProgressManager.destroyInstance() // Cleanup
    }

    override fun onImageSaved(uri: Uri) {
        Log.d("CameraControllerActivity", "onImageSaved: $uri")
        viewModel.updateThumbnailUri(uri)
    }

    private fun initializeCamera() {
        CameraController.initialize(this, viewModel)
        concreteSuperResolution = ConcreteSuperResolution(viewModel)

        // Initialize Progress Manager
        // May introduce tight coupling, but it's necessary for now
        initializeProgressManager()

        imageReaderManager = ImageReaderManager(
            this,
            CameraController.getInstance(),
            concreteSuperResolution,
            viewModel
        )
        imageReaderManager.initializeImageReader()
    }

    private fun initializeProgressManager() {
        ProgressManager.initialize(viewModel)
        progressManager = ProgressManager.getInstance()
    }

    /* ===== UI Methods ===== */
    private fun disableUIInteractivity() {
        captureButton.isEnabled = false
        settingsButton.isEnabled = false
        switchCameraButton.isEnabled = false
        textureView.isEnabled = false
        thumbnailPreview.isEnabled = false
    }

    private fun enableUIInteractivity() {
        captureButton.isEnabled = true
        settingsButton.isEnabled = true
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
            ProgressManager.getInstance().resetProgress()
            CameraController.getInstance().captureImage()
            Log.d("CameraControllerActivity", "Capture button clicked")
        }

        switchCameraButton.setOnClickListener {
            CameraController.getInstance().switchCamera(textureView)
        }

        // TODO: fix thumbnail functionality
        thumbnailPreview.setOnClickListener {
            // if normal image showPhotoActivity
            if (ParameterConfig.isDehazeEnabled() || ParameterConfig.isSuperResolutionEnabled()) {
                val safeUriList = FileImageReader.getInstance()
                    ?.let { reader ->
                        listOfNotNull(reader.getBeforeUriDefaultResultsFolder(), reader.getAfterUriDefaultResultsFolder())
                    } ?: emptyList()

                launchBeforeAndAfterActivity(safeUriList)
            } else {
                // normal image
                val safeUriList = listOfNotNull(thumbnailUri)
                launchBeforeAndAfterActivity(safeUriList)
            }
        }

        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
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
        settingsButton = activityCameraControllerBinding.btnSettings
        switchCameraButton = activityCameraControllerBinding.switchCamera
        progressBar = activityCameraControllerBinding.progressBar
        gridOverLayView = activityCameraControllerBinding.gridOverlayView
    }

    private fun setBackground() {
        val superResolutionEnabled = ParameterConfig.isSuperResolutionEnabled()
        val dehazeEnabled = ParameterConfig.isDehazeEnabled()

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
            setStroke(2.dpToPx(), borderColor)
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
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .fitCenter()
            .into(thumbnailPreview)
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
            if (visible) {
                loadingBox.visibility = View.VISIBLE
                progressBar.visibility = View.VISIBLE
            } else {
                loadingBox.visibility = View.GONE
                progressBar.visibility = View.GONE
            }

            if (visible) {
                Log.d("CameraControllerActivity", "Disabling UI interactivity")
                disableUIInteractivity()
            } else {
                Log.d("CameraControllerActivity", "Enabling UI interactivity")
                enableUIInteractivity()
            }
        })

        progressManager.progress.observe(this, Observer { progress ->
            if (loadingBox.isVisible) {
                progressBar.visibility = View.VISIBLE
                progressBar.progress = progress
                idleAnimator?.cancel() // Cancel any idle animation if active
                animateProgressUpdate(progress)
            }
        })

    }

    private fun animateProgressUpdate(newProgress: Int) {
        // Cancel and Reset
        idleAnimator?.cancel()
        progressBar.alpha = 1f

        // For smoothing the progress update
        val animator = ObjectAnimator.ofInt(progressBar, "progress", progressBar.progress, newProgress).apply {
            duration = 1000
            interpolator = AccelerateDecelerateInterpolator()
        }
        animator.start()

        // If the progress hasn't updated by "idleDelayMillis", start idle animation
        progressBar.postDelayed({
            if (progressBar.progress == newProgress) {
                startAlphaIdleAnimation()
            }
        }, idleDelayMillis)
    }

    private fun startAlphaIdleAnimation() {
        idleAnimator = ObjectAnimator.ofFloat(progressBar, "alpha", 1f, 0.5f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        idleAnimator?.start()
    }

    private fun launchBeforeAndAfterActivity(uriList: List<Uri>) {
        val intent = Intent(this, BeforeAndAfterPreviewActivity::class.java)

        // convert list to arraylist
        val uriArrayList = ArrayList<Uri>()
        uriArrayList .addAll(uriList)
        intent.putParcelableArrayListExtra("uriListKey", uriArrayList)
        startActivity(intent)
    }
}