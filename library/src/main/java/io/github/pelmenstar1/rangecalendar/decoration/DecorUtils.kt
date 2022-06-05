package io.github.pelmenstar1.rangecalendar.decoration

import android.graphics.Paint
import android.graphics.RectF
import io.github.pelmenstar1.rangecalendar.Border
import io.github.pelmenstar1.rangecalendar.BorderAnimationType
import io.github.pelmenstar1.rangecalendar.PackedRectF
import io.github.pelmenstar1.rangecalendar.adjustForBorder

internal fun Border.doDrawPreparation(
    animatedBounds: PackedRectF,
    endBounds: PackedRectF,
    animationFraction: Float,
    borderAnimationType: BorderAnimationType,
    paint: Paint,
    outRect: RectF
) {
    applyToPaint(paint)

    val animatedBorderWidth: Float

    when(borderAnimationType) {
        BorderAnimationType.ONLY_SHAPE -> {
            animatedBorderWidth = width

            animatedBounds.setTo(outRect)
        }
        BorderAnimationType.ONLY_WIDTH -> {
            animatedBorderWidth = width * animationFraction
            paint.strokeWidth = animatedBorderWidth

            endBounds.setTo(outRect)
        }
        BorderAnimationType.SHAPE_AND_WIDTH -> {
            animatedBorderWidth = width * animationFraction
            paint.strokeWidth = animatedBorderWidth

            animatedBounds.setTo(outRect)
        }
    }

    outRect.adjustForBorder(animatedBorderWidth)
}