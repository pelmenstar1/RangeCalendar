package com.github.pelmenstar1.rangecalendar

import android.graphics.Canvas
import android.graphics.Paint

internal class WeekdayRow(
    private val localizedWeekdayData: WeekdayData,
    private val textPaint: Paint
) {
    private var isCustomWeekdays = false
    private val currentWidths = FloatArray(7)

    /**
     * Height of the row, in pixels.
     */
    var height: Float = Float.NaN
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
                    _weekdays = localizedWeekdayData.getWeekdays(value)

                    onMeasurementsChanged()
                }
            }
        }

    private var _weekdays: Array<out String> = localizedWeekdayData.shortWeekdays

    var weekdays: Array<out String>?
        get() = _weekdays
        set(value) {
            _weekdays = value ?: localizedWeekdayData.getWeekdays(type)
            isCustomWeekdays = value != null

            onMeasurementsChanged()
        }

    init {
        onMeasurementsChanged()
    }

    fun onMeasurementsChanged() {
        height = WeekdayMeasureHelper.computeWidthsAndMaxHeight(_weekdays, textPaint, currentWidths)
    }

    fun draw(c: Canvas, x: Float, columnWidth: Float) {
        var midX = x + columnWidth * 0.5f

        val textY = height

        for (i in 0 until 7) {
            val text = _weekdays[i]
            val width = currentWidths[i]

            val textX = midX - width * 0.5f

            c.drawText(text, textX, textY, textPaint)

            midX += columnWidth
        }
    }
}