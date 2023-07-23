package com.github.pelmenstar1.rangecalendar

import com.github.pelmenstar1.rangecalendar.utils.withAlpha
import com.github.pelmenstar1.rangecalendar.utils.withCombinedAlpha
import com.github.pelmenstar1.rangecalendar.utils.withoutAlpha
import org.junit.Test
import kotlin.test.assertEquals

class ColorHelperTests {
    @Test
    fun withAlphaTest() {
        fun testHelper(color: Int, newAlpha: Int, expectedColor: Int) {
            val actualColor = color.withAlpha(newAlpha)

            assertEquals(expectedColor, actualColor)
        }

        testHelper(color = 0x55443322, newAlpha = 0x66, expectedColor = 0x66443322)
        testHelper(color = 0x11223344, newAlpha = 0x00, expectedColor = 0x00223344)
    }

    @Test
    fun withCombinedAlphaTest() {
        fun testHelper(color: Int, alphaFraction: Float, expectedColor: Int) {
            val actualColor = color.withCombinedAlpha(alphaFraction)

            assertEquals(expectedColor, actualColor)
        }

        testHelper(color = 0xFF112233.toInt(), alphaFraction = 0.5f, expectedColor = 0x80112233.toInt())
        testHelper(color = 0x7F112233, alphaFraction = 0.5f, expectedColor = 0x40112233)
        testHelper(color = 0x22334455, alphaFraction = 0f, expectedColor = 0x00334455)
        testHelper(color = 0xAABBCCDD.toInt(), alphaFraction = 1f, expectedColor = 0xAABBCCDD.toInt())
    }

    @Test
    fun withoutAlphaTest() {
        val actual = 0x11223344.withoutAlpha()

        assertEquals(expected = 0x00223344, actual)
    }
}