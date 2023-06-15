@file:Suppress("NOTHING_TO_INLINE")

package com.github.pelmenstar1.rangecalendar

import com.github.pelmenstar1.rangecalendar.TimeUtils.isLeapYear
import com.github.pelmenstar1.rangecalendar.utils.floorMod
import java.time.LocalDate
import java.util.*

internal fun PackedDate(year: Int, month: Int, dayOfMonth: Int): PackedDate {
    require(year in 0..PackedDate.MAX_YEAR) { "year=$year" }
    require(month in 1..12) { "month=$month" }

    val daysInMonth = TimeUtils.getDaysInMonth(year, month)

    require(
        dayOfMonth in 1..TimeUtils.getDaysInMonth(year, month)
    ) { "dayOfMonth=$dayOfMonth (daysInMonth=$daysInMonth)" }

    return PackedDate((year shl PackedDate.YEAR_SHIFT) or (month shl PackedDate.MONTH_SHIFT) or dayOfMonth)
}

internal fun PackedDate(ym: YearMonth, dayOfMonth: Int): PackedDate {
    return PackedDate(ym.year, ym.month, dayOfMonth)
}

// Some code was taken from OpenJDK
// https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/time/LocalDate.java
@JvmInline
internal value class PackedDate(val bits: Int) {
    // 0..8 bits - day of month
    // 8..16 bits - month
    // 16..32 bits - year

    val year: Int
        get() = (bits shr YEAR_SHIFT) and 0xffff

    val month: Int
        get() = (bits shr MONTH_SHIFT) and 0xff

    val yearMonth: YearMonth
        get() = YearMonth(year, month)

    val dayOfMonth: Int
        get() = bits and 0xff

    val dayOfWeek: Int
        get() = getDayOfWeek(toEpochDay())

    fun plusDays(days: Int): PackedDate {
        if (days == 0) {
            return this
        }

        return fromEpochDay(toEpochDay() + days)
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
        val month = month

        val yearL = year.toLong()
        var total = 0L

        total += 365L * yearL
        total += ((yearL + 3L) / 4L - (yearL + 99L) / 100L + (yearL + 399L) / 400L)
        total += (367L * month - 362L) / 12L
        total += dayOfMonth - 1L

        if (month > 2) {
            total--
            if (!isLeapYear(year)) {
                total--
            }
        }

        return total - DAYS_0000_TO_1970
    }

    override fun toString(): String {
        return "PackedDate(year=$year, month=$month, dayOfMonth=$dayOfMonth)"
    }

    companion object {
        const val MILLIS_IN_DAY = (24 * 60 * 60000).toLong()
        const val MAX_YEAR = 65535

        const val YEAR_SHIFT = 16
        const val MONTH_SHIFT = 8

        // year: 0 month: 1 dayOfMonth: 1
        val MIN_DATE = PackedDate((0 shl YEAR_SHIFT) or (1 shl MONTH_SHIFT) or 1)

        // year: 65535 month: 12 dayOfMonth: 31
        val MAX_DATE = PackedDate((65535 shl YEAR_SHIFT) or (12 shl MONTH_SHIFT) or 31)

        const val MIN_DATE_EPOCH = -719528L
        const val MAX_DATE_EPOCH = 23217003L

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
            var zeroDay = epochDay + DAYS_0000_TO_1970 - 60L

            var adjust = 0L
            if (zeroDay < 0) {
                // adjust negative years to positive for calculation
                val adjustCycles = (zeroDay + 1) / DAYS_PER_CYCLE - 1L
                adjust = adjustCycles * 400L
                zeroDay -= adjustCycles * DAYS_PER_CYCLE
            }

            var yearEst = (400L * zeroDay + 591L) / DAYS_PER_CYCLE
            var doyEst = zeroDay - (365L * yearEst + yearEst / 4L - yearEst / 100L + yearEst / 400L)

            if (doyEst < 0) {
                yearEst--
                doyEst = zeroDay - (365L * yearEst + yearEst / 4L - yearEst / 100L + yearEst / 400L)
            }

            yearEst += adjust

            val marchDoy0 = doyEst.toInt()
            val marchMonth0 = (marchDoy0 * 5 + 2) / 153
            val month = (marchMonth0 + 2) % 12 + 1
            val dom = marchDoy0 - (marchMonth0 * 306 + 5) / 10 + 1

            var year = yearEst.toInt()
            year += marchMonth0 / 10

            return PackedDate(year, month, dom)
        }

        fun fromLocalDate(date: LocalDate): PackedDate {
            require(date.year in 0..MAX_YEAR) { "LocalDate is out of valid range" }

            return PackedDate(date.year, date.monthValue, date.dayOfMonth)
        }

        fun isValidEpochDay(epochDay: Long): Boolean {
            return epochDay in MIN_DATE_EPOCH..MAX_DATE_EPOCH
        }

        fun getDayOfWeek(epochDay: Long): Int {
            return floorMod(epochDay + 3L, 7L).toInt() + 1
        }
    }
}

internal fun PackedDateRange(start: PackedDate, end: PackedDate): PackedDateRange {
    return PackedDateRange(packInts(start.bits, end.bits))
}

@JvmInline
internal value class PackedDateRange(val bits: Long) {
    inline val start: PackedDate
        get() = PackedDate(unpackFirstInt(bits))

    inline val end: PackedDate
        get() = PackedDate(unpackSecondInt(bits))

    inline operator fun component1() = start
    inline operator fun component2() = end

    override fun toString(): String {
        return "PackedDateRange(start=$start, end=$end)"
    }

    companion object {
        fun fromLocalDates(start: LocalDate, end: LocalDate): PackedDateRange {
            return PackedDateRange(PackedDate.fromLocalDate(start), PackedDate.fromLocalDate(end))
        }

        fun fromSingleDate(date: LocalDate): PackedDateRange {
            val packed = PackedDate.fromLocalDate(date)

            return PackedDateRange(packed, packed)
        }

        fun week(year: Int, month: Int, weekIndex: Int): PackedDateRange {
            val firstDay = PackedDate(year, month, 1)
            val firstDayEpochDay = firstDay.toEpochDay()
            val dayOfWeek = PackedDate.getDayOfWeek(firstDayEpochDay)

            var startEpochDay = firstDayEpochDay - dayOfWeek + 1

            // Integer multiplication with cast to long is intentional.
            // weekIndex is always in [0; 5], so the value can't overflow.
            startEpochDay += (weekIndex * 7).toLong()

            val endEpochDay = startEpochDay + 6

            return PackedDateRange(
                start = PackedDate.fromEpochDay(startEpochDay),
                end = PackedDate.fromEpochDay(endEpochDay)
            )
        }

        fun month(year: Int, month: Int): PackedDateRange {
            val daysInMonth = TimeUtils.getDaysInMonth(year, month)

            return PackedDateRange(
                start = PackedDate(year, month, dayOfMonth = 1),
                end = PackedDate(year, month, dayOfMonth = daysInMonth)
            )
        }
    }
}