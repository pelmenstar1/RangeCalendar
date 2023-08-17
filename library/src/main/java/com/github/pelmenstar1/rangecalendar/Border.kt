package com.github.pelmenstar1.rangecalendar

import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PathEffect
import android.graphics.RectF
import androidx.annotation.ColorInt
import com.github.pelmenstar1.rangecalendar.utils.appendColor
import com.github.pelmenstar1.rangecalendar.utils.withCombinedAlpha

/**
 * Represents information needed for drawing border around some shape.
 */
class Border {
    /**
     * Stroke width of border
     */
    val width: Float

    /**
     * Array with even length of floats specifying "on" and "off" length intervals of dashes.
     *
     * If the border is "dash" one, the array is `null`.
     */
    val dashPathIntervals: FloatArray?

    /**
     * An offset into the [dashPathIntervals] array (mod the sum of all of the intervals).
     *
     * If the border is "dash" one, the property is `0`
     */
    val dashPathPhase: Float

    /**
     * Color of the border.
     */
    @get:ColorInt
    val color: Int

    /**
     * The path effect of the border.
     *
     * If the border is "dash" one, the property is not null and of type [DashPathEffect].
     * If the border is created via constructor with custom [PathEffect], the property stores given path effect.
     * In other cases, it's `null`.
     */
    val pathEffect: PathEffect?

    /**
     * Constructs [Border] instance using color specified by color int and stroke width.
     *
     * @param color color of the border.
     * @param width stroke width of the border in pixels.
     */
    constructor(@ColorInt color: Int, width: Float) {
        this.color = color
        this.width = width
        dashPathIntervals = null
        dashPathPhase = 0f
        pathEffect = null
    }

    /**
     * Constructs [Border] instance with dashed line.
     *
     * @param color color of the border.
     * @param width stroke width of the border.
     * @param dashPathIntervals array with even length of floats specifying "on" and "off" length intervals of dashes
     * @param dashPathPhase an offset into the [dashPathIntervals] array (mod the sum of all of the intervals)
     */
    constructor(
        @ColorInt color: Int,
        width: Float,
        dashPathIntervals: FloatArray,
        dashPathPhase: Float
    ) {
        this.color = color
        this.width = width
        this.dashPathIntervals = dashPathIntervals
        this.dashPathPhase = dashPathPhase

        pathEffect = DashPathEffect(dashPathIntervals, dashPathPhase)
    }

    /**
     * Constructs [Border] instance with custom path effect.
     *
     * @param color color of the border
     * @param width stroke width of the border, in pixels
     * @param effect custom path effect of the border
     */
    constructor(@ColorInt color: Int, width: Float, effect: PathEffect) {
        this.color = color
        this.width = width
        pathEffect = effect

        dashPathIntervals = null
        dashPathPhase = 0f
    }

    /**
     * Mutates [Paint] instance to apply [Border] to the paint setting its style, stroke width, color, and path effect.
     */
    @JvmOverloads
    fun applyToPaint(paint: Paint, alpha: Float = 1f) {
        paint.color = color.withCombinedAlpha(alpha)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.pathEffect = pathEffect
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null || javaClass != other.javaClass) return false

        other as Border

        if (color != other.color || width != other.width) {
            return false
        }

        return if (dashPathIntervals == null) {
            // Compare pathEffect's only if both borders are not "dash" ones.
            other.dashPathIntervals == null && pathEffect == other.pathEffect
        } else {
            dashPathIntervals.contentEquals(other.dashPathIntervals) && dashPathPhase == other.dashPathPhase
        }
    }

    override fun hashCode(): Int {
        var result = color
        result = result * 31 + width.toBits()

        if (dashPathIntervals == null) {
            result = result * 31 + pathEffect.hashCode()
        } else {
            result = result * 31 + dashPathIntervals.contentHashCode()
            result = result * 31 + dashPathPhase.hashCode()
        }

        return result
    }

    override fun toString(): String {
        return buildString(64) {
            append("Border(color=")
            appendColor(color)
            append(", width=")
            append(width)

            if (dashPathIntervals == null) {
                pathEffect?.also {
                    append(", pathEffect=")
                    append(it)
                }
            } else {
                append(", dashPathIntervals=")
                append(dashPathIntervals.contentToString())
                append(", dashPathPhase=")
                append(dashPathPhase)
            }

            append(')')
        }
    }
}

internal fun RectF.adjustForBorder(strokeWidth: Float) {
    val half = strokeWidth * 0.5f

    left += half
    top += half
    right -= half
    bottom -= half
}

/**
 * Represents all the supported animation types for border
 */
enum class BorderAnimationType {
    /**
     * Only internal shape of border is animated, stroke width is not.
     */
    ONLY_SHAPE,

    /**
     * Only stroke width of border is animated, internal shape is not.
     */
    ONLY_WIDTH,

    /**
     * Both internal shape of border and stroke width are animated
     */
    SHAPE_AND_WIDTH
}