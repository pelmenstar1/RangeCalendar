package com.github.pelmenstar1.rangecalendar

import org.junit.Test
import kotlin.test.assertEquals

class TimeUtilsTests {
    @Test
    fun isLeapYearTest() {
        fun testHelper(year: Int, expected: Boolean) {
            val actual = isLeapYear(year)

            assertEquals(expected, actual, "year: $year")
        }

        testHelper(year = 2023, expected = false)
        testHelper(year = 2020, expected = true)
        testHelper(year = 2000, expected = true)
        testHelper(year = 1900, expected = false)
    }

    @Test
    fun getDaysInMonthTest() {
        fun testHelper(year: Int, month: Int, expected: Int) {
            val actual = getDaysInMonth(year, month)

            assertEquals(expected, actual)
        }

        testHelper(year = 2023, month = 1, expected = 31)
        testHelper(year = 2023, month = 2, expected = 28)
        testHelper(year = 2020, month = 2, expected = 29)
        testHelper(year = 2023, month = 3, expected = 31)
        testHelper(year = 2023, month = 4, expected = 30)
        testHelper(year = 2023, month = 5, expected = 31)
        testHelper(year = 2023, month = 6, expected = 30)
        testHelper(year = 2023, month = 7, expected = 31)
        testHelper(year = 2023, month = 8, expected = 31)
        testHelper(year = 2023, month = 9, expected = 30)
        testHelper(year = 2023, month = 10, expected = 31)
        testHelper(year = 2023, month = 11, expected = 30)
        testHelper(year = 2023, month = 12, expected = 31)
    }
}