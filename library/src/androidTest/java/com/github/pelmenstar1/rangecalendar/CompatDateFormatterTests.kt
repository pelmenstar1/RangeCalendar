package com.github.pelmenstar1.rangecalendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class CompatDateFormatterTests {
    @Test
    fun formatTest() {
        val dateFormatter = CompatDateFormatter(Locale.ENGLISH, "dd MMMM yyyy")
        val date = PackedDate(year = 2023, month = 7, dayOfMonth = 23)
        val expected = "23 July 2023"
        val actual = dateFormatter.format(date)

        assertEquals(expected, actual)
    }

    @Test
    fun patternSetterTest() {
        val dateFormatter = CompatDateFormatter(Locale.ENGLISH, "dd MMMM yyyy")
        val date = PackedDate(year = 2023, month = 7, dayOfMonth = 23)
        val expected1 = "23 July 2023"
        val actual1 = dateFormatter.format(date)

        assertEquals(expected1, actual1)

        dateFormatter.pattern = "MMMM dd yyyy"
        val expected2 = "July 23 2023"
        val actual2 = dateFormatter.format(date)

        assertEquals(expected2, actual2)
    }

    @Test
    fun localeSetterTest() {
        val dateFormatter = CompatDateFormatter(Locale.ENGLISH, "dd MMMM yyyy")
        val date = PackedDate(year = 2023, month = 7, dayOfMonth = 23)
        val expected1 = "23 July 2023"
        val actual1 = dateFormatter.format(date)

        assertEquals(expected1, actual1)

        dateFormatter.locale = Locale.FRENCH
        val expected2 = "23 juillet 2023"
        val actual2 = dateFormatter.format(date)

        assertEquals(expected2, actual2)
    }
}