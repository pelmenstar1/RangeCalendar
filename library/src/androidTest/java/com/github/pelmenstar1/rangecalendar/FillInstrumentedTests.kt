package com.github.pelmenstar1.rangecalendar

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class FillInstrumentedTests {
    // Fill is actually a stateful object. The colors can be mutated if we call applyToPaint() with alpha different than 1.0f
    // But this should not be visible to outside that's why the Fill.Gradient is maintaining the array of original alphas
    // Tests like *AfterMutationTest are checking if the behaviour is correct.

    private fun createMutatedGradient(): Fill {
        return Fill.linearGradient(Color.RED, Color.BLACK, Fill.Orientation.LEFT_RIGHT).apply {
            setBounds(0f, 0f, 10f, 10f)
            applyToPaint(Paint(), alpha = 0.5f)
        }
    }

    @Test
    fun equalsLinearGradientAfterMutationTest() {
        val thisFill = createMutatedGradient()

        val otherFill = Fill.linearGradient(Color.RED, Color.BLACK, Fill.Orientation.LEFT_RIGHT)
        val isEqual = thisFill == otherFill

        assertTrue(isEqual)
    }

    @Test
    fun hashCodeLinearGradientAfterMutationTest() {
        val thisFill = createMutatedGradient()

        val otherFill = Fill.linearGradient(Color.RED, Color.BLACK, Fill.Orientation.LEFT_RIGHT)
        val isSameHashCode = thisFill.hashCode() == otherFill.hashCode()

        assertTrue(isSameHashCode)
    }

    @Test
    fun toStringLinearGradientAfterMutationTest() {
        val thisFill = createMutatedGradient()

        val otherFill = Fill.linearGradient(Color.RED, Color.BLACK, Fill.Orientation.LEFT_RIGHT)

        val thisToString = thisFill.toString()
        val otherToString = otherFill.toString()

        assertEquals("Fill(type=LINEAR_GRADIENT, colors=[#FFFF0000, #FF000000], orientation=LEFT_RIGHT)", thisToString)
        assertEquals(thisToString, otherToString)
    }

    @Test
    fun applyToPaintSolidTest() {
        fun testCase(fillColor: Int, alpha: Float, expectedPaintColor: Int) {
            val fill = Fill.solid(fillColor)
            val paint = Paint()

            fill.setBounds(0f, 0f, 10f, 10f)
            fill.applyToPaint(paint, alpha)

            val paintColor = paint.color

            assertEquals(expectedPaintColor, paintColor)
            assertEquals(Paint.Style.FILL, paint.style)
            assertNull(paint.shader)
        }

        testCase(fillColor = Color.RED, alpha = 1f, expectedPaintColor = Color.RED)
        testCase(fillColor = Color.RED, alpha = 0.5f, expectedPaintColor = 0x80FF0000.toInt())
    }

    @Test
    fun applyToPaintGradientTest() {
        fun testCase(isLinear: Boolean, expectedClass: Class<out Shader>) {
            val colors = intArrayOf(0, 1)

            val fill = if (isLinear) {
                Fill.linearGradient(colors, null, Fill.Orientation.LEFT_RIGHT)
            } else {
                Fill.radialGradient(colors, null)
            }

            val paint = Paint()

            fill.setBounds(0f, 0f, 10f, 10f)
            fill.applyToPaint(paint, alpha = 1f)

            assertEquals(Paint.Style.FILL, paint.style)
            assertEquals(expectedClass, paint.shader.javaClass)
        }

        testCase(isLinear = true, expectedClass = LinearGradient::class.java)
        testCase(isLinear = false, expectedClass = RadialGradient::class.java)
    }
}