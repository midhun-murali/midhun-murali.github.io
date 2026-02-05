package com.soopersaiyan.filterify

import android.graphics.ColorMatrix

/**
 * Shared Filter enum used across activities to avoid duplicate definitions.
 * Keeps identical values and matrix() helper as before.
 */
enum class Filter(val nameResId: Int, private val matrixValues: FloatArray) {
    ORIGINAL(R.string.filter_original, floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )),
    SWEET(R.string.filter_sweet, floatArrayOf(
        1.2f, 0.1f, 0.1f, 0f, 10f,
        0.1f, 1.1f, 0f, 0f, 5f,
        0.1f, 0.1f, 1.0f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )),
    PASTEL(R.string.filter_pastel, floatArrayOf(
        0.9f, 0.1f, 0.1f, 0f, 20f,
        0f, 0.9f, 0.1f, 0f, 20f,
        0.1f, 0.1f, 0.9f, 0f, 20f,
        0f, 0f, 0f, 1f, 0f
    )),
    BLOOM(R.string.filter_bloom, floatArrayOf(
        1.15f, 0.05f, 0.05f, 0f, 5f,
        0.05f, 1.15f, 0.05f, 0f, 5f,
        0.05f, 0.05f, 1.1f, 0f, 5f,
        0f, 0f, 0f, 1f, 0f
    )),
    VINTAGS(R.string.filter_vintags, floatArrayOf(
        0.627f, 0.320f, -0.039f, 0f, 9.651f,
        0.025f, 0.644f, 0.130f, 0f, 7.163f,
        0.046f, -0.085f, 0.524f, 0f, 5.159f,
        0f, 0f, 0f, 1f, 0f
    )),
    MONO(R.string.filter_mono, floatArrayOf(
        0.299f, 0.587f, 0.114f, 0f, 0f,
        0.299f, 0.587f, 0.114f, 0f, 0f,
        0.299f, 0.587f, 0.114f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ));

    fun matrix() = ColorMatrix(matrixValues)
}
