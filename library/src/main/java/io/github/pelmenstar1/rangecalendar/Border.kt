package io.github.pelmenstar1.rangecalendar

import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.annotation.ColorInt

/**
 * Represents information needed for drawing border around some shape.
 */
class Border {
    /**
     * Color of border
     */
    @ColorInt
    val color: Int

    /**
     * Stroke width of border
     */
    val width: Float

    /**
     * Array with even length of floats specifying "on" and "off" length intervals of dashes
     */
    val dashPathIntervals: FloatArray?

    /**
     * An offset into the [dashPathIntervals] array (mod the sum of all of the intervals)
     */
    val dashPathPhase: Float

    /**
     * Constructs [Border] instance.
     *
     * @param color color of the border.
     * @param width stroke width of the border.
     */
    constructor(@ColorInt color: Int, width: Float) {
        this.color = color
        this.width = width
        dashPathIntervals = null
        dashPathPhase = 0f
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
    }

    private var pathEffect: DashPathEffect? = null

    /**
     * Mutates [Paint] instance to apply [Border] to the paint setting its style, stroke width, color, and path effect.
     */
    fun applyToPaint(paint: Paint) {
        if (pathEffect == null && dashPathIntervals != null) {
            pathEffect = DashPathEffect(dashPathIntervals, dashPathPhase)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.color = color
        paint.pathEffect = pathEffect
    }
}

internal fun PackedRectF.adjustBoundsForBorder(strokeWidth: Float): PackedRectF {
    val half = strokeWidth * 0.5f

    return PackedRectF(
        left + half,
        top + half,
        right - half,
        bottom - half
    )
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