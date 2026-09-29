package com.vintogrophy.app.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoFilterTest {

    @Test
    fun `every filter has a 4x5 matrix of finite values`() {
        PhotoFilter.entries.forEach { filter ->
            assertEquals("${filter.label} matrix size", 20, filter.values.size)
            filter.values.forEach { value ->
                assertTrue("${filter.label} contains non-finite value", value.isFinite())
            }
        }
    }

    @Test
    fun `none is the identity matrix`() {
        val expected = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        expected.forEachIndexed { i, v ->
            assertEquals("identity[$i]", v, PhotoFilter.NONE.values[i], 0f)
        }
    }

    @Test
    fun `labels are unique and non-blank`() {
        val labels = PhotoFilter.entries.map { it.label }
        assertEquals(labels.toSet().size, labels.size)
        labels.forEach { assertTrue(it.isNotBlank()) }
    }

    @Test
    fun `mono maps every channel to the same grayscale weights`() {
        val v = PhotoFilter.MONO.values
        for (col in 0..2) {
            assertEquals("row0[$col]", v[col], v[5 + col], 0f)
            assertEquals("row1[$col]", v[col], v[10 + col], 0f)
        }
    }

    @Test
    fun `warm shifts red up and cool shifts blue up`() {
        assertTrue(PhotoFilter.WARM.values[4] > 0f)   // red offset
        assertTrue(PhotoFilter.COOL.values[14] > 0f)  // blue offset
    }

    @Test
    fun `all filters preserve alpha channel`() {
        PhotoFilter.entries.forEach { filter ->
            val alphaRow = filter.values.copyOfRange(15, 20)
            val expected = floatArrayOf(0f, 0f, 0f, 1f, 0f)
            expected.forEachIndexed { i, v ->
                assertEquals("${filter.label} alpha[$i]", v, alphaRow[i], 0f)
            }
        }
    }
}
