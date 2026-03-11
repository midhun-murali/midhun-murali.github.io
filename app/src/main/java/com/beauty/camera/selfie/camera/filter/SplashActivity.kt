package com.beauty.camera.selfie.camera.filter

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import android.app.AlertDialog
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.FullScreenContentCallback

class SplashActivity : AppCompatActivity() {

    private var appOpenAd: AppOpenAd? = null
    private var loadingDialog: AlertDialog? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        // Reset to the normal app theme so activity content uses the regular theme
        setTheme(R.style.Theme_Filterify)
        setContentView(R.layout.activity_splash)

        // Initialize Mobile Ads
        MobileAds.initialize(this) {}

        // Show a loading dialog while requesting the app-open interstitial
        showLoadingDialog()

        // Load App Open ad (use the app open id configured in strings)
        val adUnit = getString(R.string.admob_app_open_id)
        val request = AdRequest.Builder().build()
        // AppOpenAd requires an orientation parameter; use PORTRAIT for splash
        AppOpenAd.load(this, adUnit, request, AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT, object : AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) {
                appOpenAd = ad
                hideLoadingDialog()
                try {
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            appOpenAd = null
                            launchMainAndFinish()
                        }

                        override fun onAdFailedToShowFullScreenContent(p0: com.google.android.gms.ads.AdError) {
                            appOpenAd = null
                            launchMainAndFinish()
                        }

                        override fun onAdShowedFullScreenContent() {
                            // no-op
                        }
                    }
                    ad.show(this@SplashActivity)
                } catch (_: Exception) {
                    // show failed -> continue
                    launchMainAndFinish()
                }
            }

            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                appOpenAd = null
                hideLoadingDialog()
                // fallback: wait the same splash duration then continue
                handler.postDelayed({ launchMainAndFinish() }, 1200)
            }
        })

        // Safety: if ad hasn't loaded within 4s, hide dialog and continue
        handler.postDelayed({
            if (appOpenAd == null) {
                hideLoadingDialog()
                launchMainAndFinish()
            }
        }, 4000)
    }

    private fun showLoadingDialog() {
        if (loadingDialog?.isShowing == true) return
        try {
            loadingDialog = AlertDialog.Builder(this)
                .setMessage(getString(R.string.loading))
                .setCancelable(false)
                .create()
            loadingDialog?.show()
        } catch (_: Exception) { loadingDialog = null }
    }

    private fun hideLoadingDialog() {
        try { loadingDialog?.dismiss() } catch (_: Exception) {}
        loadingDialog = null
    }

    private fun launchMainAndFinish() {
        try {
            startActivity(Intent(this, MainActivity::class.java))
        } catch (_: Exception) { }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
