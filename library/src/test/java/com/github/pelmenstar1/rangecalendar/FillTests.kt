package com.github.pelmenstar1.rangecalendar

import android.graphics.Color
import android.graphics.Shader
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FillTests {
    class ShaderFactoryImpl(private val startColor: Int, private val endColor: Int): Fill.ShaderFactory {
        override fun create(width: Float, height: Float, shape: Shape): Shader {
            throw NotImplementedError()
        }

        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other == null || javaClass != other.javaClass) return false

            other as ShaderFactoryImpl

            return startColor == other.startColor && endColor == other.endColor
        }

        override fun hashCode(): Int {
            return startColor * 31 + endColor
        }

        override fun toString(): String {
            return "ShaderFactoryImpl(startColor=$startColor, endColor=$endColor)"
        }
    }

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

        // null positions should be equal to [0, 1] positions
        testCase(
            thisColors = intArrayOf(0, 1),
            thisPositions = null,
            thisOrientation = Fill.Orientation.LEFT_RIGHT,
            otherColors = intArrayOf(0, 1),
            otherPositions = floatArrayOf(0f, 1f),
            otherOrientation = Fill.Orientation.LEFT_RIGHT,
            expectedResult = true
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
    fun hashCodeLinearGradientTest() {
        val colors = intArrayOf(0, 1)

        val fill1 = Fill.linearGradient(colors, positions = floatArrayOf(0f, 1f), Fill.Orientation.LEFT_RIGHT)
        val fill2 = Fill.linearGradient(colors, positions = null, Fill.Orientation.LEFT_RIGHT)

        val hc1 = fill1.hashCode()
        val hc2 = fill2.hashCode()
        val isEqual = hc1 == hc2

        assertTrue(isEqual, "hashcodes should be equal")
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
            expectedResult = "Fill(type=LINEAR_GRADIENT, colors=[#FFFF0000, #FF00FF00], positions=[0.0, 1.0], orientation=LEFT_RIGHT)"
        )

        testCase(
            colors = intArrayOf(Color.RED, Color.GREEN),
            positions = floatArrayOf(0f, 1f),
            orientation = Fill.Orientation.LEFT_RIGHT,
            expectedResult = "Fill(type=LINEAR_GRADIENT, colors=[#FFFF0000, #FF00FF00], positions=[0.0, 1.0], orientation=LEFT_RIGHT)"
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
            expectedResult = "Fill(type=RADIAL_GRADIENT, colors=[#FFFF0000, #FF00FF00], positions=[0.0, 1.0])"
        )

        testCase(
            colors = intArrayOf(Color.RED, Color.GREEN),
            positions = floatArrayOf(0f, 1f),
            expectedResult = "Fill(type=RADIAL_GRADIENT, colors=[#FFFF0000, #FF00FF00], positions=[0.0, 1.0])"
        )

        testCase(
            colors = intArrayOf(Color.RED, Color.GREEN),
            positions = floatArrayOf(0f, 0.5f),
            expectedResult = "Fill(type=RADIAL_GRADIENT, colors=[#FFFF0000, #FF00FF00], positions=[0.0, 0.5])"
        )
    }

    @Test
    fun equalsShaderTest() {
        // Fill.equals() should use structured equality instead of reference one when fill is shader.
        val factory1 = ShaderFactoryImpl(0, 1)
        val factory2 = ShaderFactoryImpl(0, 1)

        val fill1 = Fill.shader(factory1)
        val fill2 = Fill.shader(factory2)

        assertEquals(fill1, fill2)
    }

    @Test
    fun hashCodeShaderTest() {
        // Fill.hashCode() should call hashCode() on ShaderFactory.

        val fill1 = Fill.shader(ShaderFactoryImpl(0, 1))
        val fill2 = Fill.shader( ShaderFactoryImpl(0, 1))

        val hash1 = fill1.hashCode()
        val hash2 = fill2.hashCode()

        assertEquals(hash1, hash2)
    }

    @Test
    fun toStringShaderTest() {
        val fill = Fill.shader(ShaderFactoryImpl(1, 2))

        val actualResult = fill.toString()

        assertEquals("Fill(type=SHADER, factory=ShaderFactoryImpl(startColor=1, endColor=2))", actualResult)
    }
}