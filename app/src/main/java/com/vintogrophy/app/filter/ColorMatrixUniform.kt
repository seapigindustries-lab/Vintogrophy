package com.vintogrophy.app.filter

/**
 * Upload-ready representation of a 4x5 Android [android.graphics.ColorMatrix]
 * for GLSL: a column-major mat4 plus a vec4 offset.
 *
 * GLSL computes: `out = uColor * color + uOffset`, which expands to the
 * standard row-major Android matrix layout when columns/offset are built here.
 */
class ColorMatrixUniform(values: FloatArray) {

    init {
        require(values.size == 20) { "Color matrix must have 20 values, was ${values.size}" }
    }

    /** Column-major 4x4: color[col * 4 + row] == values[row * 5 + col] (columns 0..3). */
    val color: FloatArray = FloatArray(16).also { out ->
        for (row in 0..3) {
            for (col in 0..3) {
                out[col * 4 + row] = values[row * 5 + col]
            }
        }
    }

    /** Offset term: offset[row] == values[row * 5 + 4]. */
    val offset: FloatArray = FloatArray(4).also { out ->
        for (row in 0..3) {
            out[row] = values[row * 5 + 4]
        }
    }
}
