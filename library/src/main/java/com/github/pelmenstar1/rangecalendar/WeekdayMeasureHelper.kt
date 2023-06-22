package com.github.pelmenstar1.rangecalendar

import android.graphics.Paint
import com.github.pelmenstar1.rangecalendar.utils.getTextBoundsArray

object WeekdayMeasureHelper {
    fun computeWeekdayWidthAndMaxHeight(weekdays: Array<out String>, offset: Int, paint: Paint, outWidths: FloatArray): Float {
        var maxHeight = -1

        paint.getTextBoundsArray(weekdays, offset, offset + 7) { i, width, height ->
            if (height > maxHeight) {
                maxHeight = height
            }

            outWidths[i + offset] = width.toFloat()
        }

        return maxHeight.toFloat()
    }
}