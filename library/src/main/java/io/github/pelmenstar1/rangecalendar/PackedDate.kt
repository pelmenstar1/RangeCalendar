package io.github.pelmenstar1.rangecalendar

import io.github.pelmenstar1.rangecalendar.TimeUtils.isLeapYear
import io.github.pelmenstar1.rangecalendar.utils.floorMod
import java.time.LocalDate
import java.util.*

internal fun PackedDate(year: Int, month: Int, dayOfMonth: Int): PackedDate {
    require(year in 0..PackedDate.MAX_YEAR) { "year=$year" }
    require(month in 1..12) { "month=$month" }

    val daysInMonth = TimeUtils.getDaysInMonth(year, month)

    require(
        dayOfMonth in 1..TimeUtils.getDaysInMonth(year, month)
    ) { "dayOfMonth=$dayOfMonth (daysInMonth=$daysInMonth)" }

    return PackedDate(year shl 16 or (month shl 8) or dayOfMonth)
}

internal fun PackedDate(ym: YearMonth, dayOfMonth: Int): PackedDate {
    return PackedDate(ym.year, ym.month, dayOfMonth)
}

// Some code was taken from OpenJDK
// https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/time/LocalDate.java
@JvmInline
internal value class PackedDate(val bits: Int) {
    /* Packed values:
       - 32-16 bits - year
       - 16-8 bits - month
       - 8-0 bits - day
     */

    val year: Int
        get() = (bits shr 16) and 0xffff

    val month: Int
        get() = (bits shr 8) and 0xff

    val dayOfMonth: Int
        get() = bits and 0xff

    val dayOfWeek: Int
        get() {
            return floorMod(toEpochDay() + 3L, 7L).toInt() + 1
        }

    fun toCalendar(calendar: Calendar) {
        // month in Calendar is in [0; 11], but "our" month is in [1; 12], so we need to minus 1
        calendar.set(year, month - 1, dayOfMonth)
    }

    fun toLocalDate(): LocalDate {
        return LocalDate.of(year, month, dayOfMonth)
    }

    fun toEpochDay(): Long {
        val year = year
        val yearL = year.toLong()
        val month = month.toLong()
        val day = dayOfMonth.toLong()

        var total = 0L

        total += 365L * yearL
        total += ((yearL + 3L) / 4L - (yearL + 99L) / 100L + (yearL + 399L) / 400L)
        total += (367L * month - 362L) / 12L
        total += day - 1L

        if (month > 2L) {
            total--
            if (!isLeapYear(year)) {
                total--
            }
        }

        return total - DAYS_0000_TO_1970
    }

    companion object {
        const val MILLIS_IN_DAY = (24 * 60 * 60000).toLong()
        const val MAX_YEAR = 65535

        val MIN_DATE = PackedDate(257)
        val MAX_DATE = PackedDate(-6234)

        const val MIN_DATE_EPOCH = -719528L
        const val MAX_DATE_EPOCH = 23217002L

        private const val DAYS_PER_CYCLE = 146097L
        private const val DAYS_0000_TO_1970 = DAYS_PER_CYCLE * 5 - (30 * 365 + 7)

        fun today(timeZone: TimeZone = TimeZone.getDefault()): PackedDate {
            return fromEpochDay(todayEpochDay(timeZone))
        }

        fun todayEpochDay(timeZone: TimeZone = TimeZone.getDefault()): Long {
            val utcMillis = System.currentTimeMillis()
            val localMillis = utcMillis + timeZone.getOffset(utcMillis)

            return localMillis / MILLIS_IN_DAY
        }

        fun fromEpochDay(epochDay: Long): PackedDate {
            val zeroDay = epochDay + DAYS_0000_TO_1970 - 60L

            var yearEst = (400L * zeroDay + 591L) / DAYS_PER_CYCLE
            var doyEst = zeroDay - (365L * yearEst + yearEst / 4L - yearEst / 100L + yearEst / 400L)

            if (doyEst < 0) {
                yearEst--
                doyEst = zeroDay - (365L * yearEst + yearEst / 4L - yearEst / 100L + yearEst / 400L)
            }

            val marchDoy0 = doyEst.toInt()
            val marchMonth0 = (marchDoy0 * 5 + 2) / 153
            val month = (marchMonth0 + 2) % 12 + 1
            val dom = marchDoy0 - (marchMonth0 * 306 + 5) / 10 + 1
            yearEst += (marchMonth0 / 10).toLong()

            return PackedDate(yearEst.toInt(), month, dom)
        }

        fun fromLocalDate(date: LocalDate): PackedDate {
            require(isValidLocalDate(date)) { "LocalDate is out of valid range" }

            return PackedDate(date.year, date.monthValue, date.dayOfMonth)
        }

        fun isValidEpochDay(epochDay: Long): Boolean {
            return epochDay in MIN_DATE_EPOCH..MAX_DATE_EPOCH
        }

        private inline fun localDateCompare(
            date: LocalDate,
            year: Int, month: Int, dayOfMonth: Int,
            op: (Int, Int) -> Boolean
        ): Boolean {
            return op(date.year, year) &&
                    op(date.monthValue, month) &&
                    op(date.dayOfMonth, dayOfMonth)
        }

        fun isValidLocalDate(date: LocalDate): Boolean {
            return localDateCompare(date, 0, 1, 1) { a, b -> a >= b } &&
                    localDateCompare(date, MAX_YEAR, 12, 30) { a, b -> a <= b }
        }
    }
}