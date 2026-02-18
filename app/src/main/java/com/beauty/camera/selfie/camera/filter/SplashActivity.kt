package com.beauty.camera.selfie.camera.filter

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        // Reset to the normal app theme so activity content uses the regular theme
        setTheme(R.style.Theme_Filterify)
        setContentView(R.layout.activity_splash)

        // Keep splash for 1200ms then open MainActivity
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }, 1200)
    }
}
