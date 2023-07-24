package com.github.pelmenstar1.rangecalendar.decoration

import android.graphics.RectF
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.component3
import androidx.core.graphics.component4
import com.github.pelmenstar1.rangecalendar.Padding
import com.github.pelmenstar1.rangecalendar.VerticalAlignment
import kotlin.math.sqrt

/**
 * Simple structure which stores information about the cell
 */
class CellInfo internal constructor() {
    /**
     * Width of the cell.
     */
    var width = 0f
        internal set

    /**
     * Height of the cell.
     */
    var height = 0f
        internal set

    /**
     * Radius of corners of the cell.
     */
    var radius = 0f
        internal set

    /**
     * Decor layout options associated with the cell.
     */
    var layoutOptions: DecorLayoutOptions? = null
        internal set

    private var textLeft = 0f
    private var textTop = 0f
    private var textRight = 0f

    @JvmField
    internal var textBottom = 0f

    internal fun setTextBounds(left: Float, top: Float, right: Float, bottom: Float) {
        textLeft = left
        textTop = top
        textRight = right
        textBottom = bottom
    }

    /**
     * Sets bounds of text in the cell to [outRect]
     */
    fun getTextBounds(outRect: RectF) {
        outRect.set(textLeft, textTop, textRight, textBottom)
    }

    /**
     * Narrows left and right of specified [RectF] to make rectangle fit the shape of the cell.
     */
    fun narrowRectOnBottom(outRect: RectF) {
        if (radius > 0f) {
            var (left, top, right, bottom) = outRect

            val width = width
            val heightWithoutRadius = height - radius

            when {
                bottom > heightWithoutRadius -> {
                    val intersectionX = findIntersectionWithCircle(bottom - heightWithoutRadius)

                    left = intersectionX
                    right = width - intersectionX
                }

                top > heightWithoutRadius -> {
                    val intersectionX = findIntersectionWithCircle(top - heightWithoutRadius)

                    left = intersectionX
                    right = width - intersectionX
                }
            }

            outRect.left = left
            outRect.right = right
        }
    }

    // This function finds x of intersection of circle and line. Line is horizontal and specified by y.
    //
    // Circle's equation (with center at (0; 0)):
    // x^2 + y^2 = R^2
    //
    // Let's find x:
    // x^2 = R^2 - y^2
    // x = sqrt(R^2 - y^2)
    // x = sqrt((R - y) * (R + y))
    //
    // In the end, we need to invert y as y-axis.
    //
    // Final formula:
    // x = R - sqrt((R - y) * (R + y))
    private fun findIntersectionWithCircle(y: Float): Float {
        val r = radius

        if (y > r) {
            return r
        }

        return r - sqrt((r - y) * (r + y))
    }

    /**
     * Finds y-axis coordinate from which to start layout decorations
     * using [areaHeight] of the needed place, padding of the cell and vertical alignment.
     */
    fun findTopWithAlignment(
        areaHeight: Float,
        padding: Padding,
        align: VerticalAlignment
    ): Float {
        val areaTop = textBottom
        val areaBottom = height

        return when (align) {
            VerticalAlignment.TOP -> areaTop + padding.top
            VerticalAlignment.CENTER -> (areaTop + areaBottom - areaHeight) * 0.5f
            VerticalAlignment.BOTTOM -> areaBottom - padding.bottom - areaHeight
        }
    }
}