package com.soopersaiyan.filterify

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.io.File
import java.io.FileOutputStream

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
            // Try to resolve to a file if possible
            imageFile = uri.path?.let { File(it) }
            val input = contentResolver.openInputStream(uri)
            val bitmap = input?.use { BitmapFactory.decodeStream(it) }
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
