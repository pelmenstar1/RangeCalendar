package com.github.pelmenstar1.rangecalendar

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Paint
import androidx.core.graphics.alpha
import com.github.pelmenstar1.rangecalendar.utils.getColorFromAttribute
import com.github.pelmenstar1.rangecalendar.utils.getColorStateListFromAttribute
import com.github.pelmenstar1.rangecalendar.utils.getLocaleCompat
import com.github.pelmenstar1.rangecalendar.utils.getTextBoundsArray
import com.github.pelmenstar1.rangecalendar.utils.withoutAlpha

internal class CalendarResources(context: Context) {
    val hPadding: Float
    val cellSize: Float

    val colorPrimary: Int
    val hoverAlpha: Float
    val textColor: Int
    val outMonthTextColor: Int
    val disabledTextColor: Int

    val weekdayTextSize: Float
    val defaultWeekdayData: WeekdayData

    val dayNumberTextSize: Float

    // It's a precomputed text sizes for day numbers using default system font and dayNumberTextSize
    val defaultDayNumberSizes: PackedSizeArray

    val weekdayRowMarginBottom: Float
    val colorControlNormal: ColorStateList

    init {
        val res = context.resources

        colorPrimary = context.getColorFromAttribute(androidx.appcompat.R.attr.colorPrimary)
        textColor = getTextColor(context)
        colorControlNormal =
            context.getColorStateListFromAttribute(androidx.appcompat.R.attr.colorControlNormal)!!

        outMonthTextColor = colorControlNormal.getColorForState(ENABLED_STATE, textColor)
        disabledTextColor = colorControlNormal.getColorForState(EMPTY_STATE, textColor)
        hoverAlpha = getHoverAlpha(context)

        hPadding = res.getDimension(R.dimen.rangeCalendar_paddingH)
        weekdayTextSize = res.getDimension(R.dimen.rangeCalendar_weekdayTextSize)
        dayNumberTextSize = res.getDimension(R.dimen.rangeCalendar_dayNumberTextSize)
        cellSize = res.getDimension(R.dimen.rangeCalendar_cellSize)
        weekdayRowMarginBottom = res.getDimension(R.dimen.rangeCalendar_weekdayRowMarginBottom)

        // Compute text size of numbers in [0; 31]
        val paint = Paint().apply {
            textSize = dayNumberTextSize
        }

        defaultDayNumberSizes = PackedSizeArray(31)
        paint.getTextBoundsArray(DAYS) { i, width, height ->
            defaultDayNumberSizes[i] = PackedSize(width, height)
        }

        defaultWeekdayData = WeekdayData.get(context.getLocaleCompat())
    }

    companion object {
        val DAYS = arrayOf(
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
            "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
            "21", "22", "23", "24", "25", "26", "27", "28", "29", "30",
            "31"
        )

        private val HOVER_STATE =
            intArrayOf(android.R.attr.state_hovered, android.R.attr.state_enabled)
        private val ENABLED_STATE = intArrayOf(android.R.attr.state_enabled)
        private val EMPTY_STATE = IntArray(0)

        private const val DEFAULT_HOVER_ALPHA = 0.12f

        fun getDayText(day: Int) = DAYS[day - 1]

        @SuppressLint("PrivateResource")
        private fun getTextColor(context: Context): Int {
            val theme = context.theme
            val array = theme.obtainStyledAttributes(
                androidx.appcompat.R.style.TextAppearance_AppCompat,
                androidx.appcompat.R.styleable.TextAppearance
            )

            try {
                val colorList =
                    array.getColorStateList(androidx.appcompat.R.styleable.TextAppearance_android_textColor)

                return colorList!!.getColorForState(ENABLED_STATE, 0)
            } finally {
                array.recycle()
            }
        }

        private fun getHoverAlpha(context: Context): Float {
            val hoverList = context.getColorStateListFromAttribute(androidx.appcompat.R.attr.colorControlHighlight)

            if (hoverList != null) {
                val hoverColor = hoverList.getColorForState(HOVER_STATE, hoverList.defaultColor)

                // Check if the color is black without alpha channel. If it is, we use that value.
                // Otherwise, use the default hover alpha.
                if (hoverColor.withoutAlpha() == 0) {
                    return hoverColor.alpha / 255f
                }
            }

            return DEFAULT_HOVER_ALPHA
        }
    }
}