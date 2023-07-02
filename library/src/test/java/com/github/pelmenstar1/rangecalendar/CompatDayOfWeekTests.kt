package com.github.pelmenstar1.rangecalendar

import org.junit.Test
import java.time.DayOfWeek
import java.util.Calendar
import kotlin.test.assertEquals

class CompatDayOfWeekTests {
    @Test
    fun daysBetweenTest() {
        fun testHelper(start: CompatDayOfWeek, end: CompatDayOfWeek, expected: Int) {
            val actual = CompatDayOfWeek.daysBetween(start, end)

            assertEquals(expected, actual, "start: $start, end: $end")
        }

        testHelper(CompatDayOfWeek.Monday, CompatDayOfWeek.Tuesday, expected = 1)
        testHelper(CompatDayOfWeek.Monday, CompatDayOfWeek.Wednesday, expected = 2)
        testHelper(CompatDayOfWeek.Sunday, CompatDayOfWeek.Tuesday, expected = 2)
        testHelper(CompatDayOfWeek.Sunday, CompatDayOfWeek.Monday, expected = 1)
        testHelper(CompatDayOfWeek.Friday, CompatDayOfWeek.Monday, expected = 3)
    }

    @Test
    fun fromCalendarDayOfWeekTest() {
        fun testHelper(calendarDow: Int, expected: CompatDayOfWeek) {
            val actual = CompatDayOfWeek.fromCalendarDayOfWeek(calendarDow)

            assertEquals(expected, actual, "calendarDow: $calendarDow")
        }

        testHelper(Calendar.MONDAY, CompatDayOfWeek.Monday)
        testHelper(Calendar.TUESDAY, CompatDayOfWeek.Tuesday)
        testHelper(Calendar.WEDNESDAY, CompatDayOfWeek.Wednesday)
        testHelper(Calendar.THURSDAY, CompatDayOfWeek.Thursday)
        testHelper(Calendar.FRIDAY, CompatDayOfWeek.Friday)
        testHelper(Calendar.SATURDAY, CompatDayOfWeek.Saturday)
        testHelper(Calendar.SUNDAY, CompatDayOfWeek.Sunday)
    }

    @Test
    fun toCalendarValueTest() {
        fun testHelper(dow: CompatDayOfWeek, expectedDow: Int) {
            val actualDow = dow.toCalendarValue()

            assertEquals(expectedDow, actualDow, "dow: $dow")
        }

        testHelper(CompatDayOfWeek.Monday, Calendar.MONDAY)
        testHelper(CompatDayOfWeek.Tuesday, Calendar.TUESDAY)
        testHelper(CompatDayOfWeek.Wednesday, Calendar.WEDNESDAY)
        testHelper(CompatDayOfWeek.Thursday, Calendar.THURSDAY)
        testHelper(CompatDayOfWeek.Friday, Calendar.FRIDAY)
        testHelper(CompatDayOfWeek.Saturday, Calendar.SATURDAY)
        testHelper(CompatDayOfWeek.Sunday, Calendar.SUNDAY)
    }

    @Test
    fun fromEnumValueTest() {
        fun testHelper(enumValue: DayOfWeek, expected: CompatDayOfWeek) {
            val actual = CompatDayOfWeek.fromEnumValue(enumValue)

            assertEquals(expected, actual, "enumValue: $enumValue")
        }

        testHelper(DayOfWeek.MONDAY, CompatDayOfWeek.Monday)
        testHelper(DayOfWeek.THURSDAY, CompatDayOfWeek.Thursday)
        testHelper(DayOfWeek.WEDNESDAY, CompatDayOfWeek.Wednesday)
        testHelper(DayOfWeek.THURSDAY, CompatDayOfWeek.Thursday)
        testHelper(DayOfWeek.FRIDAY, CompatDayOfWeek.Friday)
        testHelper(DayOfWeek.SATURDAY, CompatDayOfWeek.Saturday)
        testHelper(DayOfWeek.SUNDAY, CompatDayOfWeek.Sunday)
    }

    @Test
    fun toEnumValueTest() {
        fun testHelper(dow: CompatDayOfWeek, expected: DayOfWeek) {
            val actual = dow.toEnumValue()

            assertEquals(expected, actual, "dow: $dow")
        }

        testHelper(CompatDayOfWeek.Monday, DayOfWeek.MONDAY)
        testHelper(CompatDayOfWeek.Thursday, DayOfWeek.THURSDAY)
        testHelper(CompatDayOfWeek.Wednesday, DayOfWeek.WEDNESDAY)
        testHelper(CompatDayOfWeek.Thursday, DayOfWeek.THURSDAY)
        testHelper(CompatDayOfWeek.Friday, DayOfWeek.FRIDAY)
        testHelper(CompatDayOfWeek.Saturday, DayOfWeek.SATURDAY)
        testHelper(CompatDayOfWeek.Sunday, DayOfWeek.SUNDAY)
    }
}