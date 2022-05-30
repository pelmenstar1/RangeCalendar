package io.github.pelmenstar1.rangecalendar

import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.annotation.ColorInt

class Border {
    @ColorInt
    val color: Int
    val width: Float
    val dashPathIntervals: FloatArray?
    val dashPathPhase: Float

    constructor(@ColorInt color: Int, width: Float) {
        this.color = color
        this.width = width
        dashPathIntervals = null
        dashPathPhase = 0f
    }

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

enum class BorderAnimationType {
    ONLY_SHAPE,
    ONLY_WIDTH,
    SHAPE_AND_WIDTH
}