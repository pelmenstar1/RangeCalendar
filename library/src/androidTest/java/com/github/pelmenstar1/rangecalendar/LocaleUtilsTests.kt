package com.github.pelmenstar1.rangecalendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.pelmenstar1.rangecalendar.utils.getFirstDayOfWeek
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class LocaleUtilsTests {
    @Test
    fun getFirstDayOfWeekTest() {
        fun testHelper(locale: Locale, expectedFirstDow: CompatDayOfWeek) {
            val actualFirstDow = locale.getFirstDayOfWeek()

            assertEquals(expectedFirstDow, actualFirstDow, "locale: ${locale.toLanguageTag()}")
        }

        testHelper(Locale.FRANCE, CompatDayOfWeek.Monday)
        testHelper(Locale.CANADA, CompatDayOfWeek.Sunday)
        testHelper(Locale.US, CompatDayOfWeek.Sunday)
        testHelper(Locale.forLanguageTag("ar"), CompatDayOfWeek.Saturday)
    }
}