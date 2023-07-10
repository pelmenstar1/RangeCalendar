package com.github.pelmenstar1.rangecalendar

import android.graphics.Canvas
import android.graphics.Paint
import com.github.pelmenstar1.rangecalendar.utils.getTextBoundsArray
import kotlin.math.max

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

    var firstDayOfWeek = CompatDayOfWeek.Monday

    init {
        onMeasurementsChanged()
    }

    fun onMeasurementsChanged() {
        val widths = currentWidths
        var maxHeight = -1

        textPaint.getTextBoundsArray(_weekdays) { i, width, height ->
            maxHeight = max(maxHeight, height)
            widths[i] = width.toFloat()
        }

        height = maxHeight.toFloat()
    }

    fun draw(canvas: Canvas, x: Float, columnWidth: Float) {
        var midX = x + columnWidth * 0.5f
        val firstDow = firstDayOfWeek.value

        for (i in firstDow until 7) {
            drawWeekday(canvas, midX, i)

            midX += columnWidth
        }

        for (i in 0 until firstDow) {
            drawWeekday(canvas, midX, i)

            midX += columnWidth
        }
    }

    private fun drawWeekday(canvas: Canvas,  midX: Float, index: Int) {
        val text = _weekdays[index]
        val width = currentWidths[index]

        val textX = midX - width * 0.5f

        canvas.drawText(text, textX, height, textPaint)
    }
}