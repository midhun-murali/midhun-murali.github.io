package com.soopersaiyan.filterify

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
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayInputStream

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
    private val retouchItems = listOf(
        RetouchOption.CROP,
        RetouchOption.ENHANCE,
        RetouchOption.SHARPEN,
        RetouchOption.SATURATION,
        RetouchOption.BRIGHTNESS
    )
    private lateinit var effectsButtonEdit: LinearLayout
    private lateinit var retouchButtonEdit: LinearLayout
    private lateinit var effectsTextEdit: TextView
    private lateinit var retouchTextEdit: TextView
    private var modeContainerOriginalPaddingTop = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_photo_edit)

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

    private fun onRetouchOptionSelected(opt: RetouchOption) {
        when (opt) {
            RetouchOption.CROP -> {
                // simple placeholder: show a toast (full crop UI is out of scope)
                Toast.makeText(this, getString(R.string.retouch_crop), Toast.LENGTH_SHORT).show()
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
            RetouchOption.SATURATION -> {
                showSaturationDialog()
            }
            RetouchOption.BRIGHTNESS -> {
                showBrightnessDialog()
            }
        }
    }

    private fun applyCurrentFilter() {
        val orig = originalBitmap ?: return
        // Create a new bitmap applying color matrix + brightness
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

    private fun showBrightnessDialog() {
        val seekBar = SeekBar(this).apply {
            max = 200
            progress = (brightnessValue + 100f).toInt()
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.retouch_brightness)
            .setView(seekBar)
            .setPositiveButton(R.string.save) { _, _ ->
                // save brightnessValue and apply
                brightnessValue = (seekBar.progress - 100).toFloat()
                applyCurrentFilter()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                brightnessValue = (progress - 100).toFloat()
                applyCurrentFilter()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        dialog.show()
    }

    // Apply a subtle enhance (contrast + warm lift) and return a new bitmap (recycles input)
    private fun applyEnhance(bmp: Bitmap): Bitmap {
        val result = createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val cm = ColorMatrix().apply {
            set(floatArrayOf(
                1.06f, 0f, 0f, 0f, 6f,
                0f, 1.06f, 0f, 0f, 6f,
                0f, 0f, 1.06f, 0f, 6f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm); isFilterBitmap = true }
        canvas.drawBitmap(bmp, 0f, 0f, paint)
        if (!bmp.isRecycled) bmp.recycle()
        return result
    }

    // Apply a mild sharpen-like effect (implemented as slight contrast boost) and return new bitmap (recycles input)
    private fun applySharpen(bmp: Bitmap): Bitmap {
        val result = createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val cm = ColorMatrix().apply {
            set(floatArrayOf(
                1.09f, 0f, 0f, 0f, 0f,
                0f, 1.09f, 0f, 0f, 0f,
                0f, 0f, 1.09f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm); isFilterBitmap = true }
        canvas.drawBitmap(bmp, 0f, 0f, paint)
        if (!bmp.isRecycled) bmp.recycle()
        return result
    }

    // Apply saturation to a bitmap and return a new bitmap without recycling the source (used for interactive preview)
    private fun applySaturationImmutable(src: Bitmap, sat: Float): Bitmap {
        val cm = ColorMatrix().apply { setSaturation(sat) }
        val result = createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm); isFilterBitmap = true }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return result
    }

    private fun showSaturationDialog() {
        val seekBar = SeekBar(this).apply { max = 200; progress = 100 }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.retouch_saturation)
            .setView(seekBar)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        // Keep a snapshot of the current original so we don't permanently alter it during preview
        val base = originalBitmap ?: return
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val sat = progress / 100f
                // create preview from base without mutating base
                val preview = applySaturationImmutable(base, sat)
                workingBitmap?.recycle()
                workingBitmap = preview
                imageView.setImageBitmap(workingBitmap)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                // when user stops, commit the change to originalBitmap
                val finalSat = seekBar.progress / 100f
                originalBitmap = applySaturationImmutable(base, finalSat)
                applyCurrentFilter()
            }
        })
        dialog.show()
    }

    private fun saveEditedImage() {
        val bmp = workingBitmap ?: return
        val outDir = getOutputDirectory()
        val outFile = File(outDir, "edited_${System.currentTimeMillis()}.jpg")
        try {
            FileOutputStream(outFile).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
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
