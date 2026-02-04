package com.soopersaiyan.filterify

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RenderEffect
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.camera.core.Camera
import android.content.res.ColorStateList
import android.media.ExifInterface
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    // store original top padding to avoid applying status bar inset multiple times
    private var topBarOriginalPaddingTop = 0

    private lateinit var previewView: PreviewView
    private lateinit var topBarLayout: View
    private lateinit var bottomBarLayout: View
    private lateinit var captureButton: ImageButton
    private lateinit var filterCarousel: RecyclerView
    private lateinit var galleryButton: ImageButton
    // The top bar is present in the layout now — look up views directly (nullable where appropriate)
    private var settingsButton: ImageButton? = null
    private var flashButton: ImageButton? = null
    private var cameraSwitchButton: ImageButton? = null
    private lateinit var effectsButton: LinearLayout
    private lateinit var retouchButton: LinearLayout
    private lateinit var adView: AdView

    // New overlay views for gallery image
    private lateinit var galleryImageOverlay: ImageView
    private lateinit var closeGalleryButton: ImageButton
    private var currentGalleryBitmap: Bitmap? = null

    // Timer and HDR UI
    private lateinit var hdrButton: TextView
    private lateinit var timerButton: LinearLayout
    private lateinit var timerText: TextView
    private lateinit var countdownOverlay: TextView

    private lateinit var galleryLauncher: ActivityResultLauncher<Intent>

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    // default to ORIGINAL filter (no change applied)
    private var currentFilter = Filter.ORIGINAL
    private var isTorchOn = false
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var isEffectsMode = true

    // New states
    private var isHdrEnabled = false
    private var isTimerEnabled = false

    // include ORIGINAL as the first/default filter
    private val filters = listOf(
        Filter.ORIGINAL,
        Filter.SWEET,
        Filter.PASTEL,
        Filter.BLOOM,
        Filter.VINTAGS,
        Filter.MONO
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        topBarLayout = findViewById(R.id.topBar)
        // Capture original padding
        topBarOriginalPaddingTop = topBarLayout.paddingTop
        // Apply status bar inset so topBar is placed below system status bar on all devices
        ViewCompat.setOnApplyWindowInsetsListener(topBarLayout) { v, insets ->
            val statusInset = insets.getInsets(Type.statusBars()).top
            v.setPadding(v.paddingLeft, topBarOriginalPaddingTop + statusInset, v.paddingRight, v.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(topBarLayout)
        // Apply navigation bar inset for bottomBar so it sits above gesture nav / home indicator
        bottomBarLayout = findViewById(R.id.bottomBar)
        // Use TextureView-backed implementation so view effects (filters) apply reliably
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        captureButton = findViewById(R.id.captureButton)
        filterCarousel = findViewById(R.id.filterCarousel)
        galleryButton = findViewById(R.id.galleryButton)
        // The top bar is present in the layout now — look up views directly (nullable where appropriate)
        settingsButton = findViewById(R.id.settingsButton)
        flashButton = findViewById(R.id.flashButton)
        cameraSwitchButton = findViewById(R.id.cameraSwitchButton)
        effectsButton = findViewById(R.id.effectsButton)
        retouchButton = findViewById(R.id.retouchButton)
        adView = findViewById(R.id.adView)

        // New overlay views
        galleryImageOverlay = findViewById(R.id.galleryImageOverlay)
        closeGalleryButton = findViewById(R.id.closeGalleryButton)

        // Timer / HDR views
        hdrButton = findViewById(R.id.hdrButton)
        timerButton = findViewById(R.id.timerButton)
        timerText = findViewById(R.id.timerText)
        countdownOverlay = findViewById(R.id.countdownOverlay)

        // Register gallery launcher (Activity Result API) to replace deprecated startActivityForResult
        galleryLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val uri = data?.data
                if (uri != null) {
                    // Open PhotoEditActivity so user can apply filters/retouch
                    try {
                        val intent = Intent(this, PhotoEditActivity::class.java).apply {
                            putExtra(PhotoEditActivity.EXTRA_IMAGE_URI, uri.toString())
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        // Fallback: load bitmap into overlay
                        val bitmap = loadBitmapFromUri(uri)
                        if (bitmap != null) {
                            showGalleryBitmap(bitmap)
                        } else {
                            Toast.makeText(this, "Error loading image", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        MobileAds.initialize(this) {}
        adView.loadAd(AdRequest.Builder().build())

        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupFilterCarousel()
        setupClickListeners()
        updateModeButtons()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun setupFilterCarousel() {
        filterCarousel.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        val adapter = FilterAdapter(filters) { filter ->
            currentFilter = filter
            // If a gallery image is shown apply filter to it, otherwise to the preview
            if (galleryImageOverlay.isVisible) {
                applyFilterToGalleryOverlay()
            } else {
                applyFilterToPreview()
            }
        }
        filterCarousel.adapter = adapter
    }

    private fun setupClickListeners() {
        captureButton.setOnClickListener { onCaptureClicked() }

        galleryButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            galleryLauncher.launch(intent)
        }

        flashButton?.setOnClickListener {
            val capture = imageCapture
            if (capture == null) {
                Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Cycle flash mode: OFF -> ON -> AUTO -> OFF
            flashMode = when (flashMode) {
                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_OFF
                else -> ImageCapture.FLASH_MODE_OFF
            }
            capture.flashMode = flashMode

            // Enable torch only for FLASH_MODE_ON (continuous light for preview)
            val cam = camera
            val hasFlash = try { cam?.cameraInfo?.hasFlashUnit() ?: false } catch (_: Exception) { false }
            if (flashMode == ImageCapture.FLASH_MODE_ON && hasFlash) {
                try { cam?.cameraControl?.enableTorch(true); isTorchOn = true } catch (_: Exception) { /* ignore */ }
            } else {
                try { cam?.cameraControl?.enableTorch(false); isTorchOn = false } catch (_: Exception) { /* ignore */ }
            }
            updateFlashButton()
        }

        cameraSwitchButton?.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            startCamera()
        }

        effectsButton.setOnClickListener {
            isEffectsMode = true
            updateModeButtons()
        }

        retouchButton.setOnClickListener {
            isEffectsMode = false
            updateModeButtons()
            // Retouch functionality can be added later
            Toast.makeText(this, "Retouch mode coming soon", Toast.LENGTH_SHORT).show()
        }

        settingsButton?.setOnClickListener {
            // Settings functionality can be added later
            Toast.makeText(this, "Settings coming soon", Toast.LENGTH_SHORT).show()
        }

        closeGalleryButton.setOnClickListener {
            hideGalleryOverlay()
        }

        hdrButton.setOnClickListener {
            isHdrEnabled = !isHdrEnabled
            updateHdrUI()
            // Apply a simulated HDR preview by adjusting color matrix slightly
            applyFilterToPreview()
            if (galleryImageOverlay.isVisible) applyFilterToGalleryOverlay()
        }

        timerButton.setOnClickListener {
            isTimerEnabled = !isTimerEnabled
            timerText.setTextColor(if (isTimerEnabled) getColor(R.color.pink_selected) else getColor(R.color.grey_dark))
        }
    }

    private fun updateHdrUI() {
        hdrButton.setTextColor(if (isHdrEnabled) getColor(R.color.pink_selected) else getColor(R.color.grey_dark))
        hdrButton.typeface = android.graphics.Typeface.create(hdrButton.typeface, if (isHdrEnabled) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    private fun onCaptureClicked() {
        if (isTimerEnabled) {
            startCountdownThenCapture(3)
        } else {
            takePhoto()
        }
    }

    private fun startCountdownThenCapture(seconds: Int) {
        countdownOverlay.visibility = View.VISIBLE
        object : CountDownTimer((seconds * 1000).toLong(), 1000L) {
            var remaining = seconds
            override fun onTick(millisUntilFinished: Long) {
                countdownOverlay.text = remaining.toString()
                remaining -= 1
            }

            override fun onFinish() {
                countdownOverlay.visibility = View.GONE
                takePhoto()
            }
        }.start()
    }

    private fun updateModeButtons() {
        val effectsText = effectsButton.findViewById<TextView>(R.id.effectsText)
        val retouchText = retouchButton.findViewById<TextView>(R.id.retouchText)

        if (isEffectsMode) {
            effectsButton.background = AppCompatResources.getDrawable(this, R.drawable.button_effects_background)
            effectsText.setTextColor(getColor(R.color.pink_selected))
            effectsText.textSize = 14f
            effectsText.typeface = android.graphics.Typeface.create(effectsText.typeface, android.graphics.Typeface.BOLD)

            retouchButton.background = AppCompatResources.getDrawable(this, R.drawable.button_retouch_background)
            retouchText.setTextColor(getColor(R.color.grey_dark))
            retouchText.textSize = 14f
            retouchText.typeface = android.graphics.Typeface.create(retouchText.typeface, android.graphics.Typeface.NORMAL)
        } else {
            retouchButton.background = AppCompatResources.getDrawable(this, R.drawable.button_effects_background)
            retouchText.setTextColor(getColor(R.color.pink_selected))
            retouchText.textSize = 14f
            retouchText.typeface = android.graphics.Typeface.create(retouchText.typeface, android.graphics.Typeface.BOLD)

            effectsButton.background = AppCompatResources.getDrawable(this, R.drawable.button_retouch_background)
            effectsText.setTextColor(getColor(R.color.grey_dark))
            effectsText.textSize = 14f
            effectsText.typeface = android.graphics.Typeface.create(effectsText.typeface, android.graphics.Typeface.NORMAL)
        }
    }

    private fun updateFlashButton() {
        try {
            val color = when (flashMode) {
                ImageCapture.FLASH_MODE_ON -> getColor(R.color.pink_selected)
                ImageCapture.FLASH_MODE_AUTO -> getColor(R.color.pink_selected)
                else -> getColor(R.color.grey_dark)
            }
            flashButton?.imageTintList = ColorStateList.valueOf(color)
            // Slightly reduce alpha for AUTO to indicate intermediate state
            flashButton?.alpha = if (flashMode == ImageCapture.FLASH_MODE_AUTO) 0.8f else 1.0f
            // Update contentDescription for accessibility
            val modeDesc = when (flashMode) {
                ImageCapture.FLASH_MODE_ON -> "ON"
                ImageCapture.FLASH_MODE_AUTO -> "AUTO"
                else -> "OFF"
            }
            flashButton?.contentDescription = "${getString(R.string.flash)}: $modeDesc"
        } catch (e: Exception) {
            flashButton?.alpha = if (flashMode == ImageCapture.FLASH_MODE_ON) 1.0f else 0.6f
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(flashMode)
                .build()
            try {
                cameraProvider.unbindAll()
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()
                // Bind and keep reference to the Camera so we can toggle torch
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                // Reset torch and flash mode when switching cameras
                isTorchOn = false
                flashMode = ImageCapture.FLASH_MODE_OFF
                imageCapture?.flashMode = flashMode
                updateFlashButton()
                applyFilterToPreview()
            } catch (exc: Exception) {
                Log.e(TAG, "Failed to bind camera use cases", exc)
                Toast.makeText(this, R.string.take_photo_error, Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun applyFilterToPreview() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (currentFilter == Filter.ORIGINAL && !isHdrEnabled) {
                previewView.setRenderEffect(null)
            } else {
                // Combine the selected color matrix with optional HDR boost
                val baseMatrix = currentFilter.matrix()
                if (isHdrEnabled) {
                    // Simulate HDR by increasing contrast and saturation slightly
                    val hdrMatrix = ColorMatrix().apply {
                        set(floatArrayOf(
                            1.08f, 0f, 0f, 0f, 6f,
                            0f, 1.08f, 0f, 0f, 6f,
                            0f, 0f, 1.08f, 0f, 6f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                    }
                    baseMatrix.postConcat(hdrMatrix)
                }
                val renderEffect = RenderEffect.createColorFilterEffect(
                    ColorMatrixColorFilter(baseMatrix)
                )
                previewView.setRenderEffect(renderEffect)
            }
        } else {
            // Pre-Android 12 fallback: apply the color matrix via a layer paint on the PreviewView
            if (currentFilter == Filter.ORIGINAL && !isHdrEnabled) {
                previewView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            } else {
                val matrix = currentFilter.matrix()
                if (isHdrEnabled) {
                    val hdrMatrix = ColorMatrix().apply {
                        set(floatArrayOf(
                            1.08f, 0f, 0f, 0f, 6f,
                            0f, 1.08f, 0f, 0f, 6f,
                            0f, 0f, 1.08f, 0f, 6f,
                            0f, 0f, 0f, 1f, 0f
                        ))
                    }
                    matrix.postConcat(hdrMatrix)
                }
                val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
                previewView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
            }
        }
    }

    // Apply ColorMatrix filter to the gallery overlay ImageView
    private fun applyFilterToGalleryOverlay() {
        val matrix = currentFilter.matrix()
        if (isHdrEnabled) {
            val hdrMatrix = ColorMatrix().apply {
                set(floatArrayOf(
                    1.08f, 0f, 0f, 0f, 6f,
                    0f, 1.08f, 0f, 0f, 6f,
                    0f, 0f, 1.08f, 0f, 6f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            matrix.postConcat(hdrMatrix)
        }
        val cf = if (currentFilter == Filter.ORIGINAL && !isHdrEnabled) null else ColorMatrixColorFilter(matrix)
        galleryImageOverlay.colorFilter = cf
    }

    private fun showGalleryBitmap(bitmap: Bitmap) {
        // recycle previous bitmap if present
        currentGalleryBitmap?.recycle()
        currentGalleryBitmap = bitmap
        galleryImageOverlay.setImageBitmap(bitmap)
        galleryImageOverlay.visibility = View.VISIBLE
        closeGalleryButton.visibility = View.VISIBLE
        applyFilterToGalleryOverlay()
    }

    private fun hideGalleryOverlay() {
        galleryImageOverlay.visibility = View.GONE
        closeGalleryButton.visibility = View.GONE
        // clear image and recycle
        galleryImageOverlay.setImageDrawable(null)
        currentGalleryBitmap?.recycle()
        currentGalleryBitmap = null
        // restore preview filter
        applyFilterToPreview()
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val photoFile = createImageFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                // If HDR is enabled or filter is applied, we need to post-process saved file
                if (isHdrEnabled || currentFilter != Filter.ORIGINAL) {
                    // apply filter on a background thread then save
                    try {
                        val original = BitmapFactory.decodeFile(photoFile.absolutePath) ?: run {
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, R.string.take_photo_error, Toast.LENGTH_SHORT).show()
                            }
                            return
                        }
                        val filtered = createFilteredBitmapForSave(original)
                        FileOutputStream(photoFile).use { filtered.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                        original.recycle()
                        filtered.recycle()
                        MediaScannerConnection.scanFile(this@MainActivity, arrayOf(photoFile.absolutePath), null, null)
                    } catch (_: Exception) {
                        Log.e(TAG, "Error post-processing saved image", null)
                    }
                }

                // Normalize orientation for the saved file so editor/preview show upright image
                try {
                    normalizeJpegOrientation(photoFile)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to normalize orientation", e)
                }
                runOnUiThread {
                    try {
                        val intent = Intent(this@MainActivity, PhotoEditActivity::class.java).apply {
                            putExtra(PhotoEditActivity.EXTRA_IMAGE_URI, photoFile.absolutePath)
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, getString(R.string.photo_saved, photoFile.absolutePath), Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed", exception)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, R.string.take_photo_error, Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun createFilteredBitmapForSave(source: Bitmap): Bitmap {
        // apply current filter and HDR matrix
        val result = createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val matrix = currentFilter.matrix()
        if (isHdrEnabled) {
            val hdrMatrix = ColorMatrix().apply {
                set(floatArrayOf(
                    1.08f, 0f, 0f, 0f, 6f,
                    0f, 1.08f, 0f, 0f, 6f,
                    0f, 0f, 1.08f, 0f, 6f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            matrix.postConcat(hdrMatrix)
        }
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(matrix)
            isFilterBitmap = true
        }
        Canvas(result).drawBitmap(source, 0f, 0f, paint)
        return result
    }

    // Normalize JPEG orientation: rotate pixel data according to EXIF then set orientation tag to NORMAL
    private fun normalizeJpegOrientation(file: File) {
        try {
            val exif = try {
                ExifInterface(file.absolutePath)
            } catch (_: Exception) { null }
            val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                ?: ExifInterface.ORIENTATION_NORMAL
            if (orientation == ExifInterface.ORIENTATION_NORMAL) return

            val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
                else -> { /* normal */ }
            }
            val rotated = try { if (matrix.isIdentity) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true) } catch (_: Exception) { bmp }
            FileOutputStream(file).use { rotated.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            try {
                val newExif = ExifInterface(file.absolutePath)
                newExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                newExif.saveAttributes()
            } catch (_: Exception) { /* ignore */ }
            if (rotated !== bmp && !bmp.isRecycled) bmp.recycle()
            if (!rotated.isRecycled) rotated.recycle()
        } catch (e: Exception) {
            Log.w(TAG, "normalizeJpegOrientation failed", e)
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from uri", e)
            null
        }
    }

    private fun applyFilterToBitmapAndSave(bitmap: Bitmap) {
        val filtered = createFilteredBitmap(bitmap)
        val photoFile = createImageFile()
        FileOutputStream(photoFile).use { filtered.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        filtered.recycle()
        try {
            MediaScannerConnection.scanFile(this, arrayOf(photoFile.absolutePath), null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning saved image", e)
        }
        Toast.makeText(this, getString(R.string.photo_saved, photoFile.absolutePath), Toast.LENGTH_SHORT).show()
    }

    private fun applyFilterAndSave(file: File) {
        val original = BitmapFactory.decodeFile(file.absolutePath) ?: return
        val filtered = createFilteredBitmapForSave(original)
        FileOutputStream(file).use { filtered.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        original.recycle()
        filtered.recycle()
        try {
            MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning saved image", e)
        }
    }

    private fun createFilteredBitmap(source: Bitmap): Bitmap {
        val result = createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(currentFilter.matrix())
            isFilterBitmap = true
        }
        Canvas(result).drawBitmap(source, 0f, 0f, paint)
        return result
    }

    private fun createImageFile(): File {
        val timestamp = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(Date())
        return File(outputDirectory, "$timestamp.jpg")
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        adView.resume()
    }

    override fun onPause() {
        adView.pause()
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        adView.destroy()
        cameraExecutor.shutdown()
        currentGalleryBitmap?.recycle()
        currentGalleryBitmap = null
    }

    enum class Filter(val nameResId: Int, private val matrixValues: FloatArray) {
        ORIGINAL(R.string.filter_original, floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
        SWEET(R.string.filter_sweet, floatArrayOf(
            1.2f, 0.1f, 0.1f, 0f, 10f,
            0.1f, 1.1f, 0f, 0f, 5f,
            0.1f, 0.1f, 1.0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )),
        PASTEL(R.string.filter_pastel, floatArrayOf(
            0.9f, 0.1f, 0.1f, 0f, 20f,
            0.1f, 0.9f, 0.1f, 0f, 20f,
            0.1f, 0.1f, 0.9f, 0f, 20f,
            0f, 0f, 0f, 1f, 0f
        )),
        BLOOM(R.string.filter_bloom, floatArrayOf(
            1.15f, 0.05f, 0.05f, 0f, 5f,
            0.05f, 1.15f, 0.05f, 0f, 5f,
            0.05f, 0.05f, 1.1f, 0f, 5f,
            0f, 0f, 0f, 1f, 0f
        )),
        VINTAGS(R.string.filter_vintags, floatArrayOf(
            0.627f, 0.320f, -0.039f, 0f, 9.651f,
            0.025f, 0.644f, 0.130f, 0f, 7.163f,
            0.046f, -0.085f, 0.524f, 0f, 5.159f,
            0f, 0f, 0f, 1f, 0f
        )),
        MONO(R.string.filter_mono, floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        ));

        fun matrix() = ColorMatrix(matrixValues)
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.CAMERA)
        } else {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        private const val FILENAME_FORMAT = "yyyyMMdd_HHmmss"
    }
}
