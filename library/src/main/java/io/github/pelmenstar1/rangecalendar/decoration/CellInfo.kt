package io.github.pelmenstar1.rangecalendar.decoration

import android.graphics.RectF
import io.github.pelmenstar1.rangecalendar.PackedRectF
import io.github.pelmenstar1.rangecalendar.Padding
import io.github.pelmenstar1.rangecalendar.VerticalAlignment
import kotlin.math.sqrt

/**
 * Simple structure which stores information about the cell
 */
class CellInfo internal constructor() {
    /**
     * Size of the cell, in pixels.
     */
    var size = 0f
        internal set

    /**
     * Radius of corners of the cell
     */
    var radius = 0f
        internal set

    /**
     * Decor layout options associated with the cell
     */
    var layoutOptions: DecorLayoutOptions? = null
        internal set

    internal var textBounds = PackedRectF(0)
        private set

    internal fun setTextBounds(left: Float, top: Float, right: Float, bottom: Float) {
        textBounds = PackedRectF(left, top, right, bottom)
    }

    /**
     * Sets bounds of text in the cell to [outRect]
     */
    fun getTextBounds(outRect: RectF) {
        textBounds.setTo(outRect)
    }

    /**
     * Narrows left and right of specified [RectF] to make rectangle fit the shape of the cell.
     */
    fun narrowRectOnBottom(outRect: RectF) {
        if (radius > 0f) {
            var left = outRect.left
            val top = outRect.top
            var right = outRect.right
            val bottom = outRect.bottom

            val sizeWithoutRadius = size - radius

            when {
                bottom > sizeWithoutRadius -> {
                    val intersectionX = findIntersectionWithCircle(bottom - sizeWithoutRadius)

                    left = intersectionX
                    right = size - intersectionX
                }
                top > sizeWithoutRadius -> {
                    val intersectionX = findIntersectionWithCircle(top - sizeWithoutRadius)

                    left = intersectionX
                    right = size - intersectionX
                }
            }

            outRect.left = left
            outRect.right = right
        }
    }

    internal fun narrowRectOnBottom(rect: PackedRectF): PackedRectF {
        if (radius > 0f) {
            var left = rect.left
            val top = rect.top
            var right = rect.right
            val bottom = rect.bottom

            val sizeWithoutRadius = size - radius

            when {
                bottom > sizeWithoutRadius -> {
                    val intersectionX = findIntersectionWithCircle(bottom - sizeWithoutRadius)

                    left = intersectionX
                    right = size - intersectionX
                }
                top > sizeWithoutRadius -> {
                    val intersectionX = findIntersectionWithCircle(top - sizeWithoutRadius)

                    left = intersectionX
                    right = size - intersectionX
                }
            }

            return rect.withLeftAndRight(left, right)
        }

        return rect
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
        if (y > radius) {
            return radius
        }

        return radius - sqrt((radius - y) * (radius + y))
    }

    /**
     * Finds y-axis coordinate from which to start layout decorations
     * using [height] of the needed place, padding of the cell and vertical alignment.
     */
    fun findTopWithAlignment(
        height: Float,
        padding: Padding,
        align: VerticalAlignment
    ): Float {
        val areaTop = textBounds.bottom
        val areaBottom = size

        return when(align) {
            VerticalAlignment.TOP -> {
                areaTop + padding.top
            }
            VerticalAlignment.CENTER -> {
                (areaTop + areaBottom - height) * 0.5f
            }
            VerticalAlignment.BOTTOM -> {
                areaBottom - padding.bottom - height
            }
        }
    }
}