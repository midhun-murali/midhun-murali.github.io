package com.soopersaiyan.filterify

/**
 * Retouch options shown in the editor when retouch mode is active.
 */
enum class RetouchOption(val nameResId: Int) {
    CROP(R.string.retouch_crop),
    ENHANCE(R.string.retouch_enhance),
    SHARPEN(R.string.retouch_sharpen),
    SATURATION(R.string.retouch_saturation),
    BRIGHTNESS(R.string.retouch_brightness)
}
