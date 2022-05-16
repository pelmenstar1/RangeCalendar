package io.github.pelmenstar1.rangecalendar.decoration

import android.graphics.RectF
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
        return radius - sqrt((radius - y) * (radius + y))
    }
}