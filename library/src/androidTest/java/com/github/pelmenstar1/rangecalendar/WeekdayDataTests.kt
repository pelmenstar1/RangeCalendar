package com.github.pelmenstar1.rangecalendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.test.assertContentEquals

@RunWith(AndroidJUnit4::class)
class WeekdayDataTests {
    @Suppress("UNCHECKED_CAST")
    private fun assertContentEquals(expected: Array<out String>?, actual: Array<out String>?) {
        // Pass null message to make Kotlin use the right method.
        assertContentEquals(expected as Array<String>?, actual as Array<String>?, message = null)
    }

    @Test
    fun getTest() {
        val data = WeekdayData(Locale.ENGLISH)

        val expectedShortWeekdays = arrayOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
        val expectedNarrowWeekdays = arrayOf("M", "T", "W", "T", "F", "S", "S")

        assertContentEquals(expectedShortWeekdays, data.getWeekdays(WeekdayType.SHORT))
        assertContentEquals(expectedNarrowWeekdays, data.getWeekdays(WeekdayType.NARROW))
    }

    @Test
    fun extractNarrowWeekdaysThroughFormatterTest() {
        val data = WeekdayData(Locale.ENGLISH)

        val expectedNarrowWeekdays = arrayOf("M", "T", "W", "T", "F", "S", "S")
        val actualNarrowWeekdays = data.extractNarrowWeekdaysThroughFormatter()

        assertContentEquals(expectedNarrowWeekdays, actualNarrowWeekdays)
    }
}