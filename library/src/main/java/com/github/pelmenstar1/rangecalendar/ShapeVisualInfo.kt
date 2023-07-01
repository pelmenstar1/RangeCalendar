package com.github.pelmenstar1.rangecalendar

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Contains visual information about a shape. Also provides means to draw the shape.
 * It heavily relies on the expectation that shape instances are immutable.
 */
internal class ShapeVisualInfo {
    private val bounds = RectF()
    private var shape: Shape? = null

    private var shapePath: Path? = null
    private var isPathDirty = true

    fun setBounds(rect: RectF) {
        setBounds(rect.left, rect.top, rect.right, rect.bottom)
    }

    fun setBounds(newLeft: Float, newTop: Float, newRight: Float, newBottom: Float) {
        if (newLeft == bounds.left && newTop == bounds.top && newRight == bounds.right && newBottom == bounds.bottom) {
            return
        }

        bounds.set(newLeft, newTop, newRight, newBottom)

        isPathDirty = true
    }

    fun setShape(newShape: Shape) {
        if (newShape == shape) {
            return
        }

        shape = newShape
        isPathDirty = true
    }

    fun getPath(): Path {
        var path = shapePath
        if (path == null || isPathDirty) {
            isPathDirty = false
            path = getUpdatedPath()
        }

        return path
    }

    fun draw(canvas: Canvas, paint: Paint) {
        val shape = getShape()

        if (shape.needsPathToDraw) {
            val path = getPath()

            canvas.drawPath(path, paint)
        } else {
            shape.draw(canvas, bounds, paint)
        }
    }

    private fun getUpdatedPath(): Path {
        val path = getEmptyPath()

        getShape().addToPath(path, bounds)

        return path
    }

    private fun getEmptyPath(): Path {
        var path = shapePath
        if (path == null) {
            path = Path()
            shapePath = path
        } else {
            path.rewind()
        }

        return path
    }

    private fun getShape(): Shape {
        return shape ?: throw RuntimeException("setShape() is not called")
    }
}