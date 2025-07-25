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
import androidx.core.graphics.ColorUtils
import android.util.Log
import android.view.MotionEvent
import androidx.appcompat.widget.Toolbar
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
import com.wangGang.eagleEye.processing.commands.Dehaze
import com.wangGang.eagleEye.processing.commands.Denoising
import com.wangGang.eagleEye.processing.commands.ShadowRemoval
import com.wangGang.eagleEye.processing.commands.SuperResolution
import com.wangGang.gallery.getLatestImageUri
import android.view.ScaleGestureDetector
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import androidx.core.view.isGone
import com.wangGang.eagleEye.ui.utils.CustomToast


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
    private lateinit var settingsButton: ImageButton
    private lateinit var switchCameraButton: ImageButton
    private lateinit var progressManager: ProgressManager
    private lateinit var progressBar: ProgressBar
    private lateinit var countdownText: TextView
    private lateinit var zoomLevelText: TextView
    private val zoomTextHandler = Handler(Looper.getMainLooper())
    private val hideZoomTextRunnable = Runnable { zoomLevelText.visibility = View.GONE }
    private lateinit var topToolbar: Toolbar
    private lateinit var btnFlash: ImageButton
    private lateinit var btnTimer: ImageButton
    private lateinit var btnGrid: ImageButton
    private lateinit var defaultToolbarContent: LinearLayout
    private lateinit var timerOptionsContainer: FrameLayout
    private lateinit var btnTimerOff: ImageButton
    private lateinit var btnTimer3s: ImageButton
    private lateinit var btnTimer5s: ImageButton
    private lateinit var btnTimer10s: ImageButton

    private var thumbnailUri: Uri? = null
    private val viewModel: CameraViewModel by viewModels()

    private lateinit var scaleGestureDetector: ScaleGestureDetector

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
        setSupportActionBar(topToolbar)
        initializeCamera()
        addEventListeners()
        setupObservers()

        scaleGestureDetector = ScaleGestureDetector(this, ScaleListener())

        doneSetup = true
    }

    private fun setupThumbnail() {
        thumbnailUri = getLatestImageUri(this)
        updateThumbnail()
    }

    private fun setGridOverlay() {
        val isGridEnabled = ParameterConfig.isGridOverlayEnabled()
        Log.d("CameraControllerActivity", "setGridOverlay: isGridEnabled = $isGridEnabled")
        gridOverLayView.visibility = if (isGridEnabled) View.VISIBLE else View.GONE
        gridOverLayView.invalidate()
        gridOverLayView.requestLayout()
    }

    override fun onResume() {
        super.onResume()
        Log.d("CameraControllerActivity", "onResume")

        setGridOverlay()
        setBackground()
        setupThumbnail()
        updateScreenBorder()
        updateFlashButtonIcon()
        updateTimerButtonIcon()
        updateGridButtonIcon()

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
        CameraController.getInstance().shutdownBackgroundThread()
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

        // Reset progress bar and loading text
        progressBar.progress = 0
        viewModel.updateLoadingText("")

        setupThumbnail()
    }

    private fun getAlgoIcon(algo: ImageEnhancementType): Int {
        return when (algo) {
            ImageEnhancementType.SUPER_RESOLUTION -> R.drawable.ic_super_resolution
            ImageEnhancementType.DEHAZE -> R.drawable.ic_dehaze
            ImageEnhancementType.SHADOW_REMOVAL -> R.drawable.ic_shadow_removal
            ImageEnhancementType.DENOISING -> R.drawable.ic_denoising
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun updateFlashButtonIcon() {
        if (ParameterConfig.isFlashEnabled()) {
            btnFlash.setImageResource(R.drawable.ic_flash_on)
        } else {
            btnFlash.setImageResource(R.drawable.ic_flash_off)
        }
    }

    private fun updateGridButtonIcon() {
        if (ParameterConfig.isGridOverlayEnabled()) {
            btnGrid.setImageResource(R.drawable.ic_grid_on)
        } else {
            btnGrid.setImageResource(R.drawable.ic_grid_off)
        }
    }

    private fun addEventListeners() {
        captureButton.setOnClickListener {
            val timerDuration = ParameterConfig.getTimerDuration()
            if (timerDuration > 0) {
                startTimer(timerDuration)
            } else {
                ProgressManager.getInstance().resetProgress()
                CameraController.getInstance().captureImage()
                Log.d("CameraControllerActivity", "Capture button clicked")
            }
        }

        switchCameraButton.setOnClickListener {
            CameraController.getInstance().switchCamera(textureView)
        }

        thumbnailPreview.setOnClickListener {
            // if normal image showPhotoActivity
            if (ParameterConfig.isDehazeEnabled() || ParameterConfig.isSuperResolutionEnabled()) {
                val safeUriList = FileImageReader.getInstance()
                    ?.let { reader ->
                        listOfNotNull(reader.getBeforeUriDefaultResultsFolder(), reader.getAfterUriDefaultResultsFolder())
                    } ?: emptyList()
                startActivity(
                    Intent(this, com.wangGang.gallery.MainActivity::class.java)
                )
//                launchBeforeAndAfterActivity(safeUriList)
            } else {
                // normal image
                val safeUriList = listOfNotNull(thumbnailUri)
                startActivity(
                    Intent(this, com.wangGang.gallery.MainActivity::class.java)
                )
//                launchBeforeAndAfterActivity(safeUriList)
            }
        }

        settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        btnFlash.setOnClickListener {
            val isFlashEnabled = ParameterConfig.isFlashEnabled()
            ParameterConfig.setFlashEnabled(!isFlashEnabled)
            updateFlashButtonIcon()
            CameraController.getInstance().updateFlashMode()
            val flashStatus = if (ParameterConfig.isFlashEnabled()) "On" else "Off"
            CustomToast.show(this, "Flash: $flashStatus", yOffset = 56.dpToPx())
        }

        btnTimer.setOnClickListener {
            if (timerOptionsContainer.isGone) {
                defaultToolbarContent.visibility = View.GONE
                timerOptionsContainer.visibility = View.VISIBLE
            } else {
                defaultToolbarContent.visibility = View.VISIBLE
                timerOptionsContainer.visibility = View.GONE
            }
        }

        btnGrid.setOnClickListener {
            val isGridEnabled = ParameterConfig.isGridOverlayEnabled()
            ParameterConfig.setGridOverlayEnabled(!isGridEnabled)
            updateGridButtonIcon()
            setGridOverlay()
            val gridStatus = if (ParameterConfig.isGridOverlayEnabled()) "On" else "Off"
            CustomToast.show(this, "Grid: $gridStatus", yOffset = 56.dpToPx())
        }

        FileImageWriter.setOnImageSavedListener(this)

        btnTimerOff.setOnClickListener {
            ParameterConfig.setTimerDuration(0)
            updateTimerButtonIcon()
            defaultToolbarContent.visibility = View.VISIBLE
            timerOptionsContainer.visibility = View.GONE
            CustomToast.show(this, "Timer: Off", yOffset = 56.dpToPx())
        }

        btnTimer3s.setOnClickListener {
            ParameterConfig.setTimerDuration(3)
            updateTimerButtonIcon()
            defaultToolbarContent.visibility = View.VISIBLE
            timerOptionsContainer.visibility = View.GONE
            CustomToast.show(this, "Timer: 3s", yOffset = 56.dpToPx())
        }

        btnTimer5s.setOnClickListener {
            ParameterConfig.setTimerDuration(5)
            updateTimerButtonIcon()
            defaultToolbarContent.visibility = View.VISIBLE
            timerOptionsContainer.visibility = View.GONE
            CustomToast.show(this, "Timer: 5s", yOffset = 56.dpToPx())
        }

        btnTimer10s.setOnClickListener {
            ParameterConfig.setTimerDuration(10)
            updateTimerButtonIcon()
            defaultToolbarContent.visibility = View.VISIBLE
            timerOptionsContainer.visibility = View.GONE
            CustomToast.show(this, "Timer: 10s", yOffset = 56.dpToPx())
        }
    }

    private fun updateTimerButtonIcon() {
        when (ParameterConfig.getTimerDuration()) {
            0 -> btnTimer.setImageResource(R.drawable.ic_timer_off)
            3 -> btnTimer.setImageResource(R.drawable.ic_timer_3s)
            5 -> btnTimer.setImageResource(R.drawable.ic_timer_5s)
            10 -> btnTimer.setImageResource(R.drawable.ic_timer_10s)
        }
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
        countdownText = activityCameraControllerBinding.countdownText
        zoomLevelText = activityCameraControllerBinding.zoomLevelText
        topToolbar = activityCameraControllerBinding.topBarLayout
        btnFlash = activityCameraControllerBinding.btnFlash
        btnTimer = activityCameraControllerBinding.btnTimer
        btnGrid = activityCameraControllerBinding.btnGrid
        defaultToolbarContent = activityCameraControllerBinding.defaultToolbarContent
        timerOptionsContainer = activityCameraControllerBinding.timerOptionsContainer

        btnTimerOff = findViewById(R.id.btn_timer_off)
        btnTimer3s = findViewById(R.id.btn_timer_3s)
        btnTimer5s = findViewById(R.id.btn_timer_5s)
        btnTimer10s = findViewById(R.id.btn_timer_10s)
    }

    private fun setBackground() {
        val orderedNames = ParameterConfig.getProcessingOrder()

        val enabledSet = mutableSetOf<String>()
        if (ParameterConfig.isSuperResolutionEnabled()) enabledSet.add(SuperResolution.displayName)
        if (ParameterConfig.isDehazeEnabled()) enabledSet.add(Dehaze.displayName)
        if (ParameterConfig.isShadowRemovalEnabled()) enabledSet.add(ShadowRemoval.displayName)
        if (ParameterConfig.isDenoisingEnabled()) enabledSet.add(Denoising.displayName)

        val activeImageEnhancementTechniques = mutableListOf<ImageEnhancementType>()

        for (name in orderedNames) {
            if (name in enabledSet) {
                val type = when (name) {
                    SuperResolution.displayName -> ImageEnhancementType.SUPER_RESOLUTION
                    Dehaze.displayName -> ImageEnhancementType.DEHAZE
                    ShadowRemoval.displayName -> ImageEnhancementType.SHADOW_REMOVAL
                    Denoising.displayName -> ImageEnhancementType.DENOISING
                    else -> null
                }
                type?.let { activeImageEnhancementTechniques.add(it) }
            }
        }

        updateAlgoIndicators(activeImageEnhancementTechniques)
    }


    private fun updateScreenBorder() {
        val rootView = activityCameraControllerBinding.root

        val enabledColors = mutableListOf<Int>()

        if (ParameterConfig.isSuperResolutionEnabled()) enabledColors += Color.GREEN
        if (ParameterConfig.isDehazeEnabled()) enabledColors += Color.YELLOW
        if (ParameterConfig.isShadowRemovalEnabled()) enabledColors += Color.WHITE
        if (ParameterConfig.isDenoisingEnabled()) enabledColors += Color.CYAN

        val borderColor = when (enabledColors.size) {
            0 -> Color.BLACK
            1 -> enabledColors[0]
            else -> blendColors(enabledColors)
        }

        val borderDrawable = GradientDrawable().apply {
            setColor(Color.BLACK)
            setStroke(2.dpToPx(), borderColor)
        }

        rootView.background = borderDrawable
    }

    private fun blendColors(colors: List<Int>): Int {
        if (colors.isEmpty()) return Color.BLACK
        var blended = colors[0]
        for (i in 1 until colors.size) {
            blended = ColorUtils.blendARGB(blended, colors[i], 0.5f)
        }
        return blended
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

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        return super.onTouchEvent(event)
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            CameraController.getInstance().setZoom(detector.scaleFactor)
            zoomLevelText.text = String.format("%.1fx", CameraController.getInstance().zoomLevel)
            zoomLevelText.visibility = View.VISIBLE
            zoomTextHandler.removeCallbacks(hideZoomTextRunnable)
            zoomTextHandler.postDelayed(hideZoomTextRunnable, 1000)
            return true
        }
    }

    private fun startTimer(duration: Int) {
        var timeLeft = duration
        countdownText.text = timeLeft.toString() // Display initial duration
        countdownText.visibility = View.VISIBLE
        disableUIInteractivity()

        val timer = object : CountDownTimer(((duration + 1) * 1000).toLong(), 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeft = (millisUntilFinished / 1000).toInt()
                if (timeLeft > 0) {
                    countdownText.text = timeLeft.toString()
                } else {
                    // This handles the case where timeLeft becomes 0 just before onFinish
                    countdownText.text = ""
                }
            }

            override fun onFinish() {
                countdownText.visibility = View.GONE
                enableUIInteractivity()
                ProgressManager.getInstance().resetProgress()
                CameraController.getInstance().captureImage()
            }
        }
        timer.start()
    }
}