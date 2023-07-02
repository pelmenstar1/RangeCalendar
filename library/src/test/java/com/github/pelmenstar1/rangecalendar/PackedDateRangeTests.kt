package com.github.pelmenstar1.rangecalendar

import org.junit.Test
import kotlin.test.assertEquals

class PackedDateRangeTests {
    @Test
    fun weekRangeTest() {
        fun testCase(year: Int, month: Int, weekIndex: Int, firstDayOfWeek: CompatDayOfWeek, expectedRange: PackedDateRange) {
            val actualRange = PackedDateRange.week(year, month, weekIndex, firstDayOfWeek)

            assertEquals(
                expectedRange,
                actualRange,
                "year: $year month: $month weekIndex: $weekIndex"
            )
        }

        // First day of week - Monday

        testCase(
            year = 2023, month = 6, weekIndex = 0,
            firstDayOfWeek = CompatDayOfWeek.Monday,
            expectedRange = PackedDateRange(
                start = PackedDate(year = 2023, month = 5, dayOfMonth = 29),
                end = PackedDate(year = 2023, month = 6, dayOfMonth = 4),
            )
        )

        testCase(
            year = 2023, month = 6, weekIndex = 1,
            firstDayOfWeek = CompatDayOfWeek.Monday,
            expectedRange = PackedDateRange(
                start = PackedDate(year = 2023, month = 6, dayOfMonth = 5),
                end = PackedDate(year = 2023, month = 6, dayOfMonth = 11)
            )
        )

        testCase(
            year = 2023, month = 6, weekIndex = 5,
            firstDayOfWeek = CompatDayOfWeek.Monday,
            expectedRange = PackedDateRange(
                start = PackedDate(year = 2023, month = 7, dayOfMonth = 3),
                end = PackedDate(year = 2023, month = 7, dayOfMonth = 9)
            )
        )

        // First day of week - Sunday

        testCase(
            year = 2023, month = 6, weekIndex = 0,
            firstDayOfWeek = CompatDayOfWeek.Sunday,
            expectedRange = PackedDateRange(
                start = PackedDate(year = 2023, month = 5, dayOfMonth = 28),
                end = PackedDate(year = 2023, month = 6, dayOfMonth = 3),
            )
        )

        testCase(
            year = 2023, month = 6, weekIndex = 1,
            firstDayOfWeek = CompatDayOfWeek.Sunday,
            expectedRange = PackedDateRange(
                start = PackedDate(year = 2023, month = 6, dayOfMonth = 4),
                end = PackedDate(year = 2023, month = 6, dayOfMonth = 10)
            )
        )

        testCase(
            year = 2023, month = 6, weekIndex = 5,
            firstDayOfWeek = CompatDayOfWeek.Sunday,
            expectedRange = PackedDateRange(
                start = PackedDate(year = 2023, month = 7, dayOfMonth = 2),
                end = PackedDate(year = 2023, month = 7, dayOfMonth = 8)
            )
        )
    }
}