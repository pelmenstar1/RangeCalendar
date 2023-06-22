package com.github.pelmenstar1.rangecalendar

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.test.assertContentEquals

@RunWith(AndroidJUnit4::class)
class WeekdayDataTests {
    @Suppress("UNCHECKED_CAST")
    @Test
    fun getTest() {
        val locale = Locale.ENGLISH
        val data = WeekdayData.get(locale)

        val expectedWeekdays: Array<String> = if (Build.VERSION.SDK_INT >= 24) {
            arrayOf(
                "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun",
                "M", "T", "W", "T", "F", "S", "S",
            )
        } else {
            arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        }

        assertContentEquals(expectedWeekdays, data.weekdays as Array<String>)
    }
}