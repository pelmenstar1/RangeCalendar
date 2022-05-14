package io.github.pelmenstar1.rangecalendar

import android.content.res.ColorStateList
import android.util.TypedValue
import android.annotation.SuppressLint
import android.content.Context
import android.icu.text.DateFormatSymbols
import android.os.Build
import androidx.annotation.AttrRes
import androidx.core.content.res.ResourcesCompat
import java.lang.IllegalArgumentException

internal class CalendarResources(context: Context) {
    val dayNumberSizes: LongArray
    val weekdayWidths: IntArray
    val dayNumberTextSize: Float
    val hPadding: Float
    val colorPrimary: Int
    val colorPrimaryDark: Int
    val cellSize: Float
    val yCellMargin: Float
    val hoverColor: Int
    val textColor: Int
    val outMonthTextColor: Int
    val disabledTextColor: Int
    val weekdayTextSize: Float
    val weekdays: Array<String>
    val shortWeekdayRowHeight: Float
    val narrowWeekdayRowHeight: Float
    val weekdayRowMarginBottom: Float
    val colorControlNormal: ColorStateList

    private fun computeWeekdayWidthAndMaxHeight(offset: Int): Float {
        var maxHeight = -1
        for (i in offset until offset + 7) {
            val name = weekdays[i]

            val size = getTextBounds(name, weekdayTextSize)
            val height = PackedSize.getHeight(size)
            if (height > maxHeight) {
                maxHeight = height
            }

            weekdayWidths[i] = PackedSize.getWidth(size)
        }

        return maxHeight.toFloat()
    }

    init {
        val res = context.resources

        colorPrimary = getColorPrimary(context)
        colorPrimaryDark = colorPrimary.darkerColor(0.4f)
        textColor = getTextColor(context)
        colorControlNormal = getColorStateListFromAttribute(context, R.attr.colorControlNormal)
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
        dayNumberSizes = LongArray(31) { i -> getTextBounds(DAYS[i], weekdayTextSize) }

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

        weekdayWidths = IntArray(weekdaysLength)
        shortWeekdayRowHeight = computeWeekdayWidthAndMaxHeight(SHORT_WEEKDAYS_OFFSET)
        narrowWeekdayRowHeight = if (Build.VERSION.SDK_INT >= 24) {
            computeWeekdayWidthAndMaxHeight(NARROW_WEEKDAYS_OFFSET)
        } else Float.NaN
    }

    companion object {
        private val DAYS: Array<String>

        private val SINGLE_INT_ARRAY = IntArray(1)
        private val HOVER_STATE = intArrayOf(android.R.attr.state_hovered, android.R.attr.state_enabled)
        private val ENABLED_STATE = intArrayOf(android.R.attr.state_enabled)
        private val EMPTY_STATE = IntArray(0)

        const val SHORT_WEEKDAYS_OFFSET = 0
        const val NARROW_WEEKDAYS_OFFSET = 7

        fun getDayText(day: Int) = DAYS[day - 1]

        private fun getColorPrimary(context: Context): Int {
            return getColorFromAttribute(context, R.attr.colorPrimary)
        }

        @SuppressLint("PrivateResource")
        private fun getTextColor(context: Context): Int {
            val theme = context.theme
            val array = theme.obtainStyledAttributes(
                R.style.TextAppearance_AppCompat,
                R.styleable.TextAppearance
            )

            val colorList = array.getColorStateList(R.styleable.TextAppearance_android_textColor)
            array.recycle()

            return colorList!!.getColorForState(ENABLED_STATE, 0)
        }

        private fun getHoverColor(context: Context): Int {
            val hoverList = getColorStateListFromAttribute(context, R.attr.colorControlHighlight)

            return hoverList.getColorForState(HOVER_STATE, 0)
        }

        private fun getColorFromAttribute(context: Context, @AttrRes resId: Int): Int {
            val typedValue = TypedValue()

            val theme = context.theme
            if (theme.resolveAttribute(resId, typedValue, true)) {
                val type = typedValue.type

                return if (type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
                    typedValue.data
                } else {
                    ResourcesCompat.getColor(context.resources, typedValue.resourceId, theme)
                }
            }

            throw IllegalArgumentException("Attribute $resId isn't defined")
        }

        private fun getColorStateListFromAttribute(context: Context, @AttrRes resId: Int): ColorStateList {
            SINGLE_INT_ARRAY[0] = resId

            val array = context.obtainStyledAttributes(SINGLE_INT_ARRAY)
            val list = array.getColorStateList(0)
            array.recycle()

            return list!!
        }

        init {
            val buffer = CharArray(2)

            DAYS = Array(31) { i ->
                val textLength: Int
                val day = i + 1

                if (day < 10) {
                    textLength = 1

                    buffer[0] = ('0'.code + day).toChar()
                } else {
                    textLength = 2

                    buffer[0] = ('0'.code + (day / 10)).toChar()
                    buffer[1] = ('0'.code + (day % 10)).toChar()
                }

                String(buffer, 0, textLength)
            }
        }
    }
}