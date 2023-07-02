package com.github.pelmenstar1.rangecalendar

import java.time.DayOfWeek
import java.util.Calendar

/**
 * Day of week that starts from Monday. [value] is zero-based.
 */
@JvmInline
internal value class CompatDayOfWeek(val value: Int) {
    /**
     * Returns day of week number compatible with [Calendar] days - [Calendar.MONDAY], [Calendar.TUESDAY] etc.
     */
    fun toCalendarValue(): Int {
        if (value == 6) return Calendar.SUNDAY

        return value + Calendar.MONDAY
    }

    fun toEnumValue(): DayOfWeek {
        return DayOfWeek.of(value + 1)
    }

    companion object {
        val Undefined = CompatDayOfWeek(-1)

        val Monday = CompatDayOfWeek(0)
        val Tuesday = CompatDayOfWeek(1)
        val Wednesday = CompatDayOfWeek(2)
        val Thursday = CompatDayOfWeek(3)
        val Friday = CompatDayOfWeek(4)
        val Saturday = CompatDayOfWeek(5)
        val Sunday = CompatDayOfWeek(6)

        fun daysBetween(start: CompatDayOfWeek, end: CompatDayOfWeek): Int {
            return if (start.value <= end.value) {
                end.value - start.value
            } else {
                (7 - start.value) + end.value
            }
        }

        /**
         * Converts int day of week in [Calendar] days to [CompatDayOfWeek] format.
         */
        fun fromCalendarDayOfWeek(value: Int): CompatDayOfWeek {
            if (value == Calendar.SUNDAY) return Sunday

            return CompatDayOfWeek(value - Calendar.MONDAY)
        }

        fun fromEnumValue(dow: DayOfWeek): CompatDayOfWeek {
            return CompatDayOfWeek(dow.ordinal)
        }
    }
}