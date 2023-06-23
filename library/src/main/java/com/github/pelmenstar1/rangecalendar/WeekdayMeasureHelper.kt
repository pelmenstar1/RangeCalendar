package com.github.pelmenstar1.rangecalendar

import android.graphics.Paint
import com.github.pelmenstar1.rangecalendar.utils.getTextBoundsArray
import kotlin.math.max

object WeekdayMeasureHelper {
    fun computeWidthsAndMaxHeight(
        weekdays: Array<out String>,
        paint: Paint,
        outWidths: FloatArray
    ): Float {
        var maxHeight = -1

        paint.getTextBoundsArray(weekdays, start = 0, end = 7) { i, width, height ->
            maxHeight = max(maxHeight, height)
            outWidths[i] = width.toFloat()
        }

        return maxHeight.toFloat()
    }
}