package com.github.pelmenstar1.rangecalendar

import org.junit.Test
import kotlin.test.assertEquals

class PackedDateRangeTests {
    @Test
    fun weekRangeTest() {
        fun testCase(year: Int, month: Int, weekIndex: Int, expectedRange: PackedDateRange) {
            val actualRange = PackedDateRange.week(year, month, weekIndex)

            assertEquals(
                expectedRange,
                actualRange,
                "year: $year month: $month weekIndex: $weekIndex"
            )
        }

        testCase(
            year = 2023, month = 6, weekIndex = 0,
            expectedRange = PackedDateRange(
                start = PackedDate(year = 2023, month = 5, dayOfMonth = 29),
                end = PackedDate(year = 2023, month = 6, dayOfMonth = 4),
            )
        )

        testCase(
            year = 2023, month = 6, weekIndex = 1,
            expectedRange = PackedDateRange(
                start = PackedDate(year = 2023, month = 6, dayOfMonth = 5),
                end = PackedDate(year = 2023, month = 6, dayOfMonth = 11)
            )
        )

        testCase(
            year = 2023, month = 6, weekIndex = 5,
            expectedRange = PackedDateRange(
                start = PackedDate(year = 2023, month = 7, dayOfMonth = 3),
                end = PackedDate(year = 2023, month = 7, dayOfMonth = 9)
            )
        )
    }
}