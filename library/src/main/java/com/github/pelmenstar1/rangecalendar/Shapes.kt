package com.github.pelmenstar1.rangecalendar

import android.graphics.*
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import com.github.pelmenstar1.rangecalendar.utils.SQRT_2
import kotlin.math.sqrt

/**
 * Defines geometrical properties of a shape and way to draw it on [Canvas].
 */
interface Shape {
    /**
     * Gets whether a [Path] instance is needed to draw a shape.
     *
     * @see draw
     */
    val needsPathToDraw: Boolean
        // Workaround to change JVM name for getter in interface
        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("needsPathToDraw")
        get

    /**
     * Throws an exception if a box is invalid.
     *
     * @param box rectangle in which a shape in located.
     */
    fun validateBox(box: RectF)

    /**
     * Draws a shape on [Canvas].
     *
     * @param canvas canvas to draw on.
     * @param box rectangle in which a shape should be located. In other words,
     * entire shape should be within this rectangle.
     * @param path path which should be used to initialize a shape and draw it on [canvas].
     * This path is empty and may not be restored again.
     * May be null if [needsPathToDraw] is false.
     * @param paint paint which should be used to draw a shape.
     * It shouldn't be mutated, it should remain untouched.
     */
    fun draw(canvas: Canvas, box: RectF, path: Path?, paint: Paint)

    /**
     * Computes properties of a circle in which shape is inscribed.
     *
     * If such circle does not exist, then, as workaround, finds center of mass of a shape.
     * And radius is maximum distance to a point containing a shape.
     *
     * @param box rectangle in which a shape is located.
     * @param outCenter a point which is mutated to be center point of the circle.
     *
     * @return radius of the circle.
     */
    fun computeCircumcircle(box: RectF, outCenter: PointF): Float

    /**
     * Narrows a box (if needed) to make a rectangle circumscribed.
     * In other words, if a shape is polygonal, mutates [box] to be the smallest rectangle that can be drawn
     * around a set of points such that all the points are inside it, or exactly on one of its sides.
     */
    fun narrowBox(box: RectF)
}

/**
 * A rectangle shape.
 */
object RectangleShape : Shape {
    override val needsPathToDraw: Boolean
        get() = false

    override fun validateBox(box: RectF) {
    }

    override fun narrowBox(box: RectF) {
    }

    override fun draw(canvas: Canvas, box: RectF, path: Path?, paint: Paint) {
        canvas.drawRect(box, paint)
    }

    override fun computeCircumcircle(box: RectF, outCenter: PointF): Float {
        val width = box.width()
        val height = box.height()

        // Radius of the circumcircle is half of a diagonal of rectangle:
        // sqrt(a^2 + b^2) / 2
        val radius = if (width == height) {
            // If width and height are equal, let it be 'x',
            // then radius of circumcircle is:
            // sqrt(x^2 + x^2) / 2 = sqrt(2 * x^2) / 2 = x * (sqrt(2) / 2)
            width * SQRT_2 * 0.5f
        } else {
            sqrt(width * width + height * height) * 0.5f
        }

        outCenter.x = box.centerX()
        outCenter.y = box.centerY()

        return radius
    }
}

/**
 * A circle shape.
 * Note, this is not ellipse, hence width and height of a box, in which a circle is drawn into, should be equal.
 */
object CircleShape : Shape {
    override val needsPathToDraw: Boolean
        get() = false

    override fun validateBox(box: RectF) {
        require(box.width() == box.height()) {
            "Width and shape of a box should be equal"
        }
    }

    override fun narrowBox(box: RectF) {
    }

    override fun draw(canvas: Canvas, box: RectF, path: Path?, paint: Paint) {
        canvas.drawOval(box, paint)
    }

    override fun computeCircumcircle(box: RectF, outCenter: PointF): Float {
        outCenter.x = box.centerX()
        outCenter.y = box.centerY()

        return box.width() * 0.5f
    }
}

/**
 * A triangle with points defined by relative points(x and y are within range `[0; 1]`).
 * Such definition allows a triangle to be scalable.
 *
 * @param p1x x-axis of 1 relative point
 * @param p1y y-axis of 1 relative point
 * @param p2x x-axis of 2 relative point
 * @param p2y y-axis of 2 relative point
 * @param p3x x-axis of 3 relative point
 * @param p3y y-axis of 3 relative point
 *
 * @throws IllegalArgumentException when x or y axis of any points is not within range `[0; 1]`
 */
