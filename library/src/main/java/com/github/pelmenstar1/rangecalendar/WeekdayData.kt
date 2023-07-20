package com.github.pelmenstar1.rangecalendar

import android.icu.text.DateFormatSymbols
import android.os.Build
import androidx.annotation.RequiresApi
import com.github.pelmenstar1.rangecalendar.utils.getLazyValue
import java.util.Calendar
import java.util.Locale

/**
 * Saves weekdays in short and narrow (if possible) formats
 */
internal class WeekdayData(locale: Locale) {
    private val symbols: Any
    private var shortWeekdays: Array<out String>? = null
    private var narrowWeekdays: Array<out String>? = null

    init {
        symbols = if (Build.VERSION.SDK_INT >= 24) {
            DateFormatSymbols.getInstance(locale)
        } else {
            java.text.DateFormatSymbols.getInstance(locale)
        }
    }

    private fun extractShortWeekdays(): Array<out String> {
        val weekdays = if (Build.VERSION.SDK_INT >= 24) {
            (symbols as DateFormatSymbols).shortWeekdays
        } else {
            (symbols as java.text.DateFormatSymbols).shortWeekdays
        }

        return fixWeekdaysOrder(weekdays)
    }

    @RequiresApi(24)
    private fun extractNarrowWeekdays(): Array<out String> {
        val weekdays = (symbols as DateFormatSymbols).getWeekdays(DateFormatSymbols.FORMAT, DateFormatSymbols.NARROW)

        return fixWeekdaysOrder(weekdays)
    }

    fun getWeekdays(type: WeekdayType): Array<out String> {
        return when(type) {
            WeekdayType.SHORT -> getLazyValue(shortWeekdays, ::extractShortWeekdays) { shortWeekdays = it }
            WeekdayType.NARROW -> {
                if (Build.VERSION.SDK_INT < 24) {
                    throw RuntimeException("Api level < 24")
                }

                getLazyValue(narrowWeekdays, ::extractNarrowWeekdays) { narrowWeekdays = it }
            }
        }
    }

    companion object {
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