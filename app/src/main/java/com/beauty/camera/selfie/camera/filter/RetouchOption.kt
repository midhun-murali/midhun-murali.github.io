package com.beauty.camera.selfie.camera.filter

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
    CLARITY(R.string.retouch_clarity),

    // Preset/Photo-style quick options (as shown in provided mock)
    PRESET_NATURAL(R.string.retouch_preset_natural),
    PRESET_SOFT_GLOW(R.string.retouch_preset_soft_glow),
    PRESET_GLAM(R.string.retouch_preset_glam),
    PRESET_CLEAR_SKIN(R.string.retouch_preset_clear_skin),
    PRESET_ALL_IN_ONE(R.string.retouch_preset_all_in_one)
}