class TriangleShape(
    val p1x: Float, val p1y: Float,
    val p2x: Float, val p2y: Float,
    val p3x: Float, val p3y: Float
) : Shape {
    override val needsPathToDraw: Boolean
        get() = true

    init {
        requireValidRelativePoint(p1x, p1y)
        requireValidRelativePoint(p2x, p2y)
        requireValidRelativePoint(p3x, p3y)
    }

    override fun validateBox(box: RectF) {
    }

    override fun draw(canvas: Canvas, box: RectF, path: Path?, paint: Paint) {
        requireNotNull(path) { "'path' argument is null" }

        val width = box.width()
        val height = box.height()

        val (left, top) = box

        path.run {
            moveTo(left + width * p1x, top + height * p1y)
            lineTo(left + width * p2x, top + height * p2y)
            lineTo(left + width * p3x, top + height * p3y)
            close()
        }

        canvas.drawPath(path, paint)
    }

    override fun narrowBox(box: RectF) {
        val (left, top) = box

        val width = box.width()
        val height = box.height()

        // Find leftmost and rightmost points
        box.left = left + minOf(p1x, p2x, p3x) * width
        box.right = left + maxOf(p1x, p2x, p3x) * width

        // Find topmost and bottom-most points
        box.top = top + minOf(p1y, p2y, p3y) * height
        box.bottom = top + maxOf(p1y, p2y, p3y) * height
    }

    override fun computeCircumcircle(box: RectF, outCenter: PointF): Float {
        // Source:
        // https://en.wikipedia.org/wiki/Circumscribed_circle#Cartesian_coordinates_2

        val (left, top) = box

        val width = box.width()
        val height = box.height()

        val ax = left + width * p1x
        val ay = top + height * p1y

        val tbx = width * (p2x - p1x)
        val tby = height * (p2y - p1y)

        val tcx = width * (p3x - p1x)
        val tcy = height * (p3y - p1y)

        val td = 2 * (tbx * tcy - tby * tcx)

        val tbSq = tbx * tbx + tby * tby
        val tcSq = tcx * tcx + tcy * tcy

        val tx = (tcy * tbSq - tby * tcSq) / td
        val ty = (tbx * tcSq - tcx * tbSq) / td

        val radius = sqrt(tx * tx + ty * ty)

        outCenter.x = tx + ax
        outCenter.y = ty + ay

        return radius
    }

    companion object {
        private fun requireValidRelativePoint(x: Float, y: Float) {
            requireValidRelativePointAxis(x)
            requireValidRelativePointAxis(y)
        }

        private fun requireValidRelativePointAxis(value: Float) {
            require(value in 0f..1f) {
                "Relative points' axes should be in range [0; 1]"
            }
        }

        /**
         * Isosceles triangle which fills all the available space in the box and
         * whose base is located at bottom of the box.
         */
        @JvmField
        val ISOSCELES_BASE_BOTTOM = TriangleShape(
            0.5f, 0f,
            1f, 1f,
            0f, 1f
        )

        /**
         * Isosceles triangle which fills all the available space in the box and
         * whose base is located at top of the box.
         */
        @JvmField
        val ISOSCELES_BASE_TOP = TriangleShape(
            0f, 0f,
            1f, 0f,
            0.5f, 1f
        )

        /**
         * Orthogonal (right; right-angled) triangle which fills all the available space in the box and
         * whose right angle is located at left top of the box.
         */
        @JvmField
        val ORTHOGONAL_RIGHT_ANGLE_LEFT_TOP = TriangleShape(
            0f, 0f,
            1f, 0f,
            0f, 1f
        )

        /**
         * Orthogonal (right; right-angled) triangle which fills all the available space in the box and
         * whose right angle is located at left bottom of the box.
         */
        @JvmField
        val ORTHOGONAL_RIGHT_ANGLE_LEFT_BOTTOM = TriangleShape(
            0f, 0f,
            0f, 1f,
            1f, 1f
        )

        /**
         * Orthogonal (right; right-angled) triangle which fills all the available space in the box and
         * whose right angle is located at right top of the box.
         */
        @JvmField
        val ORTHOGONAL_RIGHT_ANGLE_RIGHT_TOP = TriangleShape(
            0f, 0f,
            1f, 0f,
            1f, 1f
        )

        /**
         * Orthogonal (right; right-angled) triangle which fills all the available space in the box and
         * whose right angle is located at right bottom of the box.
         */
        @JvmField
        val ORTHOGONAL_RIGHT_ANGLE_RIGHT_BOTTOM = TriangleShape(
            1f, 0f,
            1f, 1f,
            0f, 1f
        )
    }
}