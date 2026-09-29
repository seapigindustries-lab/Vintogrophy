package com.vintogrophy.app.filter

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ColorMatrixUniformTest {

    @Test
    fun `transposes row-major 4x5 into column-major mat4`() {
        // Row-major: row r is [5r, 5r+1, 5r+2, 5r+3, 5r+4]
        val values = FloatArray(20) { it.toFloat() }
        val uniform = ColorMatrixUniform(values)

        // color[col * 4 + row] == values[row * 5 + col]
        for (row in 0..3) {
            for (col in 0..3) {
                assertEquals(
                    "color[row=$row, col=$col]",
                    values[row * 5 + col],
                    uniform.color[col * 4 + row],
                    0f,
                )
            }
        }
    }

    @Test
    fun `extracts the offset column`() {
        val values = FloatArray(20) { it.toFloat() }
        val uniform = ColorMatrixUniform(values)
        assertArrayEquals(
            floatArrayOf(4f, 9f, 14f, 19f),
            uniform.offset,
            0f,
        )
    }

    @Test
    fun `identity matrix maps to identity color and zero offset`() {
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val uniform = ColorMatrixUniform(identity)

        assertArrayEquals(
            floatArrayOf(
                1f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f,
                0f, 0f, 1f, 0f,
                0f, 0f, 0f, 1f,
            ),
            uniform.color,
            0f,
        )
        assertArrayEquals(floatArrayOf(0f, 0f, 0f, 0f), uniform.offset, 0f)
    }

    @Test
    fun `rejects matrices that are not 20 values`() {
        assertThrows(IllegalArgumentException::class.java) {
            ColorMatrixUniform(FloatArray(19))
        }
    }

    @Test
    fun `every photo filter produces a valid uniform`() {
        PhotoFilter.entries.forEach { filter ->
            val uniform = ColorMatrixUniform(filter.values)
            assertEquals(16, uniform.color.size)
            assertEquals(4, uniform.offset.size)
            uniform.color.forEach { assertTrue(it.isFinite()) }
            uniform.offset.forEach { assertTrue(it.isFinite()) }
        }
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
