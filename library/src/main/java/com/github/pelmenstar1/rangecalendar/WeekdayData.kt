package com.github.pelmenstar1.rangecalendar

import android.icu.text.DateFormatSymbols
import android.os.Build
import com.github.pelmenstar1.rangecalendar.utils.getLazyValue
import java.text.FieldPosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Saves weekdays in short and narrow (if possible) formats
 */
internal class WeekdayData(private val locale: Locale) {
    private val symbols: Any
    private var shortWeekdays: Array<String>? = null
    private var narrowWeekdays: Array<String>? = null

    init {
        symbols = if (Build.VERSION.SDK_INT >= 24) {
            DateFormatSymbols.getInstance(locale)
        } else {
            java.text.DateFormatSymbols.getInstance(locale)
        }
    }

    private fun extractShortWeekdays(): Array<String> {
        val weekdays = if (Build.VERSION.SDK_INT >= 24) {
            (symbols as DateFormatSymbols).getWeekdays(DateFormatSymbols.FORMAT, DateFormatSymbols.SHORT)
        } else {
            (symbols as java.text.DateFormatSymbols).shortWeekdays
        }

        return fixWeekdaysOrder(weekdays)
    }

    private fun extractNarrowWeekdays(): Array<String> {
        return if (Build.VERSION.SDK_INT >= 24) {
            val weekdays =
                (symbols as DateFormatSymbols).getWeekdays(DateFormatSymbols.FORMAT, DateFormatSymbols.NARROW)

             fixWeekdaysOrder(weekdays)
        } else {
            extractNarrowWeekdaysThroughFormatter()
        }
    }

    @Suppress("UNCHECKED_CAST")
    // Internal for tests
    internal fun extractNarrowWeekdaysThroughFormatter(): Array<String> {
        // Seems lke the only way to extract narrow weekdays when API level < 24.
        // Though, there exists a private API: libcore.icu.LocaleData. It can be used with reflection to
        // get the "tiny" weekdays, but probably usage of the private API is not worth it.

        // 5 c's means standalone narrow weekday
        val formatter = SimpleDateFormat("ccccc", locale)
        val date = Date()
        val weekdays = arrayOfNulls<String>(7) as Array<String>
        val buffer = StringBuffer(16)
        val pos = FieldPosition(0)

        val mondayEpochMillis = PackedDate.MONDAY_EPOCH_MILLIS
        var currentEpochMillis = mondayEpochMillis

        for (weekday in 0 until 7) {
            date.time = currentEpochMillis

            buffer.setLength(0)
            formatter.format(date, buffer, pos)

            weekdays[weekday] = buffer.toString()

            currentEpochMillis += PackedDate.MILLIS_IN_DAY
        }

        return weekdays
    }

    fun getWeekdays(type: WeekdayType): Array<out String> {
        return when(type) {
            WeekdayType.SHORT -> getLazyValue(shortWeekdays, ::extractShortWeekdays) { shortWeekdays = it }
            WeekdayType.NARROW -> getLazyValue(narrowWeekdays, ::extractNarrowWeekdays) { narrowWeekdays = it }
        }
    }

    companion object {
        // In format that DateFormatSymbols returns, Sunday is the first day of the week and 0 element is null, then goes Sun, Mon, Tue, ...
        // But it's better for logic when first day of week is Monday and the elements start from 0 element.
        // The method creates a new array with fixed order.
        @Suppress("UNCHECKED_CAST")
        private fun fixWeekdaysOrder(source: Array<out String?>): Array<String> {
            // In the end, weekdays will contain only non-null elements
            val weekdays = arrayOfNulls<String>(7)

            System.arraycopy(source, 2, weekdays, 0, 6)

            // Sunday is the last day of week
            weekdays[6] = source[Calendar.SUNDAY]

            return weekdays as Array<String>
        }
    }
}