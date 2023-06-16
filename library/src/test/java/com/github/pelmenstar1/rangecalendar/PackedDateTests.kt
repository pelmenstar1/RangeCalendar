package com.github.pelmenstar1.rangecalendar

import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class PackedDateTests {
    private fun forEachValidDate(block: (year: Int, month: Int, dayOfMonth: Int) -> Unit) {
        for (year in 0..PackedDate.MAX_YEAR) {
            for (month in 1..12) {
                val daysInMonth = getDaysInMonth(year, month)

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
        var expectedEpochDay = PackedDate.MIN_DATE_EPOCH

        forEachValidDate { year, month, dayOfMonth ->
            val actual = PackedDate(year, month, dayOfMonth).toEpochDay()

            assertEquals(expectedEpochDay, actual)

            expectedEpochDay++
        }
    }

    @Test
    fun fromEpochDayTest() {
        var currentYear = 0
        var currentMonth = 1
        var currentDayOfMonth = 1

        for(epochDay in PackedDate.MIN_DATE_EPOCH..PackedDate.MAX_DATE_EPOCH) {
            val packed = PackedDate.fromEpochDay(epochDay)

            assertEquals(currentYear, packed.year)
            assertEquals(currentMonth, packed.month)
            assertEquals(currentDayOfMonth, packed.dayOfMonth)

            currentDayOfMonth++

            if (currentDayOfMonth > getDaysInMonth(currentYear, currentMonth)) {
                currentDayOfMonth = 1
                currentMonth++
            }

            if (currentMonth > 12) {
                currentYear++
                currentMonth = 1
            }
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

    @Test
    fun plusDaysTest() {
        fun testCase(anchorDate: PackedDate, days: Int, expectedDate: PackedDate) {
            val actualDate = anchorDate.plusDays(days)

            assertEquals(expectedDate, actualDate, "days: $days")
        }

        testCase(
            anchorDate = PackedDate(year = 2023, month = 6, dayOfMonth = 16),
            days = 0,
            expectedDate = PackedDate(year = 2023, month = 6, dayOfMonth = 16)
        )

        testCase(
            anchorDate = PackedDate(year = 2023, month = 6, dayOfMonth = 16),
            days = 1,
            expectedDate = PackedDate(year = 2023, month = 6, dayOfMonth = 17)
        )

        testCase(
            anchorDate = PackedDate(year = 2023, month = 6, dayOfMonth = 16),
            days = -15,
            expectedDate = PackedDate(year = 2023, month = 6, dayOfMonth = 1)
        )

        testCase(
            anchorDate = PackedDate(year = 2023, month = 6, dayOfMonth = 16),
            days = -16,
            expectedDate = PackedDate(year = 2023, month = 5, dayOfMonth = 31)
        )

        testCase(
            anchorDate = PackedDate(year = 2023, month = 6, dayOfMonth = 16),
            days = -18,
            expectedDate = PackedDate(year = 2023, month = 5, dayOfMonth = 29)
        )

        testCase(
            anchorDate = PackedDate(year = 2023, month = 6, dayOfMonth = 16),
            days = 14,
            expectedDate = PackedDate(year = 2023, month = 6, dayOfMonth = 30)
        )

        testCase(
            anchorDate = PackedDate(year = 2023, month = 6, dayOfMonth = 16),
            days = 15,
            expectedDate = PackedDate(year = 2023, month = 7, dayOfMonth = 1)
        )

        testCase(
            anchorDate = PackedDate(year = 2023, month = 6, dayOfMonth = 16),
            days = 17,
            expectedDate = PackedDate(year = 2023, month = 7, dayOfMonth = 3)
        )

        testCase(
            anchorDate = PackedDate(year = 2023, month = 6, dayOfMonth = 16),
            days = 46,
            expectedDate = PackedDate(year = 2023, month = 8, dayOfMonth = 1)
        )
    }
}