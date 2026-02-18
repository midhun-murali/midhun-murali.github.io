package com.beauty.camera.selfie.camera.filter

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat.Type
import com.yalantis.ucrop.UCrop
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.LoadAdError
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayInputStream
import android.media.MediaScannerConnection
import android.content.Context

class PhotoEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
    }

    // reuse main layout ids: previewView is an ImageView in editor layout
    private lateinit var imageView: ImageView
    private lateinit var filterCarousel: RecyclerView
    private lateinit var btnSave: ImageButton
    private lateinit var btnClose: ImageButton
    private lateinit var galleryImageOverlay: ImageView
    private var originalBitmap: Bitmap? = null
    private var workingBitmap: Bitmap? = null
    private var currentFilter: Filter = Filter.ORIGINAL
    private var brightnessValue = 0f // -100 .. +100
    private var isEffectsMode = true
    private var isRetouchMode = false
    // show all available retouch options
    private val retouchItems = RetouchOption.entries.toList()
    private lateinit var effectsButtonEdit: LinearLayout
    private lateinit var retouchButtonEdit: LinearLayout
    private lateinit var effectsTextEdit: TextView
    private lateinit var retouchTextEdit: TextView
    private var modeContainerOriginalPaddingTop = 0
    private lateinit var cropLauncher: ActivityResultLauncher<Intent>
    private var sourceImageUri: Uri? = null
    private lateinit var adView: AdView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_photo_edit)

        // Initialize ads and load banner
        MobileAds.initialize(this) {}
        adView = findViewById(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() { adView.visibility = View.VISIBLE }
            override fun onAdFailedToLoad(p0: LoadAdError) { adView.visibility = View.GONE }
        }

        imageView = findViewById(R.id.previewView)
        filterCarousel = findViewById(R.id.filterCarousel)
        btnSave = findViewById(R.id.captureButton)
        btnClose = findViewById(R.id.closeGalleryButton)
        galleryImageOverlay = findViewById(R.id.galleryImageOverlay)
        // reuse main UI ids so layout mirrors MainActivity
        effectsButtonEdit = findViewById(R.id.effectsButton)
        retouchButtonEdit = findViewById(R.id.retouchButton)
        effectsTextEdit = findViewById(R.id.effectsText)
        retouchTextEdit = findViewById(R.id.retouchText)
        // Back button in the top-left of the editor
        val btnBack = findViewById<ImageButton?>(R.id.btnBackEdit)
        btnBack?.setOnClickListener { finish() }

        val uriString = intent?.getStringExtra(EXTRA_IMAGE_URI)
        if (uriString == null) {
            finish()
            return
        }

        sourceImageUri = if (uriString.startsWith("content://") || uriString.startsWith("file://")) uriString.toUri() else Uri.fromFile(File(uriString))

        // register crop launcher
        cropLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.let { data ->
                    val resultUri = UCrop.getOutput(data)
                    if (resultUri != null) {
                        // replace originalBitmap with cropped content
                        val cropped = loadBitmap(resultUri.toString())
                        if (cropped != null) {
                            originalBitmap = cropped
                            applyCurrentFilter()
                        }
                    }
                }
            }
        }

        val bitmap = loadBitmap(uriString)
        if (bitmap == null) {
            Toast.makeText(this, R.string.error_loading_image, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        originalBitmap = bitmap
        workingBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        imageView.setImageBitmap(workingBitmap)

        setupFilters()

        // Apply status bar inset to the top mode buttons container so it's below system status bar
        val modeContainer = findViewById<View>(R.id.modeButtonsContainer)
        modeContainerOriginalPaddingTop = modeContainer.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(modeContainer) { v, insets ->
            val statusInset = insets.getInsets(Type.statusBars()).top
            v.setPadding(v.paddingLeft, modeContainerOriginalPaddingTop + statusInset, v.paddingRight, v.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(modeContainer)

        // Initialize mode buttons behavior
        updateModeButtonsUI()
        effectsButtonEdit.setOnClickListener {
            isEffectsMode = true
            isRetouchMode = false
            showFilters()
            updateModeButtonsUI()
        }
        retouchButtonEdit.setOnClickListener {
            isEffectsMode = false
            isRetouchMode = true
            showRetouchOptions()
            updateModeButtonsUI()
            // removed automatic showRetouchDialog() so tapping Retouch won't open the slider
        }

        btnSave.setOnClickListener { saveEditedImage() }
        btnClose.setOnClickListener { hideGalleryOverlay(); finish() }
    }

    private fun setupFilters() {
        // initialize carousel initially with filters
        showFilters()
    }

    private fun showFilters() {
        val filters = listOf(
            Filter.ORIGINAL,
            Filter.SWEET,
            Filter.PASTEL,
            Filter.BLOOM,
            Filter.VINTAGE,
            Filter.MONO,
            Filter.SEPIA,
            Filter.VIBRANT,
            Filter.CINEMATIC,
            Filter.COOL,
            Filter.WARM,
            Filter.FADE
        )
        filterCarousel.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        val adapter = FilterAdapter(filters) { filter ->
            currentFilter = filter
            applyCurrentFilter()
        }
        filterCarousel.adapter = adapter
    }

    private fun showRetouchOptions() {
        filterCarousel.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        val adapter = RetouchAdapter(retouchItems) { opt ->
            onRetouchOptionSelected(opt)
        }
        filterCarousel.adapter = adapter
    }

    // Apply current selected filter and brightness to originalBitmap and update workingBitmap/imageView
    private fun applyCurrentFilter() {
        val orig = originalBitmap ?: return
        val result = createBitmap(orig.width, orig.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val matrix = currentFilter.matrix()
        if (brightnessValue != 0f) {
            val translate = ColorMatrix().apply {
                set(floatArrayOf(
                    1f, 0f, 0f, 0f, brightnessValue,
                    0f, 1f, 0f, 0f, brightnessValue,
                    0f, 0f, 1f, 0f, brightnessValue,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            matrix.postConcat(translate)
        }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix); isFilterBitmap = true }
        canvas.drawBitmap(orig, 0f, 0f, paint)
        workingBitmap?.recycle()
        workingBitmap = result
        imageView.setImageBitmap(workingBitmap)
    }

    // Subtle enhance: small contrast + saturation boost
    private fun applyEnhance(src: Bitmap): Bitmap {
        val cm = ColorMatrix().apply {
            set(floatArrayOf(
                1.06f, 0f, 0f, 0f, 6f,
                0f, 1.06f, 0f, 0f, 6f,
                0f, 0f, 1.06f, 0f, 6f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorMatrix(src, cm)
    }

    // Mild sharpen-like effect (implemented as a contrast boost placeholder)
    private fun applySharpen(src: Bitmap): Bitmap {
        val cm = ColorMatrix().apply {
            set(floatArrayOf(
                1.09f, 0f, 0f, 0f, 0f,
                0f, 1.09f, 0f, 0f, 0f,
                0f, 0f, 1.09f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        return applyColorMatrix(src, cm)
    }

    // Saturation that doesn't recycle source (used for preview/immutable operations)
    private fun applySaturationImmutable(src: Bitmap, sat: Float): Bitmap {
        val cm = ColorMatrix().apply { setSaturation(sat) }
        val result = createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm); isFilterBitmap = true }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    private fun onRetouchOptionSelected(opt: RetouchOption) {
        when (opt) {
            RetouchOption.CROP -> {
                // start UCrop with sourceImageUri if available
                val src = sourceImageUri ?: run {
                    Toast.makeText(this, R.string.error_loading_image, Toast.LENGTH_SHORT).show()
                    return
                }
                val destFile = File(cacheDir, "crop_${System.currentTimeMillis()}.jpg")
                val destUri = Uri.fromFile(destFile)
                val uCrop = UCrop.of(src, destUri)
                    .withMaxResultSize(4000, 4000)
                    .withAspectRatio(0f, 0f)
                val intent = uCrop.getIntent(this)
                cropLauncher.launch(intent)
            }
            RetouchOption.ENHANCE -> {
                // subtle auto-enhance: slightly boost contrast and saturation
                originalBitmap = originalBitmap?.let { applyEnhance(it) }
                applyCurrentFilter()
            }
            RetouchOption.SHARPEN -> {
                originalBitmap = originalBitmap?.let { applySharpen(it) }
                applyCurrentFilter()
            }
            RetouchOption.SATURATION -> showSliderDialog(R.string.retouch_saturation, 100,
                { progress ->
                    // live preview
                    val sat = progress / 100f
                    workingBitmap?.recycle()
                    workingBitmap = originalBitmap?.let { applySaturationImmutable(it, sat) }
                    imageView.setImageBitmap(workingBitmap)
                },
                { final ->
                    originalBitmap = originalBitmap?.let { applySaturationImmutable(it, final / 100f) }
                    applyCurrentFilter()
                }
            )
            RetouchOption.BRIGHTNESS -> showSliderDialog(R.string.retouch_brightness, (brightnessValue + 100f).toInt(),
                { progress ->
                    brightnessValue = (progress - 100).toFloat()
                    applyCurrentFilter()
                },
                { _ -> /* committed already applied */ }
            )
            RetouchOption.EXPOSURE -> showSliderDialog(R.string.retouch_exposure, 100,
                { p ->
                    val exposure = (p - 100) / 50f // -2..+2
                    workingBitmap?.recycle()
                    workingBitmap = originalBitmap?.let { applyExposure(it, exposure) }
                    imageView.setImageBitmap(workingBitmap)
                },
                { final -> originalBitmap = originalBitmap?.let { applyExposure(it, (final - 100) / 50f) }; applyCurrentFilter() }
            )
            RetouchOption.CONTRAST -> showSliderDialog(R.string.retouch_contrast, 100,
                { p ->
                    val c = p / 100f // 0..2
                    workingBitmap?.recycle()
                    workingBitmap = originalBitmap?.let { applyContrast(it, c) }
                    imageView.setImageBitmap(workingBitmap)
                },
                { final -> originalBitmap = originalBitmap?.let { applyContrast(it, final / 100f) }; applyCurrentFilter() }
            )
            RetouchOption.TEMPERATURE -> showSliderDialog(R.string.retouch_temperature, 100,
                { p ->
                    val t = (p - 100) / 50f // -2..+2
                    workingBitmap?.recycle()
                    workingBitmap = originalBitmap?.let { applyTemperature(it, t) }
                    imageView.setImageBitmap(workingBitmap)
                },
                { final -> originalBitmap = originalBitmap?.let { applyTemperature(it, (final - 100) / 50f) }; applyCurrentFilter() }
            )
            RetouchOption.TINT -> showSliderDialog(R.string.retouch_tint, 100,
                { p ->
                    val tt = (p - 100) / 100f
                    workingBitmap?.recycle()
                    workingBitmap = originalBitmap?.let { applyTint(it, tt) }
                    imageView.setImageBitmap(workingBitmap)
                },
                { final -> originalBitmap = originalBitmap?.let { applyTint(it, (final - 100) / 100f) }; applyCurrentFilter() }
            )
            RetouchOption.HIGHLIGHTS -> showSliderDialog(R.string.retouch_highlights, 100,
                { p ->
                    // simple emulate: adjust brightness on highlights by scaling (placeholder)
                    val f = p / 100f
                    workingBitmap?.recycle()
                    workingBitmap = originalBitmap?.let { applyHighlights(it, f) }
                    imageView.setImageBitmap(workingBitmap)
                },
                { final -> originalBitmap = originalBitmap?.let { applyHighlights(it, final / 100f) }; applyCurrentFilter() }
            )
            RetouchOption.SHADOWS -> showSliderDialog(R.string.retouch_shadows, 100,
                { p ->
                    val f = p / 100f
                    workingBitmap?.recycle()
                    workingBitmap = originalBitmap?.let { applyShadows(it, f) }
                    imageView.setImageBitmap(workingBitmap)
                },
                { final -> originalBitmap = originalBitmap?.let { applyShadows(it, final / 100f) }; applyCurrentFilter() }
            )
            RetouchOption.VIGNETTE -> showSliderDialog(R.string.retouch_vignette, 0,
                { p ->
                    val v = p / 100f
                    workingBitmap?.recycle()
                    workingBitmap = originalBitmap?.let { applyVignette(it, v) }
                    imageView.setImageBitmap(workingBitmap)
                },
                { final -> originalBitmap = originalBitmap?.let { applyVignette(it, final / 100f) }; applyCurrentFilter() }
            )
            RetouchOption.SMOOTH -> {
                originalBitmap = originalBitmap?.let { applySmooth(it) }
                applyCurrentFilter()
            }
            RetouchOption.CLARITY -> {
                originalBitmap = originalBitmap?.let { applyClarity(it) }
                applyCurrentFilter()
            }
            RetouchOption.ROTATE -> {
                originalBitmap = originalBitmap?.let { rotateBitmap(it, 90f) }
                applyCurrentFilter()
            }
            RetouchOption.FLIP -> {
                originalBitmap = originalBitmap?.let { flipBitmap(it) }
                applyCurrentFilter()
            }
            RetouchOption.AUTO -> {
                // alias to enhance
                originalBitmap = originalBitmap?.let { applyEnhance(it) }
                applyCurrentFilter()
            }
        }
    }

    // Generic slider dialog helper: titleRes, initialProgress, onChange(progress), onCommit(progress)
    private fun showSliderDialog(titleRes: Int, initialProgress: Int, onChange: (Int) -> Unit, onCommit: (Int) -> Unit) {
        val seekBar = SeekBar(this).apply { max = 200; progress = initialProgress }
        val dialog = AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setView(seekBar)
            .setPositiveButton(R.string.save) { _, _ -> onCommit(seekBar.progress) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) { onChange(progress) }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        dialog.show()
    }

    // Simple implementations of new retouch transforms (fast, approximate)
    private fun applyExposure(src: Bitmap, exposure: Float): Bitmap {
        // exposure: -2..+2 mapped to translate brightness
        val translateVal = exposure * 20f
        val cm = ColorMatrix().apply { set(floatArrayOf(
            1f,0f,0f,0f,translateVal,
            0f,1f,0f,0f,translateVal,
            0f,0f,1f,0f,translateVal,
            0f,0f,0f,1f,0f
        )) }
        return applyColorMatrix(src, cm)
    }

    private fun applyContrast(src: Bitmap, contrast: Float): Bitmap {
        val scale = contrast
        val translate = (-0.5f * scale + 0.5f) * 255f
        val cm = ColorMatrix().apply { set(floatArrayOf(
            scale,0f,0f,0f,translate,
            0f,scale,0f,0f,translate,
            0f,0f,scale,0f,translate,
            0f,0f,0f,1f,0f
        )) }
        return applyColorMatrix(src, cm)
    }

    private fun applyTemperature(src: Bitmap, temp: Float): Bitmap {
        // temp approx: shift red/blue gains
        val rScale = 1f + temp * 0.1f
        val bScale = 1f - temp * 0.1f
        val cm = ColorMatrix().apply { set(floatArrayOf(
            rScale,0f,0f,0f,0f,
            0f,1f,0f,0f,0f,
            0f,0f,bScale,0f,0f,
            0f,0f,0f,1f,0f
        )) }
        return applyColorMatrix(src, cm)
    }

    private fun applyTint(src: Bitmap, tint: Float): Bitmap {
        // tint: positive -> more magenta, negative -> more green
        val rScale = 1f + tint * 0.05f
        val gScale = 1f - tint * 0.05f
        val bScale = 1f + tint * 0.02f
        val cm = ColorMatrix().apply { set(floatArrayOf(
            rScale,0f,0f,0f,0f,
            0f,gScale,0f,0f,0f,
            0f,0f,bScale,0f,0f,
            0f,0f,0f,1f,0f
        )) }
        return applyColorMatrix(src, cm)
    }

    private fun applyHighlights(src: Bitmap, f: Float): Bitmap {
        // placeholder: slightly brighten overall with f factor
        val cm = ColorMatrix().apply { set(floatArrayOf(
            1f,0f,0f,0f, f * 10f,
            0f,1f,0f,0f, f * 10f,
            0f,0f,1f,0f, f * 10f,
            0f,0f,0f,1f,0f
        )) }
        return applyColorMatrix(src, cm)
    }

    private fun applyShadows(src: Bitmap, f: Float): Bitmap {
        // placeholder: slightly lift shadows by a small gamma-like scale
        val cm = ColorMatrix().apply { set(floatArrayOf(
            1f,0f,0f,0f, -f * 10f,
            0f,1f,0f,0f, -f * 10f,
            0f,0f,1f,0f, -f * 10f,
            0f,0f,0f,1f,0f
        )) }
        return applyColorMatrix(src, cm)
    }

    private fun applyVignette(src: Bitmap, strength: Float): Bitmap {
        val w = src.width
        val h = src.height
        val result = createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(src, 0f, 0f, null)
        if (strength <= 0f) return result
        try {
            val paint = Paint()
            val radius = Math.hypot(w.toDouble()/2.0, h.toDouble()/2.0).toFloat()
            val shader = android.graphics.RadialGradient(
                (w/2).toFloat(), (h/2).toFloat(), radius,
                intArrayOf(0x00000000, 0x7f000000), floatArrayOf(0.5f, 1f), android.graphics.Shader.TileMode.CLAMP)
            paint.shader = shader
            paint.alpha = (strength * 200).toInt().coerceIn(0,255)
            canvas.drawRect(0f,0f,w.toFloat(),h.toFloat(), paint)
        } catch (_: Exception) {}
        return result
    }

    private fun applySmooth(src: Bitmap): Bitmap {
        // placeholder: slight desaturation + blur-like effect via scaled draw
        val small = Bitmap.createScaledBitmap(src, src.width/2, src.height/2, true)
        val result = createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(Bitmap.createScaledBitmap(small, src.width, src.height, true), 0f, 0f, null)
        small.recycle()
        return result
    }

    private fun applyClarity(src: Bitmap): Bitmap {
        // clarity ~ midtone contrast boost -> simple contrast increase
        return applyContrast(src, 1.08f)
    }

    private fun rotateBitmap(src: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        src.recycle()
        return out
    }

    private fun flipBitmap(src: Bitmap): Bitmap {
        val matrix = Matrix().apply { preScale(-1f, 1f) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        src.recycle()
        return out
    }

    private fun applyColorMatrix(src: Bitmap, cm: ColorMatrix): Bitmap {
        val result = createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm); isFilterBitmap = true }
        canvas.drawBitmap(src, 0f, 0f, paint)
        src.recycle()
        return result
    }

    private fun saveEditedImage() {
        val bmp = workingBitmap ?: return
        val prefs = getSharedPreferences("beauty_prefs", Context.MODE_PRIVATE)
        val replaceOriginal = prefs.getBoolean("replace_original", false)

        try {
            if (replaceOriginal) {
                // Try to overwrite the original file when possible (file:// or absolute path)
                val src = sourceImageUri
                val targetFile = when {
                    src == null -> null
                    src.scheme == "file" -> File(src.path ?: "")
                    else -> {
                        // if the source was passed as an absolute path string (no scheme) we stored it as file:// earlier
                        val s = src.path
                        if (s != null && File(s).exists()) File(s) else null
                    }
                }
                if (targetFile != null) {
                    try {
                        FileOutputStream(targetFile).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                        MediaScannerConnection.scanFile(this, arrayOf(targetFile.absolutePath), null, null)
                        val intent = Intent(this, PhotoPreviewActivity::class.java).apply {
                            putExtra(PhotoPreviewActivity.EXTRA_IMAGE_URI, targetFile.absolutePath)
                        }
                        startActivity(intent)
                        finish()
                        return
                    } catch (_: Exception) {
                        // If overwrite fails fall back to saving new file below
                    }
                }
            }

            // Fallback: save a new edited file (default behavior)
            val outDir = getOutputDirectory()
            val outFile = File(outDir, "edited_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            MediaScannerConnection.scanFile(this, arrayOf(outFile.absolutePath), null, null)
            // Open PhotoPreviewActivity with saved file path
            val fileUriString = Uri.fromFile(outFile).toString()
            val intent = Intent(this, PhotoPreviewActivity::class.java).apply {
                putExtra(PhotoPreviewActivity.EXTRA_IMAGE_URI, fileUriString)
            }
            startActivity(intent)
            finish()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadBitmap(uriString: String): Bitmap? {
        return try {
            when {
                uriString.startsWith("content://") -> {
                    val uri = uriString.toUri()
                    contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        val exif = ExifInterface(ByteArrayInputStream(bytes))
                        return rotateBitmapByExif(bmp, exif)
                    }
                }
                uriString.startsWith("file://") -> {
                    val uri = uriString.toUri()
                    val path = uri.path ?: return null
                    val bmp = BitmapFactory.decodeFile(path)
                    val exif = ExifInterface(path)
                    return rotateBitmapByExif(bmp, exif)
                }
                else -> {
                    // assume absolute file path
                    val bmp = BitmapFactory.decodeFile(uriString)
                    val exif = try { ExifInterface(uriString) } catch (_: Exception) { null }
                    return if (exif != null) rotateBitmapByExif(bmp, exif) else bmp
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun rotateBitmapByExif(bitmap: Bitmap?, exif: ExifInterface?): Bitmap? {
        if (bitmap == null || exif == null) return bitmap
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
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
        return try {
            if (matrix.isIdentity) {
                bitmap
            } else {
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                bitmap.recycle()
                rotated
            }
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    private fun updateModeButtonsUI() {
        if (isEffectsMode) {
            effectsButtonEdit.background = AppCompatResources.getDrawable(this, R.drawable.button_effects_background)
            effectsTextEdit.setTextColor(getColor(R.color.pink_selected))
            effectsTextEdit.textSize = 14f
            effectsTextEdit.typeface = android.graphics.Typeface.create(effectsTextEdit.typeface, android.graphics.Typeface.BOLD)

            retouchButtonEdit.background = AppCompatResources.getDrawable(this, R.drawable.button_retouch_background)
            retouchTextEdit.setTextColor(getColor(R.color.grey_dark))
            retouchTextEdit.textSize = 14f
            retouchTextEdit.typeface = android.graphics.Typeface.create(retouchTextEdit.typeface, android.graphics.Typeface.NORMAL)
        } else {
            retouchButtonEdit.background = AppCompatResources.getDrawable(this, R.drawable.button_effects_background)
            retouchTextEdit.setTextColor(getColor(R.color.pink_selected))
            retouchTextEdit.textSize = 14f
            retouchTextEdit.typeface = android.graphics.Typeface.create(retouchTextEdit.typeface, android.graphics.Typeface.BOLD)

            effectsButtonEdit.background = AppCompatResources.getDrawable(this, R.drawable.button_retouch_background)
            effectsTextEdit.setTextColor(getColor(R.color.grey_dark))
            effectsTextEdit.textSize = 14f
            effectsTextEdit.typeface = android.graphics.Typeface.create(effectsTextEdit.typeface, android.graphics.Typeface.NORMAL)
        }
    }

    private fun hideGalleryOverlay() {
        try {
            galleryImageOverlay.visibility = View.GONE
            galleryImageOverlay.setImageDrawable(null)
        } catch (_: Exception) {
            // ignore
        }
    }
}
