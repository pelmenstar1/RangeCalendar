package com.github.pelmenstar1.rangecalendar.decoration

import android.graphics.Paint
import android.graphics.RectF
import com.github.pelmenstar1.rangecalendar.Border
import com.github.pelmenstar1.rangecalendar.BorderAnimationType
import com.github.pelmenstar1.rangecalendar.adjustForBorder
import com.github.pelmenstar1.rangecalendar.PackedRectFArray

internal fun Border.doDrawPreparation(
    animatedBoundsArray: PackedRectFArray, endBoundsArray: PackedRectFArray,
    arrayIndex: Int,
    animationFraction: Float,
    borderAnimationType: BorderAnimationType,
    paint: Paint,
    outRect: RectF
) {
    paint.style = Paint.Style.STROKE
    applyToPaint(paint)

    val animatedBorderWidth: Float
    val outRectArray: PackedRectFArray

    when (borderAnimationType) {
        BorderAnimationType.ONLY_SHAPE -> {
            animatedBorderWidth = width

            outRectArray = animatedBoundsArray
        }

        BorderAnimationType.ONLY_WIDTH -> {
            animatedBorderWidth = width * animationFraction
            paint.strokeWidth = animatedBorderWidth

            outRectArray = endBoundsArray
        }

        BorderAnimationType.SHAPE_AND_WIDTH -> {
            animatedBorderWidth = width * animationFraction
            paint.strokeWidth = animatedBorderWidth

            outRectArray = animatedBoundsArray
        }
    }

    outRectArray.toObjectRect(arrayIndex, outRect)
    outRect.adjustForBorder(animatedBorderWidth)
}