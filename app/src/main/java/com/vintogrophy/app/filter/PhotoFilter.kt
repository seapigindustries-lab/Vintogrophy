package com.vintogrophy.app.filter

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter

enum class PhotoFilter(val label: String) {
    NONE("Original"),
    MONO("Mono"),
    SEPIA("Sepia"),
    VINTAGE("Vintage"),
    COOL("Cool"),
    WARM("Warm");

    fun colorMatrix(): ColorMatrix = when (this) {
        NONE -> ColorMatrix()

        MONO -> ColorMatrix().apply { setSaturation(0f) }

        SEPIA -> ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )

        VINTAGE -> ColorMatrix(
            floatArrayOf(
                0.9f, 0.5f, 0.1f, 0f, 10f,
                0.3f, 0.8f, 0.1f, 0f, 5f,
                0.2f, 0.3f, 0.6f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )

        COOL -> ColorMatrix(
            floatArrayOf(
                0.9f, 0f, 0f, 0f, 0f,
                0f, 0.95f, 0f, 0f, 0f,
                0f, 0f, 1.15f, 0f, 15f,
                0f, 0f, 0f, 1f, 0f,
            )
        )

        WARM -> ColorMatrix(
            floatArrayOf(
                1.15f, 0f, 0f, 0f, 10f,
                0f, 1.0f, 0f, 0f, 5f,
                0f, 0f, 0.9f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    }

    fun colorFilter(): ColorMatrixColorFilter = ColorMatrixColorFilter(colorMatrix())
}
