package com.github.pelmenstar1.rangecalendar

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class ShapeVisualInfoTests {
    class TestShape (override val needsPathToDraw: Boolean = true) : Shape {
        // If addToPath or draw is called earlier than expected, comparison with NaN always fails
        var expectedBox = RectF(Float.NaN, Float.NaN, Float.NaN, Float.NaN)
        var addToPathCallCount = 0
        var drawCallCount = 0

        override fun addToPath(path: Path, box: RectF) {
            assertEquals(expectedBox, box)
            addToPathCallCount++
        }

        override fun draw(canvas: Canvas, box: RectF, paint: Paint) {
            assertEquals(expectedBox, box)
            drawCallCount++
        }

        override fun computeCircumcircle(box: RectF, outCenter: PointF): Float {
            throw NotImplementedError()
        }
    }

    @Test
    fun getPathTest() {
        val visualInfo = ShapeVisualInfo()
        val shape = TestShape()
        val box = RectF(1f, 2f, 4f, 5f)

        visualInfo.setShape(shape)
        visualInfo.setBounds(box)

        shape.expectedBox = box
        visualInfo.getPath()

        assertEquals(1, shape.addToPathCallCount)
    }

    @Test
    fun getPathFailsWhenShapeNotCalledTest() {
        val visualInfo = ShapeVisualInfo()

        assertFailsWith<RuntimeException> {
            visualInfo.getPath()
        }
    }

    @Test
    fun setSameBoundsDoNotCausePathRecreationTest() {
        val visualInfo = ShapeVisualInfo()
        val box = RectF(1f, 2f, 3f, 4f)
        val shape = TestShape()

        visualInfo.setShape(shape)
        visualInfo.setBounds(box)

        shape.expectedBox = box

        // Trigger path creation once
        visualInfo.getPath()

        // Set same bounds
        visualInfo.setBounds(box)

        // Trigger getting path one more time
        visualInfo.getPath()

        assertEquals(1, shape.addToPathCallCount)
    }

    @Test
    fun setDifferentBoundsCausePathRecreationTest() {
        val visualInfo = ShapeVisualInfo()
        val box1 = RectF(1f, 2f, 3f, 4f)
        val box2 = RectF(2f, 2f, 3f, 4f)
        val shape = TestShape()

        visualInfo.setShape(shape)
        visualInfo.setBounds(box1)

        shape.expectedBox = box1

        // Trigger path creation once
        visualInfo.getPath()

        // Set different bounds
        visualInfo.setBounds(box2)

        shape.expectedBox = box2

        // Trigger getting path one more time
        visualInfo.getPath()

        assertEquals(2, shape.addToPathCallCount)
    }

    @Test
    fun drawShapeNoPathTest() {
        val visualInfo = ShapeVisualInfo()
        val box = RectF(1f, 2f, 3f, 4f)
        val shape = TestShape(needsPathToDraw = false)

        visualInfo.setShape(shape)
        visualInfo.setBounds(box)

        shape.expectedBox = box

        val canvas = Canvas()
        val paint = Paint()

        visualInfo.draw(canvas, paint)

        assertEquals(1, shape.drawCallCount)
        assertEquals(0, shape.addToPathCallCount)
    }

    @Test
    fun drawShapeWithPathTest() {
        val visualInfo = ShapeVisualInfo()
        val box = RectF(1f, 2f, 3f, 4f)
        val shape = TestShape(needsPathToDraw = true)

        visualInfo.setShape(shape)
        visualInfo.setBounds(box)

        shape.expectedBox = box

        val canvas = Canvas()
        val paint = Paint()

        visualInfo.draw(canvas, paint)

        assertEquals(0, shape.drawCallCount)
        assertEquals(1, shape.addToPathCallCount)
    }
}