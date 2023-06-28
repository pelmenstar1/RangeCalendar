package com.github.pelmenstar1.rangecalendar

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.pelmenstar1.rangecalendar.utils.getLocaleCompat
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class RangeCalendarViewTests {
    open class TestInfoFormatter(
        private val expectedYear: Int,
        private val expectedMonth: Int,
        private val returnValue: String
    ) : RangeCalendarView.InfoFormatter {
        var formatCallCount = 0

        override fun format(year: Int, month: Int): CharSequence {
            assertEquals(expectedYear, year, "year")
            assertEquals(expectedMonth, month, "month")
            formatCallCount++

            return returnValue
        }
    }

    class TestLocalizedInfoFormatter(
        expectedYear: Int,
        expectedMonth: Int,
        private val expectedLocale: Locale,
        returnValue: String
    ) : TestInfoFormatter(expectedYear, expectedMonth, returnValue),
        RangeCalendarView.LocalizedInfoFormatter {
        var isOnLocaleChangedCalled = false

        override fun onLocaleChanged(newLocale: Locale) {
            assertEquals(expectedLocale, newLocale)
            isOnLocaleChangedCalled = true
        }
    }

    class TestActivity : AppCompatActivity() {
        lateinit var rangeCalendar: RangeCalendarView

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            rangeCalendar = RangeCalendarView(this)
        }
    }

    private fun launchActivity(block: (RangeCalendarView) -> Unit) {
        ActivityScenario.launch(TestActivity::class.java).onActivity { block(it.rangeCalendar) }
    }

    private fun getAnyLocaleExcept(excludedLocale: Locale): Locale {
        return Locale.getAvailableLocales().find { it != excludedLocale }!!
    }

    private fun RangeCalendarView.getDefaultFormatterPattern(): String {
        return (infoFormatter as RangeCalendarView.DefaultLocalizedInfoFormatter).dateFormatter.pattern
    }

    @Test
    fun infoFormatterWithSimpleFormatterTest() = launchActivity { rangeCalendar ->
        // The selected calendar page should be the one with today date.
        val (year, month) = YearMonth.forDate(PackedDate.today())

        val expectedText = "Test"
        val formatter = TestInfoFormatter(year, month, expectedText)

        // Setting custom infoFormatter should trigger re-formatting year and month
        rangeCalendar.infoFormatter = formatter

        val actualText = rangeCalendar.infoView.text.toString()

        assertEquals(expectedText, actualText)
        assertEquals(1, formatter.formatCallCount)
    }

    @Test
    fun localizedInfoFormatterTest() = launchActivity { rangeCalendar ->
        // The selected calendar page should be the one with today date.
        val (year, month) = YearMonth.forDate(PackedDate.today())

        val currentConfig = rangeCalendar.resources.configuration
        val newLocale = getAnyLocaleExcept(currentConfig.getLocaleCompat())

        val expectedText = "Test"
        val formatter = TestLocalizedInfoFormatter(year, month, newLocale, expectedText)

        rangeCalendar.infoFormatter = formatter

        val actualText = rangeCalendar.infoView.text.toString()

        assertEquals(expectedText, actualText)
        assertEquals(1, formatter.formatCallCount)

        val newConfig = Configuration(currentConfig).apply {
            setLocale(newLocale)
        }

        rangeCalendar.dispatchConfigurationChanged(newConfig)

        // Check whether format() was called again and onLocaleChanged() was called
        assertEquals(2, formatter.formatCallCount)
        assertTrue(formatter.isOnLocaleChangedCalled)
    }

    @Test
    fun useBestPatternForInfoPatternTest() {
        if (Build.VERSION.SDK_INT < 18) {
            return
        }

        launchActivity { rangeCalendar ->
            val originPattern = "MMMM y"
            rangeCalendar.infoPattern = originPattern

            var internalFormatterPattern = rangeCalendar.getDefaultFormatterPattern()
            assertEquals(rangeCalendar.infoPattern, internalFormatterPattern)

            if (originPattern == rangeCalendar.infoPattern) {
                Log.w(TAG, "Origin pattern and best-format pattern are the same. Nothing to test")
                return@launchActivity
            }

            // When we disable best-format, we should get originPattern
            rangeCalendar.useBestPatternForInfoPattern = false

            assertEquals(originPattern, rangeCalendar.infoPattern)

            internalFormatterPattern = rangeCalendar.getDefaultFormatterPattern()
            assertEquals(originPattern, internalFormatterPattern)
        }
    }
    companion object {
        private const val TAG = "RangeCalendarViewTests"
    }
}