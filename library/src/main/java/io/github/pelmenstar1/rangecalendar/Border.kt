package io.github.pelmenstar1.rangecalendar

import android.graphics.*
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.ColorLong
import androidx.annotation.RequiresApi
import androidx.core.graphics.isSrgb
import io.github.pelmenstar1.rangecalendar.utils.asSrgbUnsafe
import io.github.pelmenstar1.rangecalendar.utils.packColorCompat

/**
 * Represents information needed for drawing border around some shape.
 */
class Border {
    @ColorLong
    private val convertedColorInternal: Long

    @ColorLong
    private val outColorInternal: Long

    private var pathEffect: DashPathEffect? = null

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
     * Color of border expressed by color int.
     *
     * If border color is initially specified by color long, color is converted to sRGB color space
     * and it causes additional allocations.
     */
    @get:ColorInt
    val color: Int
        get() = if (Build.VERSION.SDK_INT < 29) {
            // convertedColorInternal is *always* sRGB if API level is less than 29
            convertedColorInternal.asSrgbUnsafe()
        } else {
            Color.toArgb(outColorInternal)
        }

    /**
     * Color of border expressed by color long.
     *
     * Supported when API level is 26 and higher.
     */
    @get:RequiresApi(26)
    @get:ColorLong
    val colorLong: Long
        get() = outColorInternal

    /**
     * Constructs [Border] instance using color specified by color int and stroke width.
     *
     * @param color color of the border.
     * @param width stroke width of the border in pixels.
     */
    constructor(@ColorInt color: Int, width: Float) {
        outColorInternal = color.packColorCompat()
        convertedColorInternal = outColorInternal

        this.width = width
        dashPathIntervals = null
        dashPathPhase = 0f
    }

    /**
     * Constructs [Border] instance using color specified by color long and stroke width.
     *
     * Supported when API level is 26 and higher,
     * but actually color longs are used when API level is 29 or higher because
     * color long support was added to [Paint] in Android 10 (API level 29).
     * So before it, [color] is converted to color int and used as such.
     *
     * @param color color of the border.
     * @param width stroke width of the border in pixels.
     */
    @RequiresApi(26)
    constructor(@ColorLong color: Long, width: Float) {
        outColorInternal = color
        convertedColorInternal = convertColor(color)

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
        outColorInternal = color.packColorCompat()
        convertedColorInternal = outColorInternal

        this.width = width
        this.dashPathIntervals = dashPathIntervals
        this.dashPathPhase = dashPathPhase
    }

    /**
     * Constructs [Border] instance with dashed line.
     *
     * Supported when API level is 26 and higher,
     * but actually color longs are used when API level is 29 or higher because
     * color long support was added to [Paint] in Android 10 (API level 29).
     * So before it, [color] is converted to color int and used as such.
     *
     * @param color color of the border.
     * @param width stroke width of the border.
     * @param dashPathIntervals array with even length of floats specifying "on" and "off" length intervals of dashes
     * @param dashPathPhase an offset into the [dashPathIntervals] array (mod the sum of all of the intervals)
     */
    @RequiresApi(26)
    constructor(
        @ColorLong color: Long,
        width: Float,
        dashPathIntervals: FloatArray,
        dashPathPhase: Float
    ) {
        outColorInternal = color
        convertedColorInternal = convertColor(color)

        this.width = width
        this.dashPathIntervals = dashPathIntervals
        this.dashPathPhase = dashPathPhase
    }

    /**
     * Mutates [Paint] instance to apply [Border] to the paint setting its style, stroke width, color, and path effect.
     */
    fun applyToPaint(paint: Paint) {
        if (pathEffect == null && dashPathIntervals != null) {
            pathEffect = DashPathEffect(dashPathIntervals, dashPathPhase)
        }

        // convertedColorInternal is *always* sRGB when API level is less than 29.
        if (Build.VERSION.SDK_INT < 29 || convertedColorInternal.isSrgb) {
            paint.color = convertedColorInternal.asSrgbUnsafe()
        } else {
            paint.setColor(convertedColorInternal)
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.pathEffect = pathEffect
    }

    companion object {
        @ColorLong
        @RequiresApi(26)
        private fun convertColor(@ColorLong color: Long): Long {
            return if(Build.VERSION.SDK_INT >= 29 || color.isSrgb) {
                color
            } else {
                Color.pack(Color.toArgb(color))
            }
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