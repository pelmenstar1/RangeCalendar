package com.github.pelmenstar1.rangecalendar

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
class RoundRectVisualInfoTests {
    class TestCanvas : Canvas() {
        var drawRoundRectCallCount = 0
        var expectedRRectBounds = RectF(Float.NaN, Float.NaN, Float.NaN, Float.NaN)
        var expectedRoundRadius = Float.NaN
        var expectedPaint: Paint? = null
        var allowDrawPath: Boolean = true

        override fun drawRoundRect(rect: RectF, rx: Float, ry: Float, paint: Paint) {
            drawRoundRectInternal(rect.left, rect.top, rect.right, rect.bottom, rx, ry, paint)
        }

        override fun drawRoundRect(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            rx: Float,
            ry: Float,
            paint: Paint
        ) {
            drawRoundRectInternal(left, top, right, bottom, rx, ry, paint)
        }

        private fun drawRoundRectInternal(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            rx: Float,
            ry: Float,
            paint: Paint
        ) {
            drawRoundRectCallCount++

            assertEquals(expectedRRectBounds, RectF(left, top, right, bottom))
            assertEquals(expectedRoundRadius, rx)
            assertEquals(expectedRoundRadius, ry)
            assertSame(expectedPaint, paint)
        }

        override fun drawPath(path: Path, paint: Paint) {
            if (!allowDrawPath) {
                throw RuntimeException("drawPath() should not be called")
            }
        }
    }

    @Test
    fun drawDoNotUsePathWhenPossibleTest() {
        val visualInfo = RoundRectVisualInfo()
        val box = RectF(1f, 2f, 3f, 4f)
        val roundRadius = 1f
        val paint = Paint()

        visualInfo.setBounds(box)
        visualInfo.setRoundedCorners(roundRadius)

        val canvas = TestCanvas()

        canvas.expectedRRectBounds = box
        canvas.expectedRoundRadius = roundRadius
        canvas.expectedPaint = paint
        canvas.allowDrawPath = false

        visualInfo.draw(canvas, paint)

        assertEquals(1, canvas.drawRoundRectCallCount)
    }

    @Test
    fun drawUseCanonizedRoundRadiusTest() {
        val visualInfo = RoundRectVisualInfo()
        val box = RectF(1f, 2f, 3f, 4f)
        val paint = Paint()

        visualInfo.setBounds(box)

        // Maximum round radius possible
        visualInfo.setRoundedCorners(Float.POSITIVE_INFINITY)

        val canvas = TestCanvas()

        canvas.expectedRRectBounds = box

        // Canvas.drawRoundRect() changes Float.POSITIVE_INFINITY as round radius to 0.
        // When positive infinity is used, it's meant to be maximum round radius possible, i.e half of the height.
        canvas.expectedRoundRadius = 1f
        canvas.expectedPaint = paint
        canvas.allowDrawPath = false

        visualInfo.draw(canvas, paint)

        assertEquals(1, canvas.drawRoundRectCallCount)
    }

    private fun RoundRectVisualInfo.setBounds(rect: RectF) {
        setBounds(rect.left, rect.top, rect.right, rect.bottom)
    }
}