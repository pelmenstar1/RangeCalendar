@file:Suppress("NOTHING_TO_INLINE")

package com.github.pelmenstar1.rangecalendar

import com.github.pelmenstar1.rangecalendar.utils.floorMod
import java.time.LocalDate
import java.util.*

internal fun PackedDate(year: Int, month: Int, dayOfMonth: Int): PackedDate {
    PackedDate.checkYear(year)
    PackedDate.checkMonth(month)
    PackedDate.checkDayOfMonth(dayOfMonth, daysInMonth = getDaysInMonth(year, month))

    return PackedDate.createUnchecked(year, month, dayOfMonth)
}

internal fun PackedDate(ym: YearMonth, dayOfMonth: Int): PackedDate {
    val year = ym.year
    val month = ym.month

    PackedDate.checkYear(year)
    PackedDate.checkDayOfMonth(dayOfMonth, daysInMonth = getDaysInMonth(year, month))

    return PackedDate.createUnchecked(year, month, dayOfMonth)
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

    val dayOfMonth: Int
        get() = bits and 0xff

    val dayOfWeek: CompatDayOfWeek
        get() = getDayOfWeek(toEpochDay())

    operator fun component1() = year
    operator fun component2() = month
    operator fun component3() = dayOfMonth

    operator fun compareTo(other: PackedDate): Int {
        var cmp = year - other.year
        if (cmp == 0) {
            cmp = month - other.month

            if (cmp == 0) {
                cmp = dayOfMonth - other.dayOfMonth
            }
        }

        return cmp
    }

    private fun withDayOfMonthUnchecked(newValue: Int): PackedDate {
        return PackedDate((bits and 0xFFFFFF00.toInt()) or newValue)
    }

    fun plusDays(days: Int): PackedDate {
        if (days == 0) {
            return this
        }

        val currentYear = year
        val currentMonth = month

        val newDay = dayOfMonth + days

        if (newDay <= 0) {
            var prevYear = currentYear
            var prevMonth = currentMonth - 1

            if (prevMonth == 0) {
                prevYear--
                checkYear(prevYear)

                prevMonth = 12
            }

            val daysInPrevMonth = getDaysInMonth(prevYear, prevMonth)
            val prevDay = newDay + daysInPrevMonth

            // Check if prevDay specifies the day in prevYear and prevMonth. If not, fallback to slow path.
            if (prevDay >= 1) {
                return createUnchecked(prevYear, prevMonth, prevDay)
            }
        } else {
            val daysInCurrentMonth = getDaysInMonth(currentYear, currentMonth)

            if (newDay <= daysInCurrentMonth) {
                // newDay still specifies the day in year and month. So change only the day.
                return withDayOfMonthUnchecked(newDay)
            } else {
                var nextYear = currentYear
                var nextMonth = currentMonth + 1

                if (nextMonth == 13) {
                    nextYear++
                    checkYear(nextYear)

                    nextMonth = 1
                }

                val daysInNextMonth = getDaysInMonth(nextYear, nextMonth)
                val nextDay = newDay - daysInCurrentMonth

                if (nextDay <= daysInNextMonth) {
                    return PackedDate(nextYear, nextMonth, nextDay)
                }
            }
        }

        return fromEpochDay(toEpochDay() + days)
    }

    fun toLocalDate(): LocalDate {
        return LocalDate.of(year, month, dayOfMonth)
    }

    fun toEpochDay(): Long {
        val (y, m, d) = this

        var total = 0L

        total += 365L * y
        total += ((y + 3L) / 4L - (y + 99L) / 100L + (y + 399L) / 400L)
        total += (367L * m - 362L) / 12L
        total += d - 1

        if (m > 2) {
            total--
            if (!isLeapYear(y)) {
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
        val MAX_DATE = PackedDate((MAX_YEAR shl YEAR_SHIFT) or (12 shl MONTH_SHIFT) or 31)

        // the date is invalid because month is 0.
        val INVALID = PackedDate(0)

        const val MIN_DATE_EPOCH = -719528L
        const val MAX_DATE_EPOCH = 23217003L

        private const val DAYS_PER_CYCLE = 146097L
        private const val DAYS_0000_TO_1970 = DAYS_PER_CYCLE * 5 - (30 * 365 + 7)

        internal fun checkYear(year: Int) {
            require(year in 0..MAX_YEAR) { "Invalid year value ($year)" }
        }

        internal fun checkMonth(month: Int) {
            require(month in 1..12) { "Invalid month value ($month)" }
        }

        internal fun checkDayOfMonth(day: Int, daysInMonth: Int) {
            require(day in 1..daysInMonth) { "Invalid day of month value ($day)" }
        }

        internal fun createUnchecked(year: Int, month: Int, dayOfMonth: Int): PackedDate {
            return PackedDate((year shl YEAR_SHIFT) or (month shl MONTH_SHIFT) or dayOfMonth)
        }

        fun today(timeZone: TimeZone = TimeZone.getDefault()): PackedDate {
            return fromEpochDay(todayEpochDay(timeZone))
        }

        fun todayEpochDay(timeZone: TimeZone = TimeZone.getDefault()): Long {
            val utcMillis = System.currentTimeMillis()
            val localMillis = utcMillis + timeZone.getOffset(utcMillis)

            return localMillis / MILLIS_IN_DAY
        }

        fun fromEpochDay(epochDay: Long): PackedDate {
            check(epochDay in MIN_DATE_EPOCH..MAX_DATE_EPOCH) { "epochDay" }

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

            // year can't be out of range because we already checked epochDay.
            // month, dom can't be out of range because of the algorithm.
            return createUnchecked(year, month, dom)
        }

        fun fromLocalDate(date: LocalDate): PackedDate {
            val year = date.year
            checkYear(year)

            return createUnchecked(year, date.monthValue, date.dayOfMonth)
        }

        fun getDayOfWeek(epochDay: Long): CompatDayOfWeek {
            val dow = floorMod(epochDay + 3L, 7L).toInt()

            return CompatDayOfWeek(dow)
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

        fun week(year: Int, month: Int, weekIndex: Int, firstDayOfWeek: CompatDayOfWeek): PackedDateRange {
            PackedDate.checkYear(year)
            PackedDate.checkMonth(month)

            val firstDay = PackedDate.createUnchecked(year, month, 1)

            val offset = weekIndex * 7 - CompatDayOfWeek.daysBetween(firstDayOfWeek, firstDay.dayOfWeek)

            val startDate = firstDay.plusDays(offset)
            val endDate = startDate.plusDays(6)

            return PackedDateRange(startDate, endDate)
        }

        fun month(year: Int, month: Int): PackedDateRange {
            PackedDate.checkYear(year)
            PackedDate.checkMonth(month)

            return PackedDateRange(
                start = PackedDate.createUnchecked(year, month, dayOfMonth = 1),
                end = PackedDate.createUnchecked(year, month, dayOfMonth = getDaysInMonth(year, month))
            )
        }
    }
}