package com.github.pelmenstar1.rangecalendar

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.github.pelmenstar1.rangecalendar.utils.addRoundRectCompat
import com.github.pelmenstar1.rangecalendar.utils.drawRoundRectCompat
import com.github.pelmenstar1.rangecalendar.utils.getLazyValue
import kotlin.math.min

internal class RoundRectVisualInfo {
    private var left = 0f
    private var top = 0f
    private var right = 0f
    private var bottom = 0f

    private var roundRadius: Float = 0f
    private var roundRadii: FloatArray? = null

    private var normalizedRadii: FloatArray? = null
    private var isPathDirty = true

    private var roundRectPath: Path? = null

    fun setBounds(rect: RectF) {
        setBounds(rect.left, rect.top, rect.right, rect.bottom)
    }

    fun setBounds(newLeft: Float, newTop: Float, newRight: Float, newBottom: Float) {
        if (newLeft == left && newTop == top && newRight == right && newBottom == bottom) {
            return
        }

        left = newLeft
        top = newTop
        right = newRight
        bottom = newBottom

        isPathDirty = true
    }

    fun setRoundedCorners(radius: Float) {
        if (roundRadii == null && roundRadius == radius) {
            return
        }

        roundRadius = radius
        roundRadii = null

        isPathDirty = true
    }

    fun setRoundedCorners(radii: FloatArray) {
        // Use reference equality instead of structured to reduce overhead.
        if (roundRadii === radii) {
            return
        }

        roundRadii = radii
        isPathDirty = true
    }

    /**
     * Returns a path that contains round rect with properties specified by the instance.
     * It returns `null` if the round rect is not actually a rect with round corners.
     */
    fun getPath(): Path? {
        if (isPathDirty) {
            isPathDirty = false
            updatePath()
        }

        return if (isRealRoundRect()) roundRectPath else null
    }

    /**
     * Returns whether specified round rect is actually a rect with round corners.
     *
     * It misses a case when [roundRadii] is not null and all the elements are zeros.
     */
    private fun isRealRoundRect(): Boolean {
        return roundRadii != null || roundRadius > 0f
    }

    fun draw(canvas: Canvas, paint: Paint) {
        val radii = roundRadii
        if (radii != null) {
            // getPath() returns a not-null path if roundRadii is not null.
            canvas.drawPath(getPath()!!, paint)
        } else {
            val rr = min(roundRadius, getMaxRoundRadius())

            canvas.drawRoundRectCompat(left, top, right, bottom, rr, paint)
        }
    }

    private fun getEmptyPath(): Path {
        var path = roundRectPath
        if (path == null) {
            path = Path()
            roundRectPath = path
        } else {
            path.rewind()
        }

        return path
    }

    private fun updatePath() {
        val maxRoundRadius = getMaxRoundRadius()
        val radii = roundRadii
        var rr = roundRadius

        if (radii != null) {
            val normRadii = getLazyValue(normalizedRadii, { FloatArray(8) }) { normalizedRadii = it }

            for (i in 0 until 8) {
                normRadii[i] = min(radii[i], maxRoundRadius)
            }

            getEmptyPath().addRoundRectCompat(left, top, right, bottom, normRadii)
        } else if (rr > 0f) {
            rr = min(rr, maxRoundRadius)

            getEmptyPath().addRoundRectCompat(left, top, right, bottom, rr)
        } else {
            // If rr = 0, the round rect is not actually a rect with round corners. The path won't be used at all.
            // We can clear all the data.
            roundRectPath?.reset()
        }
    }

    private fun getMaxRoundRadius(): Float {
        return (bottom - top) * 0.5f // half of height
    }
}