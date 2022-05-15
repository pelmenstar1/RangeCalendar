package io.github.pelmenstar1.rangecalendar

import androidx.annotation.RequiresApi
import io.github.pelmenstar1.rangecalendar.TimeUtils.currentLocalTimeMillis
import io.github.pelmenstar1.rangecalendar.TimeUtils.isLeapYear
import io.github.pelmenstar1.rangecalendar.utils.floorMod
import java.time.LocalDate
import java.util.*

internal fun PackedDate(year: Int, month: Int, dayOfMonth: Int): PackedDate {
    return PackedDate(year shl 16 or (month shl 8) or dayOfMonth)
}

internal fun PackedDate(ym: YearMonth, dayOfMonth: Int): PackedDate {
    return PackedDate(ym.year, ym.month, dayOfMonth)
}

// Some code was taken from OpenJDK
// https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/time/LocalDate.java
@JvmInline
internal value class PackedDate(val bits: Int) {
    // Packed values positions:
    // 32-16 bits - year
    // 16-8 bits - month
    // 8-0 bits - day

    val year: Int
        get() = bits shr 16

    val month: Int
        get() = (bits shr 8) and 0xff

    val dayOfMonth: Int
        get() = bits and 0xff

    val dayOfWeek: Int
        get() {
            return floorMod(toEpochDay() + 3, 7).toInt() + 1
        }

    fun toCalendar(calendar: Calendar) {
        // month in Calendar is in [0; 11], but "our" month is in [1; 12], so we need to minus 1
        calendar.set(year, month - 1, dayOfMonth)
    }

    @RequiresApi(26)
    fun toLocalDate(): LocalDate {
        return LocalDate.of(year, month, dayOfMonth)
    }

    fun toEpochDay(): Long {
        val year = year
        val month = month
        val day = dayOfMonth

        var total = 0L

        total += 365 * year.toLong()
        total += ((year + 3) / 4 - (year + 99) / 100 + (year + 399) / 400).toLong()
        total += (367 * month.toLong() - 362) / 12
        total += (day - 1).toLong()

        if (month > 2) {
            total--
            if (!isLeapYear(year)) {
                total--
            }
        }
        return total - DAYS_0000_TO_1970
    }

    companion object {
        const val MILLIS_IN_DAY = (24 * 60 * 60000).toLong()
        const val MAX_YEAR = Short.MAX_VALUE.toInt()

        private const val DAYS_PER_CYCLE = 146097
        private const val DAYS_0000_TO_1970 = DAYS_PER_CYCLE * 5 - (30 * 365 + 7)

        fun today(): PackedDate {
            return fromEpochDay(todayEpochDay())
        }

        fun todayEpochDay(): Long {
            return currentLocalTimeMillis() / MILLIS_IN_DAY
        }

        fun fromEpochDay(epochDay: Long): PackedDate {
            var zeroDay = epochDay + DAYS_0000_TO_1970
            zeroDay -= 60

            var yearEst = (400 * zeroDay + 591) / DAYS_PER_CYCLE
            var doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400)

            if (doyEst < 0) {
                yearEst--
                doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400)
            }

            val marchDoy0 = doyEst.toInt()
            val marchMonth0 = (marchDoy0 * 5 + 2) / 153
            val month = (marchMonth0 + 2) % 12 + 1
            val dom = marchDoy0 - (marchMonth0 * 306 + 5) / 10 + 1
            yearEst += (marchMonth0 / 10).toLong()

            return PackedDate(yearEst.toInt(), month, dom)
        }

        @RequiresApi(26)
        fun fromLocalDate(date: LocalDate): PackedDate {
            return PackedDate(date.year, date.monthValue, date.dayOfMonth)
        }

        fun fromCalendar(calendar: Calendar): PackedDate {
            // month in Calendar is in [0; 11], but "our" month is in [1; 12], so we need to plus 1
            return PackedDate(
                calendar[Calendar.YEAR],
                calendar[Calendar.MONTH] + 1,
                calendar[Calendar.DAY_OF_MONTH]
            )
        }
    }
}