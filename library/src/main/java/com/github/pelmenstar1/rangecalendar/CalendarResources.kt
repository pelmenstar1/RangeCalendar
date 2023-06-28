package com.github.pelmenstar1.rangecalendar

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import com.github.pelmenstar1.rangecalendar.utils.darkerColor
import com.github.pelmenstar1.rangecalendar.utils.getColorFromAttribute
import com.github.pelmenstar1.rangecalendar.utils.getColorStateListFromAttribute
import com.github.pelmenstar1.rangecalendar.utils.getLocaleCompat
import com.github.pelmenstar1.rangecalendar.utils.getTextBoundsArray

internal class CalendarResources(context: Context) {
    val hPadding: Float
    val cellSize: Float

    val colorPrimary: Int
    val colorPrimaryDark: Int
    val hoverColor: Int
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
        colorPrimaryDark = colorPrimary.darkerColor(0.4f)
        textColor = getTextColor(context)
        colorControlNormal =
            context.getColorStateListFromAttribute(androidx.appcompat.R.attr.colorControlNormal)
        outMonthTextColor = colorControlNormal.getColorForState(ENABLED_STATE, 0)
        disabledTextColor = colorControlNormal.getColorForState(EMPTY_STATE, 0)
        hoverColor = getHoverColor(context)

        hPadding = res.getDimension(R.dimen.rangeCalendar_paddingH)
        weekdayTextSize = res.getDimension(R.dimen.rangeCalendar_weekdayTextSize)
        dayNumberTextSize = res.getDimension(R.dimen.rangeCalendar_dayNumberTextSize)
        cellSize = res.getDimension(R.dimen.rangeCalendar_cellSize)
        weekdayRowMarginBottom = res.getDimension(R.dimen.rangeCalendar_weekdayRowMarginBottom)

        // Compute text size of numbers in [0; 31]
        defaultDayNumberSizes = getTextBoundsArray(DAYS, dayNumberTextSize)

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

        private fun getHoverColor(context: Context): Int {
            val hoverList =
                context.getColorStateListFromAttribute(androidx.appcompat.R.attr.colorControlHighlight)

            return hoverList.getColorForState(HOVER_STATE, 0)
        }
    }
}