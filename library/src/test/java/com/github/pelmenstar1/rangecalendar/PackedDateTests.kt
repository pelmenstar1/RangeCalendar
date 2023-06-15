package com.github.pelmenstar1.rangecalendar

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class PackedDateTests {
    private fun forEachValidDate(block: (year: Int, month: Int, dayOfMonth: Int) -> Unit) {
        for (year in 0..PackedDate.MAX_YEAR) {
            for (month in 1..12) {
                val daysInMonth = TimeUtils.getDaysInMonth(year, month)

                for (day in 1..daysInMonth) {
                    block(year, month, day)
                }
            }
        }
    }

    @Test
    fun minMaxEpochDayTest() {
        val expectedMinEpochDay = LocalDate.of(0, 1, 1).toEpochDay()
        val expectedMaxEpochDay = LocalDate.of(65535, 12, 31).toEpochDay()

        assertEquals(expectedMinEpochDay, PackedDate.MIN_DATE_EPOCH, "min")
        assertEquals(expectedMaxEpochDay, PackedDate.MAX_DATE_EPOCH, "max")
    }

    @Test
    fun minMaxDateTest() {
        val expectedMinDate = PackedDate(0, 1, 1)
        val expectedMaxDate = PackedDate(65535, 12, 31)

        assertEquals(expectedMinDate, PackedDate.MIN_DATE, "min: expected bits: ${expectedMinDate.bits}")
        assertEquals(expectedMaxDate, PackedDate.MAX_DATE, "max: expected bits: ${expectedMaxDate.bits} ")
    }

    @Test
    fun getCreateTest() {
        forEachValidDate { expectedYear, expectedMonth, expectedDayOfMonth ->
            val packed = PackedDate(expectedYear, expectedMonth, expectedDayOfMonth)

            val actualYear = packed.year
            val actualMonth = packed.month
            val actualDayOfMonth = packed.dayOfMonth

            assertEquals(expectedYear, actualYear, "year")
            assertEquals(expectedMonth, actualMonth, "month")
            assertEquals(expectedDayOfMonth, actualDayOfMonth, "day of month")
        }
    }

    @Test
    fun toEpochDayTest() {
        forEachValidDate { year, month, dayOfMonth ->
            val expected = LocalDate.of(year, month, dayOfMonth).toEpochDay()
            val actual = PackedDate(year, month, dayOfMonth).toEpochDay()

            assertEquals(expected, actual)
        }
    }

    @Test
    fun fromEpochDayTest() {
        for(epochDay in PackedDate.MIN_DATE_EPOCH..PackedDate.MAX_DATE_EPOCH) {
            val local = LocalDate.ofEpochDay(epochDay)
            val packed = PackedDate.fromEpochDay(epochDay)

            assertEquals(local.year, packed.year)
            assertEquals(local.monthValue, packed.month)
            assertEquals(local.dayOfMonth, packed.dayOfMonth)
        }
    }

    @Test
    fun getDayOfWeekTest() {
        forEachValidDate { year, month, dayOfMonth ->
            val expectedValue = LocalDate.of(year, month, dayOfMonth).dayOfWeek.value
            val actualValue = PackedDate(year, month, dayOfMonth).dayOfWeek

            assertEquals(expectedValue, actualValue)
        }
    }
}