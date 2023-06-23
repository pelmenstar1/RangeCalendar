package com.github.pelmenstar1.rangecalendar

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.test.assertContentEquals
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
class WeekdayDataTests {
    @Suppress("UNCHECKED_CAST")
    private fun assertContentEquals(expected: Array<out String>?, actual: Array<out String>?) {
        // Pass null message to make Kotlin use the right method.
        assertContentEquals(expected as Array<String>?, actual as Array<String>?, message = null)
    }

    @Test
    fun getTest() {
        val data = WeekdayData.get(Locale.ENGLISH)

        val expectedShortWeekdays = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val expectedNarrowWeekdays = if (Build.VERSION.SDK_INT >= 24) {
            arrayOf( "M", "T", "W", "T", "F", "S", "S")
        } else {
            null
        }

        assertContentEquals(expectedShortWeekdays, data.shortWeekdays)
        assertContentEquals(expectedNarrowWeekdays, data.narrowWeekdays)
    }

    @Test
    fun getWeekdaysTest() {
        val data = WeekdayData.get(Locale.ENGLISH)

        val actualShortWeekdays = data.getWeekdays(WeekdayType.SHORT)
        assertSame(data.shortWeekdays, actualShortWeekdays)

        if (Build.VERSION.SDK_INT >= 24) {
            val actualNarrowWeekdays = data.getWeekdays(WeekdayType.NARROW)

            assertSame(data.narrowWeekdays, actualNarrowWeekdays)
        }
    }
}