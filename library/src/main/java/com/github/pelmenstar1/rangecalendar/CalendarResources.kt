package com.github.pelmenstar1.rangecalendar

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.icu.text.DateFormatSymbols
import android.os.Build
import com.github.pelmenstar1.rangecalendar.utils.darkerColor
import com.github.pelmenstar1.rangecalendar.utils.getColorFromAttribute
import com.github.pelmenstar1.rangecalendar.utils.getColorStateListFromAttribute
import com.github.pelmenstar1.rangecalendar.utils.getLocaleCompat
import com.github.pelmenstar1.rangecalendar.utils.getTextBoundsArray

internal class CalendarResources(context: Context) {
    val hPadding: Float
    val cellSize: Float
    val yCellMargin: Float

    val colorPrimary: Int
    val colorPrimaryDark: Int
    val hoverColor: Int
    val textColor: Int
    val outMonthTextColor: Int
    val disabledTextColor: Int

    val weekdayTextSize: Float
    val weekdays: Array<String>

    val dayNumberTextSize: Float

    // Can only be used when text size is default one (dayNumberTextSize)
    val defaultDayNumberSizes: PackedSizeArray

    /*
     * These are precomputed values for default weekdayTextSize and cannot be used for another text size.
     */
    val defaultWeekdayWidths: FloatArray
    val defaultShortWeekdayRowHeight: Float
    val defaultNarrowWeekdayRowHeight: Float
    /* ----- */

    val weekdayRowMarginBottom: Float
    val colorControlNormal: ColorStateList

    init {
        val res = context.resources

        colorPrimary = context.getColorFromAttribute(androidx.appcompat.R.attr.colorPrimary)
        colorPrimaryDark = colorPrimary.darkerColor(0.4f)
        textColor = getTextColor(context)
        colorControlNormal = context.getColorStateListFromAttribute(androidx.appcompat.R.attr.colorControlNormal)
        outMonthTextColor = colorControlNormal.getColorForState(ENABLED_STATE, 0)
        disabledTextColor = colorControlNormal.getColorForState(EMPTY_STATE, 0)
        hoverColor = getHoverColor(context)

        hPadding = res.getDimension(R.dimen.rangeCalendar_paddingH)
        weekdayTextSize = res.getDimension(R.dimen.rangeCalendar_weekdayTextSize)
        dayNumberTextSize = res.getDimension(R.dimen.rangeCalendar_dayNumberTextSize)
        cellSize = res.getDimension(R.dimen.rangeCalendar_cellSize)
        yCellMargin = res.getDimension(R.dimen.rangeCalendar_yCellMargin)
        weekdayRowMarginBottom = res.getDimension(R.dimen.rangeCalendar_weekdayRowMarginBottom)

        // Compute text size of numbers in [0; 31]
        defaultDayNumberSizes = getTextBoundsArray(DAYS, dayNumberTextSize)

        // First element in getShortWeekDays() is empty and actual items start from 1
        // It's better to copy them to another array where elements start from 0
        val locale = context.getLocaleCompat()
        val shortWeekdays: Array<String>
        var narrowWeekdays: Array<String>? = null

        if (Build.VERSION.SDK_INT >= 24) {
            val symbols = DateFormatSymbols.getInstance(locale)
            shortWeekdays = symbols.shortWeekdays

            narrowWeekdays = symbols.getWeekdays(
                DateFormatSymbols.FORMAT,
                DateFormatSymbols.NARROW
            )
        } else {
            shortWeekdays = java.text.DateFormatSymbols.getInstance(locale).shortWeekdays
        }

        val weekdaysLength = if (Build.VERSION.SDK_INT >= 24) 14 else 7

        @Suppress("UNCHECKED_CAST")
        weekdays = arrayOfNulls<String?>(weekdaysLength) as Array<String>
        System.arraycopy(shortWeekdays, 1, weekdays, 0, 7)

        if (narrowWeekdays != null) {
            System.arraycopy(narrowWeekdays, 1, weekdays, 7, 7)
        }

        defaultWeekdayWidths = FloatArray(weekdaysLength)
        defaultShortWeekdayRowHeight = computeWeekdayWidthAndMaxHeight(SHORT_WEEKDAYS_OFFSET)
        defaultNarrowWeekdayRowHeight = if (Build.VERSION.SDK_INT >= 24) {
            computeWeekdayWidthAndMaxHeight(NARROW_WEEKDAYS_OFFSET)
        } else Float.NaN
    }

    private fun computeWeekdayWidthAndMaxHeight(offset: Int): Float {
        var maxHeight = -1

        getTextBoundsArray(weekdays, offset, offset + 7, weekdayTextSize, typeface = null) { i, size ->
            val height = size.height
            if (height > maxHeight) {
                maxHeight = height
            }

            defaultWeekdayWidths[i + offset] = size.width.toFloat()
        }

        return maxHeight.toFloat()
    }

    companion object {
        val DAYS = arrayOf(
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
            "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
            "21", "22", "23", "24", "25", "26", "27", "28", "29", "30",
            "31"
        )

        private val SINGLE_INT_ARRAY = IntArray(1)
        private val HOVER_STATE = intArrayOf(android.R.attr.state_hovered, android.R.attr.state_enabled)
        private val ENABLED_STATE = intArrayOf(android.R.attr.state_enabled)
        private val EMPTY_STATE = IntArray(0)

        const val SHORT_WEEKDAYS_OFFSET = 0
        const val NARROW_WEEKDAYS_OFFSET = 7

        fun getDayText(day: Int) = DAYS[day - 1]

        @SuppressLint("PrivateResource")
        private fun getTextColor(context: Context): Int {
            val theme = context.theme
            val array = theme.obtainStyledAttributes(
                androidx.appcompat.R.style.TextAppearance_AppCompat,
                androidx.appcompat.R.styleable.TextAppearance
            )

            try {
                val colorList = array.getColorStateList(androidx.appcompat.R.styleable.TextAppearance_android_textColor)

                return colorList!!.getColorForState(ENABLED_STATE, 0)
            } finally {
                array.recycle()
            }
        }

        private fun getHoverColor(context: Context): Int {
            val hoverList = context.getColorStateListFromAttribute(androidx.appcompat.R.attr.colorControlHighlight)

            return hoverList.getColorForState(HOVER_STATE, 0)
        }
    }
}