package com.beauty.camera.selfie.camera.filter

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var switchReplaceOriginal: SwitchCompat
    private lateinit var versionLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        btnBack.setOnClickListener { finish() }

        switchReplaceOriginal = findViewById(R.id.switch_replace_original)
        versionLabel = findViewById(R.id.version_label)

        // Load saved preference
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saveOriginal = prefs.getBoolean(PREF_REPLACE_ORIGINAL, true)
        switchReplaceOriginal.isChecked = saveOriginal

        switchReplaceOriginal.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(PREF_REPLACE_ORIGINAL, isChecked).apply()
        }

        // Show version from package info if possible
        val version = try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0.0"
        }
        versionLabel.text = "Version $version"
    }

    companion object {
        private const val PREFS_NAME = "beauty_prefs"
        private const val PREF_REPLACE_ORIGINAL = "replace_original"

        fun start(context: Context) {
            val i = Intent(context, SettingsActivity::class.java)
            context.startActivity(i)
        }
    }
}

