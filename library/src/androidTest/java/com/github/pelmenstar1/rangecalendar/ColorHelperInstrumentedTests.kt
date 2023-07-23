package com.github.pelmenstar1.rangecalendar

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.pelmenstar1.rangecalendar.utils.colorLerp
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class ColorHelperInstrumentedTests {
    @Test
    fun colorLerpTest() {
        fun testHelper(startColor: Int, endColor: Int, fraction: Float, expected: Int) {
            val actual = colorLerp(startColor, endColor, fraction)

            assertEquals(expected, actual)
        }

        testHelper(
            startColor = Color.RED,
            endColor = Color.GREEN,
            fraction = 0f,
            expected = Color.RED
        )

        testHelper(
            startColor = Color.RED,
            endColor = Color.GREEN,
            fraction = 1f,
            expected = Color.GREEN
        )

        testHelper(
            startColor = 0x11223344,
            endColor = 0x22334455,
            fraction = 0.5f,
            expected = 0x192A3B4C
        )
    }
}