package com.github.pelmenstar1.rangecalendar

import android.icu.text.DateFormatSymbols
import android.os.Build
import java.util.Calendar
import java.util.Locale

/**
 * Saves weekdays in short format and in narrow format (if possible). [narrowWeekdays] is always non-null when API level >= 24
 */
internal class WeekdayData(val shortWeekdays: Array<out String>, val narrowWeekdays: Array<out String>?) {
    fun getWeekdays(type: WeekdayType): Array<out String> {
        return if (type == WeekdayType.SHORT) shortWeekdays else narrowWeekdays!!
    }

    companion object {
        fun get(locale: Locale): WeekdayData {
            var shortWeekdays: Array<String>
            var narrowWeekdays: Array<String>? = null

            if (Build.VERSION.SDK_INT >= 24) {
                val symbols = DateFormatSymbols.getInstance(locale)
                shortWeekdays = symbols.shortWeekdays

                narrowWeekdays = symbols.getWeekdays(
                    DateFormatSymbols.FORMAT,
                    DateFormatSymbols.NARROW
                )

                shortWeekdays = fixWeekdaysOrder(shortWeekdays)
                narrowWeekdays = fixWeekdaysOrder(narrowWeekdays)
            } else {
                shortWeekdays = java.text.DateFormatSymbols.getInstance(locale).shortWeekdays
                shortWeekdays = fixWeekdaysOrder(shortWeekdays)
            }

            return WeekdayData(shortWeekdays, narrowWeekdays)
        }

        // In format that DateFormatSymbols returns, Sunday is the first day of the week and 0 element is null, then goes Sun, Mon, Tue, ...
        // But it's better for logic when first day of week is Monday and the elements start from 0 element.
        // The method creates a new array with fixed order.
        @Suppress("UNCHECKED_CAST")
        private fun fixWeekdaysOrder(source: Array<out String>): Array<String> {
            // In the end, weekdays will contain only non-null elements
            val weekdays = arrayOfNulls<String>(7) as Array<String>

            System.arraycopy(source, 2, weekdays, 0, 6)

            // Sunday is the last day of week
            weekdays[6] = source[Calendar.SUNDAY]

            return weekdays
        }
    }
}