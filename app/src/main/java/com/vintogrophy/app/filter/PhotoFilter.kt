package com.vintogrophy.app.filter

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

enum class PhotoFilter(val label: String, val values: FloatArray) {
    NONE(
        "Original",
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),

    MONO(
        "Mono",
        floatArrayOf(
            0.213f, 0.715f, 0.072f, 0f, 0f,
            0.213f, 0.715f, 0.072f, 0f, 0f,
            0.213f, 0.715f, 0.072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),

    SEPIA(
        "Sepia",
        floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),

    VINTAGE(
        "Vintage",
        floatArrayOf(
            0.9f, 0.5f, 0.1f, 0f, 10f,
            0.3f, 0.8f, 0.1f, 0f, 5f,
            0.2f, 0.3f, 0.6f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),

    COOL(
        "Cool",
        floatArrayOf(
            0.9f, 0f, 0f, 0f, 0f,
            0f, 0.95f, 0f, 0f, 0f,
            0f, 0f, 1.15f, 0f, 15f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),

    WARM(
        "Warm",
        floatArrayOf(
            1.15f, 0f, 0f, 0f, 10f,
            0f, 1.0f, 0f, 0f, 5f,
            0f, 0f, 0.9f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    );

    fun colorMatrix(): ColorMatrix = ColorMatrix(values.copyOf())

    fun colorFilter(): ColorMatrixColorFilter = ColorMatrixColorFilter(colorMatrix())
}
