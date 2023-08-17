package com.github.pelmenstar1.rangecalendar

import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.pelmenstar1.rangecalendar.utils.withCombinedAlpha
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class BorderTests {
    @Test
    fun applyToPaintTest() {
        fun testHelper(border: Border, alpha: Float) {
            val paint = Paint().apply { style = Paint.Style.FILL }
            border.applyToPaint(paint, alpha)

            assertEquals(border.color.withCombinedAlpha(alpha), paint.color)
            assertEquals(Paint.Style.STROKE, paint.style)
            assertEquals(border.width, paint.strokeWidth)
            assertEquals(border.pathEffect, paint.pathEffect)
        }

        testHelper(Border(Color.RED, 1f), alpha = 1f)
        testHelper(Border(Color.RED, 1f), alpha = 0.5f)
        testHelper(
            Border(Color.RED, width = 1f, dashPathIntervals = floatArrayOf(0f, 1f), dashPathPhase = 1f),
            alpha = 1f
        )
    }

    @Test
    fun equalsTest() {
        fun testHelper(a: Border, b: Border, result: Boolean) {
            val actualResult = a == b

            assertEquals(result, actualResult, "a: $a, b: $b")
        }

        testHelper(
            Border(color = Color.RED, width = 1f),
            Border(color = Color.RED, width = 1f),
            result = true
        )

        testHelper(
            Border(color = Color.RED, width = 1f),
            Border(color = Color.BLUE, width = 2f),
            result = false
        )

        testHelper(
            Border(color = Color.RED, width = 1f, dashPathIntervals = floatArrayOf(1f, 2f), dashPathPhase = 1f),
            Border(color = Color.RED, width = 1f, dashPathIntervals = floatArrayOf(1f, 2f), dashPathPhase = 1f),
            result = true
        )

        testHelper(
            Border(color = Color.RED, width = 1f, dashPathIntervals = floatArrayOf(1f, 2f), dashPathPhase = 1f),
            Border(color = Color.RED, width = 1f, dashPathIntervals = floatArrayOf(2f, 2f), dashPathPhase = 1f),
            result = false
        )

        testHelper(
            Border(color = Color.RED, width = 1f, dashPathIntervals = floatArrayOf(1f, 2f), dashPathPhase = 1f),
            Border(color = Color.RED, width = 1f),
            result = false
        )

        val effectInstance = DashPathEffect(floatArrayOf(1f, 2f), 3f)

        testHelper(
            Border(color = Color.RED, width = 1f, effect = effectInstance),
            Border(color = Color.RED, width = 1f),
            result = false
        )

        testHelper(
            Border(color = Color.RED, width = 1f, effect = effectInstance),
            Border(color = Color.RED, width = 1f, effect = effectInstance),
            result = true
        )
    }

    @Test
    fun toStringTest() {
        fun testHelper(border: Border, expected: String) {
            val actual = border.toString()

            assertEquals(expected, actual)
        }

        testHelper(
            Border(color = Color.RED, width = 2f),
            expected = "Border(color=#FFFF0000, width=2.0)"
        )

        testHelper(
            Border(color = Color.RED, width = 2f, dashPathIntervals = floatArrayOf(2f, 3f), dashPathPhase = 4f),
            expected = "Border(color=#FFFF0000, width=2.0, dashPathIntervals=[2.0, 3.0], dashPathPhase=4.0)"
        )

        val pathEffect = DashPathEffect(floatArrayOf(2f, 3f), 4f)

        testHelper(
            Border(color = Color.RED, width = 2f, effect = pathEffect),
            expected = "Border(color=#FFFF0000, width=2.0, pathEffect=${pathEffect})"
        )
    }
}