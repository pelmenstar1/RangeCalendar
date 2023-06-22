package com.github.pelmenstar1.rangecalendar

import android.graphics.Canvas
import android.graphics.Paint

internal class WeekdayRow(
    private val defaultShortWeekdayRowHeight: Float,
    private val defaultNarrowWeekdayRowHeight: Float,
    private val defaultWeekdayWidths: FloatArray,
    private val localizedWeekdayData: WeekdayData,
    private val textPaint: Paint
) {
    private var weekdayWidths: FloatArray = defaultWeekdayWidths

    private var isCustomWeekdays = false
    private var weekdayData = localizedWeekdayData

    /**
     * Height of the row, in pixels.
     */
    var height: Float = defaultShortWeekdayRowHeight
        private set

    /**
     * Gets or sets type of weekdays in the row. In setter the value should be "resolved" (via [WeekdayType.resolved])
     */
    var type = WeekdayType.SHORT
        set(value) {
            if (field != value) {
                field = value

                // Changing the type when custom weekdays are used should have no effect
                if (!isCustomWeekdays) {
                    // If we're still using defaultWeekdayWidths, it means that text size of weekday haven't been changed and
                    // we can use default measurements.
                    if (weekdayWidths === defaultWeekdayWidths) {
                        height = if (value == WeekdayType.SHORT)
                            defaultShortWeekdayRowHeight
                        else
                            defaultNarrowWeekdayRowHeight
                    } else {
                        onMeasurementsChanged()
                    }
                }
            }
        }

    var weekdays: Array<out String>?
        get() = weekdayData.weekdays
        set(value) {
            weekdayData = if (value == null) {
                localizedWeekdayData
            } else {
                WeekdayData(value)
            }

            isCustomWeekdays = value != null

            onMeasurementsChanged()
        }

    /**
     * The method should be called when some measurements might be changed.
     */
    fun onMeasurementsChanged() {
        val offset = WeekdayData.getOffsetByWeekdayType(type)

        // If weekdayWidths is default one, create a new array not to overwrite default one.
        if (weekdayWidths === defaultWeekdayWidths) {
            // If we can't use precomputed widths, we re-compute them only for specified weekday type, so length is 7.
            weekdayWidths = FloatArray(7)
        }

        height = WeekdayMeasureHelper.computeWeekdayWidthAndMaxHeight(
            weekdayData.weekdays, offset, textPaint, weekdayWidths
        )
    }

    fun draw(c: Canvas, x: Float, columnWidth: Float) {
        var midX = x + columnWidth * 0.5f

        val offset = WeekdayData.getOffsetByWeekdayType(type)
        val widthsOffset = if (weekdayWidths === defaultWeekdayWidths) offset else 0

        val weekdays = weekdayData.weekdays
        val textY = height

        for (i in 0 until 7) {
            val text = weekdays[i + offset]
            val width = weekdayWidths[i + widthsOffset]

            val textX = midX - width * 0.5f

            c.drawText(text, textX, textY, textPaint)

            midX += columnWidth
        }
    }
}