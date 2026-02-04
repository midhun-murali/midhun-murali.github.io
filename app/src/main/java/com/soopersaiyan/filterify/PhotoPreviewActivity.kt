package com.soopersaiyan.filterify

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsCompat.Type
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import androidx.exifinterface.media.ExifInterface

class PhotoPreviewActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var btnDelete: ImageButton
    private lateinit var btnSave: ImageButton
    private lateinit var btnShare: ImageButton
    private lateinit var progress: ProgressBar

    private var imageUri: Uri? = null
    private var imageFile: File? = null
    private var previewOriginalPaddingTop = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_photo_preview)

        imageView = findViewById(R.id.previewImage)
        btnDelete = findViewById(R.id.btnDelete)
        btnSave = findViewById(R.id.btnSave)
        btnShare = findViewById(R.id.btnShare)
        progress = findViewById(R.id.progress)

        // Back button at top-left
        val btnBack = findViewById<ImageButton?>(R.id.btnBackPreview)
        btnBack?.setOnClickListener { finish() }

        // Apply status bar inset to back button to keep it below status bar
        btnBack?.let { btn ->
            val origTop = btn.paddingTop
            ViewCompat.setOnApplyWindowInsetsListener(btn) { v, insets ->
                val statusInset = insets.getInsets(Type.statusBars()).top
                v.setPadding(v.paddingLeft, origTop + statusInset, v.paddingRight, v.paddingBottom)
                insets
            }
            ViewCompat.requestApplyInsets(btn)
            // ensure the back button is drawn above the preview
            btn.bringToFront()
        }

        // Apply status bar inset to the preview image so it doesn't sit under the status bar
        previewOriginalPaddingTop = imageView.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(imageView) { v, insets ->
            val statusInset = insets.getInsets(Type.statusBars()).top
            v.setPadding(v.paddingLeft, previewOriginalPaddingTop + statusInset, v.paddingRight, v.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(imageView)

        imageUri = intent?.getStringExtra(EXTRA_IMAGE_URI)?.let { Uri.parse(it) }
        if (imageUri == null) {
            finish()
            return
        }

        loadImage()

        btnDelete.setOnClickListener { deleteImage() }
        btnSave.setOnClickListener { saveImageToAppDir() }
        btnShare.setOnClickListener { shareImage() }
    }

    private fun loadImage() {
        progress.visibility = View.VISIBLE
        try {
            val uri = imageUri ?: return
            val fileFromUri = uri.path?.let { File(it) }
            var bitmap: Bitmap? = null
            if (uri.scheme == "file" && fileFromUri != null && fileFromUri.exists()) {
                imageFile = fileFromUri
                val bmp = BitmapFactory.decodeFile(fileFromUri.absolutePath)
                val exif = try { ExifInterface(fileFromUri.absolutePath) } catch (_: Exception) { null }
                bitmap = rotateBitmapByExif(bmp, exif)
            } else if (uri.scheme == "content") {
                val inputBytes = try { contentResolver.openInputStream(uri)?.use { it.readBytes() } } catch (_: Exception) { null }
                if (inputBytes != null) {
                    val bmp = BitmapFactory.decodeByteArray(inputBytes, 0, inputBytes.size)
                    val exif = try { ExifInterface(ByteArrayInputStream(inputBytes)) } catch (_: Exception) { null }
                    bitmap = rotateBitmapByExif(bmp, exif)
                }
                // fallback: if content couldn't be decoded but fileFromUri exists
                if (bitmap == null && fileFromUri != null && fileFromUri.exists()) {
                    imageFile = fileFromUri
                    val bmp = BitmapFactory.decodeFile(fileFromUri.absolutePath)
                    val exif = try { ExifInterface(fileFromUri.absolutePath) } catch (_: Exception) { null }
                    bitmap = rotateBitmapByExif(bmp, exif)
                }
            } else {
                // assume absolute file path
                val path = uri.path ?: return
                val bmp = BitmapFactory.decodeFile(path)
                val exif = try { ExifInterface(path) } catch (_: Exception) { null }
                bitmap = rotateBitmapByExif(bmp, exif)
            }

            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
            } else {
                Toast.makeText(this, R.string.error_loading_image, Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_loading_image, Toast.LENGTH_SHORT).show()
            finish()
        } finally {
            progress.visibility = View.GONE
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
            if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {
            bitmap
        }
    }

    private fun deleteImage() {
        val uri = imageUri ?: return
        try {
            val file = imageFile
            if (file != null && file.exists()) {
                val ok = file.delete()
                if (ok) {
                    Toast.makeText(this, R.string.deleted, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, R.string.delete_failed, Toast.LENGTH_SHORT).show()
                }
            } else {
                // Try deleting via content resolver
                contentResolver.delete(uri, null, null)
                Toast.makeText(this, R.string.deleted, Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.delete_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImageToAppDir() {
        val uri = imageUri ?: return
        try {
            val input = contentResolver.openInputStream(uri) ?: return
            val outFile = File(getExternalFilesDir(null), "saved_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { input.copyTo(it) }
            Toast.makeText(this, getString(R.string.photo_saved, outFile.absolutePath), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareImage() {
        val uri = imageUri ?: return
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = contentResolver.getType(uri) ?: "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.share_failed, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
    }
}
