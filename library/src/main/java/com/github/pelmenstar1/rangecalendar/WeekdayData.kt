package com.github.pelmenstar1.rangecalendar

import android.icu.text.DateFormatSymbols
import android.os.Build
import java.util.Calendar
import java.util.Locale

class WeekdayData(val weekdays: Array<out String>) {
    companion object {
        const val SHORT_WEEKDAYS_OFFSET = 0
        const val NARROW_WEEKDAYS_OFFSET = 7

        fun get(locale: Locale): WeekdayData {
            return WeekdayData(weekdays = getWeekdays(locale))
        }

        fun getOffsetByWeekdayType(type: WeekdayType): Int {
            return if (type == WeekdayType.SHORT) {
                SHORT_WEEKDAYS_OFFSET
            } else {
                NARROW_WEEKDAYS_OFFSET
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun getWeekdays(locale: Locale): Array<out String> {
            val resultArrayLength: Int
            val shortWeekdays: Array<String>
            var narrowWeekdays: Array<String>? = null

            if (Build.VERSION.SDK_INT >= 24) {
                val symbols = DateFormatSymbols.getInstance(locale)
                shortWeekdays = symbols.shortWeekdays

                narrowWeekdays = symbols.getWeekdays(
                    DateFormatSymbols.FORMAT,
                    DateFormatSymbols.NARROW
                )
                resultArrayLength = 14
            } else {
                shortWeekdays = java.text.DateFormatSymbols.getInstance(locale).shortWeekdays
                resultArrayLength = 7
            }

            // Cast to array with non-null elements because in the end the array won't have any null elements.
            val weekdays = arrayOfNulls<String>(resultArrayLength) as Array<String>

            copyAndFixWeekdaysOrder(shortWeekdays, weekdays, offset = SHORT_WEEKDAYS_OFFSET)

            if (narrowWeekdays != null) {
                copyAndFixWeekdaysOrder(narrowWeekdays, weekdays, offset = NARROW_WEEKDAYS_OFFSET)
            }

            return weekdays
        }

        // In format that DateFormatSymbols returns, Sunday is the first day of the week and 0 element is null, then goes Sun, Mon, Tue, ...
        // But it's better for logic when first day of week is Monday and the elements start from 0 element.
        // The method copies elements from source to dest with some reordering.
        private fun copyAndFixWeekdaysOrder(source: Array<out String>, dest: Array<String>, offset: Int) {
            System.arraycopy(source, 2, dest, offset, 6)

            // Sunday is the last day of week
            dest[offset + 6] = source[Calendar.SUNDAY]
        }
    }
}