package com.bgcoding.camera2api


import LRWarpingOperator
import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.media.MediaActionSound
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Debug
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.bgcoding.camera2api.assessment.InputImageEnergyReader
import com.bgcoding.camera2api.constants.ParameterConfig
import com.bgcoding.camera2api.io.DirectoryStorage
import com.bgcoding.camera2api.io.FileImageReader
import com.bgcoding.camera2api.io.FileImageWriter
import com.bgcoding.camera2api.io.ImageFileAttribute
import com.bgcoding.camera2api.model.AttributeHolder
import com.bgcoding.camera2api.model.multiple.SharpnessMeasure
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
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Semaphore


class MainActivity : ComponentActivity() {
    private var processedImagesCounter = 0
    lateinit var captureRequest: CaptureRequest.Builder
    lateinit var handler: Handler
    lateinit var handlerThread: HandlerThread
    lateinit var cameraManager: CameraManager
    lateinit var textureView: TextureView
    lateinit var cameraCaptureSession: CameraCaptureSession
    lateinit var cameraDevice: CameraDevice
    lateinit var imageReader: ImageReader
    lateinit var progressBar: ProgressBar
    lateinit var loadingText: TextView
    lateinit var loadingBox: LinearLayout
    lateinit var cameraId: String
    private var sensorOrientation: Int = 0
    val ImageInputMap: MutableList<String> = mutableListOf()


    fun getAppMemoryUsage(): Long {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val memoryInfoArray = arrayOf(Debug.MemoryInfo())
        Debug.getMemoryInfo(memoryInfoArray[0])

        val usedMemory = memoryInfoArray[0].getTotalPss() * 1024L // in bytes
        return usedMemory
    }

