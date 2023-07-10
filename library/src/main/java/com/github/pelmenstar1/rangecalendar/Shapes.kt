package com.github.pelmenstar1.rangecalendar

import android.graphics.*
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import com.github.pelmenstar1.rangecalendar.utils.SQRT_2
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Defines geometrical properties of a shape and way to draw it on [Canvas].
 *
 * Custom instances of [Shape] **must** be immutable and implement [equals], [hashCode], [toString] methods.
 */
interface Shape {
    /**
     * Gets whether a [Path] instance is needed to draw a shape. If it's `true`, [draw] method should never be called.
     * Instead, a caller should use [addToPath] and then draw this path.
     *
     * @see draw
     */
    val needsPathToDraw: Boolean
        // Workaround to change JVM name for getter in interface
        @Suppress("INAPPLICABLE_JVM_NAME")
        @JvmName("needsPathToDraw")
        get

    /**
     * Adds a shape to specified [path].
     *
     * @param path a path instance where shape is added to
     * @param box rectangle in which shape is located.
     */
    fun addToPath(path: Path, box: RectF)

    /**
     * Draws a shape on [Canvas]. Fails when [needsPathToDraw] is `true`.
     *
     * @param canvas canvas to draw on.
     * @param box rectangle in which shape is located.
     * @param paint paint which should be used to draw a shape. It shouldn't be mutated.
     */
    fun draw(canvas: Canvas, box: RectF, paint: Paint)

    /**
     * Computes properties of a circle in which shape is inscribed.
     *
     * If such circle does not exist, then, as workaround, finds center of mass of a shape.
     *
     * @param box rectangle in which a shape is located.
     * @param outCenter a point which is mutated to be center point of the circle.
     *
     * @return radius of the circle.
     */
    fun computeCircumcircle(box: RectF, outCenter: PointF): Float
}

/**
 * A rectangle shape.
 */
object RectangleShape : Shape {
    override val needsPathToDraw: Boolean
        get() = false

    override fun addToPath(path: Path, box: RectF) {
        path.addRect(box, Path.Direction.CW)
    }

    override fun draw(canvas: Canvas, box: RectF, paint: Paint) {
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
 * Ellipse.
 */
object EllipseShape : Shape {
    override val needsPathToDraw: Boolean
        get() = false

    override fun draw(canvas: Canvas, box: RectF, paint: Paint) {
        canvas.drawOval(box, paint)
    }

    override fun addToPath(path: Path, box: RectF) {
        path.addOval(box, Path.Direction.CW)
    }

    override fun computeCircumcircle(box: RectF, outCenter: PointF): Float {
        outCenter.x = box.centerX()
        outCenter.y = box.centerY()

        return max(box.width(), box.height()) * 0.5f
    }
}

/**
 * A triangle with points defined by relative points (x and y are within range `[0; 1]`).
 * Such definition allows the triangle to be scalable.
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

    override fun draw(canvas: Canvas, box: RectF, paint: Paint) {
        throw IllegalStateException("TriangleShape requires a Path to draw")
    }

    override fun addToPath(path: Path, box: RectF) {
        val (left, top) = box

        val width = box.width()
        val height = box.height()

        path.run {
            moveTo(left + width * p1x, top + height * p1y)
            lineTo(left + width * p2x, top + height * p2y)
            lineTo(left + width * p3x, top + height * p3y)
            close()
        }
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

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null || javaClass != other.javaClass) return false

        other as TriangleShape

        return p1x == other.p1x && p2x == other.p2x && p3x == other.p3x &&
                p1y == other.p1y && p2y == other.p2y && p3x == other.p3y
    }

    override fun hashCode(): Int {
        var result = p1x.toBits()
        result = result * 31 + p2x.toBits()
        result = result * 31 + p3x.toBits()
        result = result * 31 + p1y.toBits()
        result = result * 31 + p2y.toBits()
        result = result * 31 + p3y.toBits()

        return result
    }

    override fun toString(): String {
        return "TriangleShape(p1=($p1x, $p1y), p2=($p2x, $p2y), p3=($p3x, $p3y))"
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