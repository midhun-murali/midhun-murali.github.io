package com.soopersaiyan.filterify

/**
 * Retouch options shown in the editor when retouch mode is active.
 */
enum class RetouchOption(val nameResId: Int) {
    CROP(R.string.retouch_crop),
    ENHANCE(R.string.retouch_enhance),
    ROTATE(R.string.retouch_rotate),
    FLIP(R.string.retouch_flip),
    AUTO(R.string.retouch_enhance),
    EXPOSURE(R.string.retouch_exposure),
    CONTRAST(R.string.retouch_contrast),
    SATURATION(R.string.retouch_saturation),
    BRIGHTNESS(R.string.retouch_brightness),
    SHARPEN(R.string.retouch_sharpen),
    TEMPERATURE(R.string.retouch_temperature),
    TINT(R.string.retouch_tint),
    HIGHLIGHTS(R.string.retouch_highlights),
    SHADOWS(R.string.retouch_shadows),
    VIGNETTE(R.string.retouch_vignette),
    SMOOTH(R.string.retouch_smooth),
    CLARITY(R.string.retouch_clarity)
}