    private val permissionsRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                if (!it.value) {
                    // Permission was denied, handle this situation
                } else {
                    // Permission was granted, you can now proceed with your operations
                    // setCameraPreview()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Initialization Failed")
        } else {
            Log.d("OpenCV", "Initialization Successful")
        }
        enableEdgeToEdge()
        DirectoryStorage.getSharedInstance().createDirectory()
        FileImageWriter.initialize(this)
        FileImageReader.initialize(this)
        ParameterConfig.initialize(this)
        AttributeHolder.initialize(this)

        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )

        val allPermissionsGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        when {
            allPermissionsGranted -> {
                // All permissions already granted
                // setCameraPreview()
            }

            else -> {
                // Some permissions not granted, request them
                permissionsRequest.launch(permissions)
            }
        }
        setCameraPreview()

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        handlerThread = HandlerThread("video thread")
        handlerThread.start()
        handler = Handler((handlerThread).looper)

        this.textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                open_camera()
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                /*TODO("Not yet implemented")*/

                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            }

        }


        this.cameraId =
            getCameraId(CameraCharacteristics.LENS_FACING_BACK) // Dynamically get the rear camera ID
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(ImageFormat.JPEG)

        // Sort the sizes array in descending order and select the first one (highest resolution)
        val highestResolution = sizes?.sortedWith(compareBy { it.width * it.height })?.last()

        imageReader = if (highestResolution != null) {
            ImageReader.newInstance(
                highestResolution.width,
                highestResolution.height,
                ImageFormat.JPEG,
                20
            )
        } else {
            // Fallback to a default resolution if no sizes are available
            ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 1)
        }

        imageReader.setOnImageAvailableListener(object : ImageReader.OnImageAvailableListener {
            override fun onImageAvailable(p0: ImageReader?) {
                val image = p0?.acquireNextImage()

                val buffer = image!!.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)

                image.close()

                // Convert byte array to Bitmap
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ImageInputMap.add(saveImageToStorage(bitmap))
                if (ImageInputMap.size == 5) {
                    superResolutionImage()
                    runOnUiThread {
                        loadingBox.visibility = View.GONE
                    }
                }
            }
        }, handler)

        /*findViewById<Button>(R.id.capture).apply {
            setOnClickListener {
                captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                captureRequest.addTarget(imageReader.surface)
                cameraCaptureSession.capture(captureRequest.build(), null, null)
            }
        }*/
        findViewById<Button>(R.id.capture).apply {
            setOnClickListener {
                var totalCaptures = 5
//                var completedCaptures = 0
                val captureList = mutableListOf<CaptureRequest>()

                for (i in 0 until totalCaptures) { // Change this to the number of photos you want to capture in the burst
                    val captureRequest =
                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                    captureRequest.addTarget(imageReader.surface)
                    captureList.add(captureRequest.build())
                }

                playShutterSound()
                runOnUiThread {
                    loadingBox.visibility = View.VISIBLE
                }

                cameraCaptureSession.captureBurst(
                    captureList,
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            super.onCaptureCompleted(session, request, result)
                            // Handle the result of the capture here
                            Log.d("BurstCapture", "Capture completed")
//                        completedCaptures++
//                        if (completedCaptures >= totalCaptures) {
//                            runOnUiThread {
//                                loadingBox.visibility = View.GONE
//                            }
//                            Toast.makeText(this@MainActivity, "Images captured and saved.", Toast.LENGTH_SHORT).show()
//                        }
                        }
                    },
                    null
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun open_camera() {
        cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(p0: CameraDevice) {
                cameraDevice = p0

                captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

                var surface = Surface(textureView.surfaceTexture)
                captureRequest.addTarget(surface)

                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                sensorOrientation =
                    characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0

                cameraDevice.createCaptureSession(
                    listOf(surface, imageReader.surface),
                    object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(p0: CameraCaptureSession) {
                            cameraCaptureSession = p0
                            cameraCaptureSession.setRepeatingRequest(
                                captureRequest.build(),
                                null,
                                null
                            )
                        }

                        override fun onConfigureFailed(p0: CameraCaptureSession) {

                        }
                    },
                    handler
                )
            }

            override fun onDisconnected(p0: CameraDevice) {
            }

            override fun onError(camera: CameraDevice, error: Int) {
            }

        }, handler)
    }

    private fun setCameraPreview() {
        /*setContent {
            Camera2ApiTheme {

            }
        }*/
        setContentView(R.layout.activity_main)
        // Initialize UI elements
        this.textureView = findViewById(R.id.textureView)
        this.progressBar = findViewById(R.id.progressBar)
        this.loadingText = findViewById(R.id.loadingText)
        this.loadingBox = findViewById(R.id.loadingBox)
    }

    fun playShutterSound() {
        val sound = MediaActionSound()
        sound.play(MediaActionSound.SHUTTER_CLICK)
    }

    // Get id of front camera
    fun getCameraId(lensFacing: Int): String {
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == lensFacing) {
                return cameraId
            }
        }
        return ""
    }

    fun saveImageToStorage(bitmap: Bitmap): String {
        val folder = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "MyApp/Images")
        if (!folder.exists()) {
            folder.mkdirs() // Create directory if it doesn't exist
        }

        val fileName = "image_${System.currentTimeMillis()}.jpg"
        val file = File(folder, fileName)

        try {
            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.close()
            MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), null, null)
            return file.absolutePath // Return the absolute path
        } catch (e: IOException) {
            e.printStackTrace()
            Log.d("SaveImage", "Failed to save image: ${e.message}")
            return ""
        }
    }

    fun superResolutionImage(){
        SharpnessMeasure.initialize();
        val energyInputMatList: Array<Mat> = Array(ImageInputMap.size) { Mat() }
        val energyReaders: MutableList<InputImageEnergyReader> = mutableListOf()
        try {
            val energySem = Semaphore(0)  // Start with 0 permits to block acquire until all tasks finish
            for (i in energyInputMatList.indices) {
                val reader = InputImageEnergyReader(energySem, ImageInputMap[i])
                energyReaders.add(reader)
                // Assuming each reader is run on a separate thread or coroutines
            }

            // Wait for all threads to finish
            for (reader in energyReaders) {
                reader.start()  // Ensure reader threads are started (this depends on how InputImageEnergyReader works)
            }

            // Wait for all semaphores to release
            energySem.acquire(energyInputMatList.size)

            // Once all tasks are done, copy results
            for (i in energyReaders.indices) {
                energyInputMatList[i] = energyReaders[i].getOutputMat()
            }

        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        val yangFilter = YangFilter(energyInputMatList)
        yangFilter.perform()

        val sharpnessResult = SharpnessMeasure.getSharedInstance().measureSharpness(yangFilter.getEdgeMatList())
        val inputIndices: Array<Int> = SharpnessMeasure.getSharedInstance().trimMatList(ImageInputMap.size, sharpnessResult, 0.0)
        val rgbInputMatList = Array(inputIndices.size) { Mat() }
        interpolateImage(sharpnessResult.getOutsideLeastIndex());
        SRProcessManager.getInstance().initialHRProduced()
        var bestIndex = 0
        var inputMat: Mat
        for (i in inputIndices.indices) {
            inputMat = FileImageReader.getInstance()!!.imReadFullPath(ImageInputMap[inputIndices[i]])
            val unsharpMaskOperator = UnsharpMaskOperator(inputMat, inputIndices[i])
            unsharpMaskOperator.perform()
            rgbInputMatList[i] = unsharpMaskOperator.getResult()

            if (sharpnessResult.bestIndex == inputIndices[i]) {
                bestIndex = i
            }
        }
        this.performActualSuperres(rgbInputMatList, inputIndices, bestIndex, false)
        SRProcessManager.getInstance().srProcessCompleted()

    }
    private fun interpolateImage(index: Int) {
        val inputMat = FileImageReader.getInstance()!!.imReadFullPath(ImageInputMap[index])

        val outputMat = ImageOperator.performInterpolation(inputMat, ParameterConfig.getScalingFactor().toFloat(), Imgproc.INTER_LINEAR)
        FileImageWriter.getInstance()?.saveMatrixToImage(outputMat, "linear", ImageFileAttribute.FileType.JPEG)
        outputMat.release()

        inputMat.release()
        System.gc()
    }

    private fun performActualSuperres(
        rgbInputMatList: Array<Mat>,
        inputIndices: Array<Int>,
        bestIndex: Int,
        debugMode: Boolean
    ) {
        // Perform denoising on original input list
        val denoisingOperator = DenoisingOperator(rgbInputMatList)
        denoisingOperator.perform()

        // Use var to allow reassignment
        var updatedMatList = denoisingOperator.getResult()

        // Pass updatedMatList to the next method
        this.performFullSRMode(updatedMatList, inputIndices, bestIndex, debugMode)
    }

    private fun performFullSRMode(
        rgbInputMatList: Array<Mat>,
        inputIndices: Array<Int>,
        bestIndex: Int,
        debug: Boolean
    ) {
        // Perform feature matching of LR images against the first image as reference mat.
        val warpChoice = ParameterConfig.getPrefsInt(ParameterConfig.WARP_CHOICE_KEY, 1)

        // Perform perspective warping and alignment
        val succeedingMatList = rgbInputMatList.sliceArray(1 until rgbInputMatList.size)

        val medianResultNames = Array(succeedingMatList.size) { i -> "median_align_" + i }
        val warpResultNames = Array(succeedingMatList.size) { i -> "warp_" + i }


        when (warpChoice) {
            1 -> {
                this.performMedianAlignment(rgbInputMatList, medianResultNames)
                this.performPerspectiveWarping(rgbInputMatList[0], succeedingMatList, succeedingMatList, warpResultNames)
            }
        }


        SharpnessMeasure.destroy()

        val numImages = AttributeHolder.getSharedInstance()!!.getValue("WARPED_IMAGES_LENGTH_KEY", 0)
        val warpedImageNames = Array(numImages) { i -> "warp_" + i }
        val medianAlignedNames = Array(numImages) { i -> "median_align_" + i }

        val alignedImageNames = assessImageWarpResults(inputIndices[0], warpChoice, warpedImageNames, medianAlignedNames, debug)

        this.performMeanFusion(inputIndices[0], bestIndex, alignedImageNames, debug)

        try {
            Thread.sleep(3000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

    }

    private fun performMedianAlignment(imagesToAlignList: Array<Mat>, resultNames: Array<String>) {

        val medianAlignmentOperator = MedianAlignmentOperator(imagesToAlignList, resultNames)
        medianAlignmentOperator.perform()
    }

    private fun performPerspectiveWarping(
        referenceMat: Mat,
        candidateMatList: Array<Mat>,
        imagesToWarpList: Array<Mat>,
        resultNames: Array<String>
    ) {

        val matchingOperator = FeatureMatchingOperator(referenceMat, candidateMatList)
        matchingOperator.perform()

        val perspectiveWarpOperator = LRWarpingOperator(
            matchingOperator.refKeypoint,
            imagesToWarpList,
            resultNames,
            matchingOperator.getdMatchesList(),
            matchingOperator.lrKeypointsList
        )
        perspectiveWarpOperator.perform()

        // release images
        matchingOperator.refKeypoint.release()
    }

    fun assessImageWarpResults(
        index: Int,
        alignmentUsed: Int,
        warpedImageNames: Array<String>,
        medianAlignedNames: Array<String>,
        useLocalDir: Boolean
    ): Array<String> {  // Change return type to Array<String>
        return when (alignmentUsed) {
            1 -> {
                val referenceMat: Mat
                val fileImageReader = FileImageReader.getInstance()
                if (fileImageReader != null) {
                    referenceMat = if (useLocalDir) {
                        fileImageReader.imReadOpenCV("input_" + index, ImageFileAttribute.FileType.JPEG)
                    } else {
                        fileImageReader.imReadFullPath(ImageInputMap[index])
                    }

                    val warpResultEvaluator = WarpResultEvaluator(referenceMat, warpedImageNames, medianAlignedNames)
                    warpResultEvaluator.perform()

                    // Filter out null values and convert to a non-nullable array
                    warpResultEvaluator.chosenAlignedNames.filterNotNull().toTypedArray()
                } else {
                    // Handle the case where FileImageReader is null (optional)
                    throw IllegalStateException("FileImageReader instance is null.")
                }
            }
            2 -> medianAlignedNames
            else -> warpedImageNames
        }
    }




    private fun performMeanFusion(
        index: Int,
        bestIndex: Int,
        alignedImageNames: Array<String>,
        debugMode: Boolean
    ) {
        if (alignedImageNames.size == 1) {
            val resultMat: Mat = if (debugMode) {
                FileImageReader.getInstance()?.imReadOpenCV(
                    "input_" + bestIndex,
                    ImageFileAttribute.FileType.JPEG
                ) ?: throw IllegalStateException("FileImageReader instance is null")
            } else {
                FileImageReader.getInstance()?.imReadFullPath(
                    ImageInputMap[bestIndex]
                ) ?: throw IllegalStateException("FileImageReader instance is null")
            }
            // No need to perform image fusion, just use the best image.
            val interpolatedMat = ImageOperator.performInterpolation(
                resultMat, ParameterConfig.getScalingFactor().toFloat(), Imgproc.INTER_CUBIC
            )
            FileImageWriter.getInstance()?.saveMatrixToImage(
                interpolatedMat, "result", ImageFileAttribute.FileType.JPEG
            )

            resultMat.release()
        } else {
            val imagePathList = mutableListOf<String>()
            // Add initial input HR image
            val inputMat: Mat = if (debugMode) {
                FileImageReader.getInstance()?.imReadOpenCV(
                    "input_" + index,
                    ImageFileAttribute.FileType.JPEG
                ) ?: throw IllegalStateException("FileImageReader instance is null")
            } else {
                FileImageReader.getInstance()?.imReadFullPath(
                    ImageInputMap[index]
                ) ?: throw IllegalStateException("FileImageReader instance is null")
            }

            for (alignedImageName in alignedImageNames) {
                imagePathList.add(alignedImageName)
            }

            val fusionOperator = MeanFusionOperator(inputMat, imagePathList.toTypedArray())
            for (i in ImageInputMap.indices) {
                val dirFile: File = File(ImageInputMap[i])
                FileImageWriter.getInstance()?.deleteRecursive(dirFile)
            }
            fusionOperator.perform()
            FileImageWriter.getInstance()?.apply {
                saveMatrixToImage(
                    fusionOperator.getResult()!!, "result", ImageFileAttribute.FileType.JPEG
                )
                saveHRResultToUserDir(
                    fusionOperator.getResult()!!, ImageFileAttribute.FileType.JPEG
                )
            }

            fusionOperator.getResult()!!.release()
        }
    }









}