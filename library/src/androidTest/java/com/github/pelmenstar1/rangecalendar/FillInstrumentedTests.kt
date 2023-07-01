package com.github.pelmenstar1.rangecalendar

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.component3
import androidx.core.graphics.component4
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
class FillInstrumentedTests {
    class ShaderFactoryImpl : Fill.ShaderFactory {
        val shader = LinearGradient(0f, 0f, 1f, 1f, 1, 2, Shader.TileMode.CLAMP)

        var createCallCount = 0
        var widthOnCreate = Float.NaN
        var heightOnCreate = Float.NaN
        var shapeOnCreate: Shape? = null

        override fun create(width: Float, height: Float, shape: Shape): Shader {
            widthOnCreate = width
            heightOnCreate = height
            shapeOnCreate = shape
            createCallCount++

            return shader
        }
    }

    class DrawableImpl(val color: Int) : Drawable() {
        override fun draw(canvas: Canvas) {
        }

        override fun setAlpha(alpha: Int) {
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
        }

        @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.OPAQUE", "android.graphics.PixelFormat"))
        override fun getOpacity(): Int = PixelFormat.OPAQUE

        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other == null || javaClass != other.javaClass) return false

            other as DrawableImpl

            return color == other.color
        }

        override fun hashCode(): Int = color

        override fun toString(): String {
            return "DrawableImpl(color=$color)"
        }
    }

    @Test
    fun equalsDrawableTest() {
        // Fill.equals() should use structured equality on Drawable instances.

        val fill1 = Fill.drawable(DrawableImpl(color = 1))
        val fill2 = Fill.drawable(DrawableImpl(color = 1))

        assertEquals(fill1, fill2)
    }

    @Test
    fun hashCodeDrawableTest() {
        // Fill.hashCode() should call hashCode() on Drawable.

        val fill1 = Fill.drawable(DrawableImpl(color = 1))
        val fill2 = Fill.drawable(DrawableImpl(color = 1))

        val hash1 = fill1.hashCode()
        val hash2 = fill2.hashCode()

        assertEquals(hash1, hash2)
    }

    @Test
    fun toStringDrawableTest() {
        val fill = Fill.drawable(DrawableImpl(color = 2))

        val actualResult = fill.toString()

        assertEquals("Fill(type=DRAWABLE, drawable=DrawableImpl(color=2))", actualResult)
    }

    @Test
    fun applyToPaintSolidTest() {
        fun testCase(fillColor: Int, alpha: Float, expectedPaintColor: Int) {
            val fill = Fill.solid(fillColor)
            val paint = Paint()

            fill.setSize(10f, 10f)
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

            fill.setSize(10f, 10f)
            fill.applyToPaint(paint)

            assertEquals(Paint.Style.FILL, paint.style)
            assertEquals(expectedClass, paint.shader.javaClass)
        }

        testCase(isLinear = true, expectedClass = LinearGradient::class.java)
        testCase(isLinear = false, expectedClass = RadialGradient::class.java)
    }

    @Test
    fun applyToPaintThrowsOnDrawableFillTest() {
        val fill = Fill.drawable(DrawableImpl(color = 0))

        assertFailsWith<IllegalStateException> { fill.applyToPaint(Paint()) }
    }

    @Test
    fun setSizeShaderTest() {
        val factory = ShaderFactoryImpl()
        val fill = Fill.shader(factory)

        fill.setSize(width = 2f, height = 3f)

        assertEquals(2f, factory.widthOnCreate)
        assertEquals(3f, factory.heightOnCreate)

        // By default shape is rectangle
        assertEquals(RectangleShape, factory.shapeOnCreate)

        val paint = Paint()
        fill.applyToPaint(paint)

        val actualShader = paint.shader
        assertSame(factory.shader, actualShader)
    }

    @Test
    fun setSizeUpdateShaderWhenDifferentSizeTest() {
        val factory = ShaderFactoryImpl()
        val fill = Fill.shader(factory)

        fill.setSize(width = 2f, height = 3f)
        fill.setSize(width = 2f, height = 2f)

        assertEquals(2, factory.createCallCount)
    }

    @Test
    fun setSizeDoNotUpdateShaderWhenSameSizeTest() {
        val factory = ShaderFactoryImpl()
        val fill = Fill.shader(factory)

        fill.setSize(width = 2f, height = 3f)
        fill.setSize(width = 2f, height = 3f)

        // ShaderFactory.create() should be called exactly one time.
        assertEquals(1, factory.createCallCount)
    }

    @Test
    fun setSizeDrawableTest() {
        fun testCase(width: Float, height: Float, expectedWidth: Int, expectedHeight: Int) {
            val drawable = DrawableImpl(color = 1)
            val fill = Fill.drawable(drawable)

            fill.setSize(width, height)

            val (actualLeft, actualTop, actualRight, actualBottom) = drawable.bounds
            assertEquals(0, actualLeft)
            assertEquals(0, actualTop)
            assertEquals(expectedWidth, actualRight)
            assertEquals(expectedHeight, actualBottom)
        }

        testCase(width = 10f, height = 20f, expectedWidth = 10, expectedHeight = 20)

        // Check if ceil is used.
        testCase(width = 10.1f, height = 20.9f, expectedWidth = 11, expectedHeight = 21)
    }

    @Test
    fun isShaderLikeTest() {
        fun testCase(createFill: Fill.Companion.() -> Fill, expectedResult: Boolean) {
            val fill = createFill(Fill.Companion)
            val actualResult = fill.isShaderLike

            assertEquals(expectedResult, actualResult, "fill type: ${fill.javaClass}")
        }

        testCase({ solid(color = 0) }, expectedResult = false)
        testCase({ linearGradient(1, 2, Fill.Orientation.LEFT_RIGHT) }, expectedResult = true)
        testCase({ radialGradient(1, 2) }, expectedResult = true)
        testCase({ shader(ShaderFactoryImpl()) }, expectedResult = true)
        testCase({ drawable(DrawableImpl(0)) }, expectedResult = false)
    }
}