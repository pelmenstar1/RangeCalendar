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
        val data = WeekdayData(Locale.ENGLISH)

        val expectedShortWeekdays = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        assertContentEquals(expectedShortWeekdays, data.getWeekdays(WeekdayType.SHORT))

        if (Build.VERSION.SDK_INT >= 24) {
            val expectedNarrowWeekdays = arrayOf( "M", "T", "W", "T", "F", "S", "S")

            assertContentEquals(expectedNarrowWeekdays, data.getWeekdays(WeekdayType.NARROW))
        }
    }
}