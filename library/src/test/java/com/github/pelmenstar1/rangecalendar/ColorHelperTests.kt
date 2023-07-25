package com.github.pelmenstar1.rangecalendar

import android.graphics.Color
import com.github.pelmenstar1.rangecalendar.utils.colorLerp
import com.github.pelmenstar1.rangecalendar.utils.withAlpha
import com.github.pelmenstar1.rangecalendar.utils.withCombinedAlpha
import com.github.pelmenstar1.rangecalendar.utils.withoutAlpha
import org.junit.Test
import kotlin.test.assertEquals

class ColorHelperTests {
    private fun createExpectedActualColorMessage(expected: Int, actual: Int): String {
        return "expected=#${String.format("%08X", expected)}, actual=#${String.format("%08X", actual)}"
    }

    @Test
    fun withAlphaTest() {
        fun testHelper(color: Int, newAlpha: Int, expectedColor: Int) {
            val actualColor = color.withAlpha(newAlpha)

            assertEquals(expectedColor, actualColor, createExpectedActualColorMessage(expectedColor, actualColor))
        }

        testHelper(color = 0x55443322, newAlpha = 0x66, expectedColor = 0x66443322)
        testHelper(color = 0x11223344, newAlpha = 0x00, expectedColor = 0x00223344)
    }

    @Test
    fun withCombinedAlphaFloatTest() {
        fun testHelper(color: Int, alphaFraction: Float, expectedColor: Int) {
            val actualColor = color.withCombinedAlpha(alphaFraction)

            assertEquals(expectedColor, actualColor, createExpectedActualColorMessage(expectedColor, actualColor))
        }

        testHelper(color = 0xFF112233.toInt(), alphaFraction = 0.5f, expectedColor = 0x80112233.toInt())
        testHelper(color = 0x7F112233, alphaFraction = 0.5f, expectedColor = 0x40112233)
        testHelper(color = 0x22334455, alphaFraction = 0f, expectedColor = 0x00334455)
        testHelper(color = 0xAABBCCDD.toInt(), alphaFraction = 1f, expectedColor = 0xAABBCCDD.toInt())
    }

    @Test
    fun withoutAlphaTest() {
        val actual = 0x11223344.withoutAlpha()
        val expected = 0x00223344

        assertEquals(expected, actual, createExpectedActualColorMessage(expected, actual))
    }

    @Test
    fun colorLerpTest() {
        fun testHelper(startColor: Int, endColor: Int, fraction: Float, alphaFraction: Float, expected: Int) {
            val actual = colorLerp(startColor, endColor, fraction, alphaFraction)

            assertEquals(expected, actual, createExpectedActualColorMessage(expected, actual))
        }

        testHelper(
            startColor = Color.RED,
            endColor = Color.GREEN,
            fraction = 0f,
            alphaFraction = 1f,
            expected = Color.RED
        )

        testHelper(
            startColor = Color.RED,
            endColor = Color.GREEN,
            fraction = 1f,
            alphaFraction = 1f,
            expected = Color.GREEN
        )

        testHelper(
            startColor = 0x11223344,
            endColor = 0x22334455,
            fraction = 0.5f,
            alphaFraction = 1f,
            expected = 0x192A3B4C
        )

        testHelper(
            startColor = 0x11223344,
            endColor = 0x22334455,
            fraction = 0.5f,
            alphaFraction = 0.5f,
            expected = 0x0C2A3B4C
        )
    }
}