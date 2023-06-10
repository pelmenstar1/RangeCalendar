package com.github.pelmenstar1.rangecalendar

import android.graphics.Color
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FillTests {
    @Test
    fun equalsSolidTest() {
        fun testCase(thisColor: Int, otherColor: Int, expectedResult: Boolean) {
            val actualResult = Fill.solid(thisColor) == Fill.solid(otherColor)

            assertEquals(
                expectedResult,
                actualResult,
                "thisColor=$thisColor, otherColor=$otherColor"
            )
        }

        testCase(thisColor = 0, otherColor = 0, expectedResult = true)
        testCase(thisColor = 1, otherColor = 2, expectedResult = false)
    }

    @Test
    fun toStringSolidTest() {
        fun testCase(color: Int, expectedResult: String) {
            val actualResult = Fill.solid(color).toString()

            assertEquals(expectedResult, actualResult, "color=$color")
        }

        testCase(color = 0x12345678, expectedResult = "Fill(type=SOLID, color=#12345678)")
        testCase(color = 0, expectedResult = "Fill(type=SOLID, color=#00000000)")
        testCase(color = 0x00FF12AA, expectedResult = "Fill(type=SOLID, color=#00FF12AA)")
    }

    @Test
    fun createLinearGradientIsTwoColorsThroughTwoColorConstructorTest() {
        val fill = Fill.linearGradient(0, 1, Fill.Orientation.LEFT_RIGHT)

        assertTrue((fill as Fill.Gradient).isTwoColors)
    }

    @Test
    fun createLinearGradientIsTwoColorsThroughArrayConstructorTest() {
        fun testCase(colors: IntArray, positions: FloatArray?, expectedValue: Boolean) {
            val fill = Fill.linearGradient(colors, positions, Fill.Orientation.LEFT_RIGHT)
            val actualValue = (fill as Fill.Gradient).isTwoColors

            assertEquals(
                expectedValue,
                actualValue,
                "colors.size=${colors.size}, positions=${positions.contentToString()}"
            )
        }

        testCase(colors = intArrayOf(0, 1), positions = null, expectedValue = true)
        testCase(colors = intArrayOf(0, 1), positions = floatArrayOf(0f, 1f), expectedValue = true)
        testCase(
            colors = intArrayOf(0, 1, 2),
            positions = floatArrayOf(0f, 1f, 2f),
            expectedValue = false
        )
        testCase(
            colors = intArrayOf(0, 1, 2, 3),
            positions = floatArrayOf(0f, 3f, 2f, 4f),
            expectedValue = false
        )
    }

    @Test
    fun equalsLinearGradientTest() {
        fun testCase(
            thisColors: IntArray, thisPositions: FloatArray?, thisOrientation: Fill.Orientation,
            otherColors: IntArray, otherPositions: FloatArray?, otherOrientation: Fill.Orientation,
            expectedResult: Boolean
        ) {
            val thisFill = Fill.linearGradient(thisColors, thisPositions, thisOrientation)
            val otherFill = Fill.linearGradient(otherColors, otherPositions, otherOrientation)

            val actualResult = thisFill == otherFill

            assertEquals(expectedResult, actualResult)
        }

        testCase(
            thisColors = intArrayOf(0, 1),
            thisPositions = null,
            thisOrientation = Fill.Orientation.LEFT_RIGHT,
            otherColors = intArrayOf(0, 1),
            otherPositions = null,
            otherOrientation = Fill.Orientation.LEFT_RIGHT,
            expectedResult = true
        )

        testCase(
            thisColors = intArrayOf(0, 1),
            thisPositions = floatArrayOf(0f, 1f),
            thisOrientation = Fill.Orientation.LEFT_RIGHT,
            otherColors = intArrayOf(0, 1),
            otherPositions = floatArrayOf(0f, 1f),
            otherOrientation = Fill.Orientation.LEFT_RIGHT,
            expectedResult = true
        )

        testCase(
            thisColors = intArrayOf(0, 1),
            thisPositions = null,
            thisOrientation = Fill.Orientation.LEFT_RIGHT,
            otherColors = intArrayOf(0, 1, 2),
            otherPositions = null,
            otherOrientation = Fill.Orientation.LEFT_RIGHT,
            expectedResult = false
        )

        testCase(
            thisColors = intArrayOf(0, 1),
            thisPositions = null,
            thisOrientation = Fill.Orientation.LEFT_RIGHT,
            otherColors = intArrayOf(0, 1, 2),
            otherPositions = floatArrayOf(0f, 1f, 2f),
            otherOrientation = Fill.Orientation.LEFT_RIGHT,
            expectedResult = false
        )

        testCase(
            thisColors = intArrayOf(0, 1),
            thisPositions = null,
            thisOrientation = Fill.Orientation.LEFT_RIGHT,
            otherColors = intArrayOf(0, 1),
            otherPositions = null,
            otherOrientation = Fill.Orientation.RIGHT_LEFT,
            expectedResult = false
        )
    }

    @Test
    fun linearGradientDoesNotEqualToRadialTest() {
        val thisFill = Fill.linearGradient(0, 1, Fill.Orientation.LEFT_RIGHT)
        val otherFill = Fill.radialGradient(0, 1)
        val result = thisFill == otherFill

        assertFalse(result)
    }

    @Test
    fun toStringLinearGradientTest() {
        fun testCase(
            colors: IntArray,
            positions: FloatArray?,
            orientation: Fill.Orientation,
            expectedResult: String
        ) {
            val fill = Fill.linearGradient(colors, positions, orientation)
            val actualResult = fill.toString()

            assertEquals(expectedResult, actualResult)
        }

        testCase(
            colors = intArrayOf(Color.RED, Color.GREEN),
            positions = null,
            orientation = Fill.Orientation.LEFT_RIGHT,
            expectedResult = "Fill(type=LINEAR_GRADIENT, colors=[#FFFF0000, #FF00FF00], orientation=LEFT_RIGHT)"
        )

        testCase(
            colors = intArrayOf(Color.RED, Color.GREEN),
            positions = floatArrayOf(0f, 0.5f),
            orientation = Fill.Orientation.TOP_BOTTOM,
            expectedResult = "Fill(type=LINEAR_GRADIENT, colors=[#FFFF0000, #FF00FF00], positions=[0.0, 0.5], orientation=TOP_BOTTOM)"
        )
    }

    @Test
    fun toStringRadialGradientTest() {
        fun testCase(
            colors: IntArray,
            positions: FloatArray?,
            expectedResult: String
        ) {
            val fill = Fill.radialGradient(colors, positions)
            val actualResult = fill.toString()

            assertEquals(expectedResult, actualResult)
        }

        testCase(
            colors = intArrayOf(Color.RED, Color.GREEN),
            positions = null,
            expectedResult = "Fill(type=RADIAL_GRADIENT, colors=[#FFFF0000, #FF00FF00])"
        )

        testCase(
            colors = intArrayOf(Color.RED, Color.GREEN),
            positions = floatArrayOf(0f, 0.5f),
            expectedResult = "Fill(type=RADIAL_GRADIENT, colors=[#FFFF0000, #FF00FF00], positions=[0.0, 0.5])"
        )
    }
}