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
    private var _height: Float = Float.NaN

    /**
     * Height of the row, in pixels.
     */
    val height: Float
        get() {
            updateMeasurementsIfNecessary()

            return _height
        }

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

    private var _weekdays: Array<out String> = localizedWeekdayData.getWeekdays(WeekdayType.SHORT)

    var weekdays: Array<out String>?
        get() = _weekdays
        set(value) {
            _weekdays = value ?: localizedWeekdayData.getWeekdays(type)
            isCustomWeekdays = value != null

            onMeasurementsChanged()
        }

    var firstDayOfWeek = CompatDayOfWeek.Monday

    private var isMeasurementsDirty = true

    fun onMeasurementsChanged() {
        isMeasurementsDirty = true
    }

    private fun updateMeasurementsIfNecessary() {
        if (isMeasurementsDirty) {
            isMeasurementsDirty = false

            val widths = currentWidths
            var maxHeight = -1

            textPaint.getTextBoundsArray(_weekdays) { i, width, height ->
                maxHeight = max(maxHeight, height)
                widths[i] = width.toFloat()
            }

            _height = maxHeight.toFloat()
        }
    }

    fun draw(canvas: Canvas, x: Float, columnWidth: Float) {
        updateMeasurementsIfNecessary()

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