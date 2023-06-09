package com.github.pelmenstar1.rangecalendar

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.util.SimpleTimeZone
import java.util.TimeZone
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class RangeCalendarConfigObserverTests {
    class TestActivity : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            setContentView(View(this))
        }
    }

    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testTimeZone = SimpleTimeZone(123, "TestTZ")

    private fun createTimeZoneChangedIntent(): Intent {
        return Intent(Intent.ACTION_TIMEZONE_CHANGED).apply {
            if (Build.VERSION.SDK_INT >= 30) {
                putExtra(Intent.EXTRA_TIMEZONE, testTimeZone)
            }
        }
    }

    private fun createDateChangedIntent(): Intent {
        return Intent(Intent.ACTION_DATE_CHANGED)
    }

    private fun useCalendarView(block: (RangeCalendarView) -> Unit) {
        ActivityScenario.launch(TestActivity::class.java).onActivity {
            block(RangeCalendarView(it))
        }
    }

    // Unfortunately, we can't change current time zone programmatically. So we can't directly test
    // the handling of Intent.ACTION_TIMEZONE_CHANGED event. To workaround, the intent sent by the system
    // is emulated and the method that handles these intents (RangeCalendarConfigObserver.onReceiveBroadcast) is called.
    @Test
    fun timeZoneChangedTest() = useCalendarView { calendar ->
        val oldTz = TimeZone.getDefault()
        TimeZone.setDefault(testTimeZone)

        try {
            RangeCalendarConfigObserver(calendar).onBroadcastReceived(createTimeZoneChangedIntent())
            assertEquals(testTimeZone, calendar.timeZone)
        } finally {
            TimeZone.setDefault(oldTz)
        }
    }

    @Test
    fun disableTimeZoneChangeNotificationsTest() = useCalendarView { calendar ->
        val observer = RangeCalendarConfigObserver(calendar).apply {
            observeTimeZoneChanges = false
        }

        val oldTz = calendar.timeZone
        observer.onBroadcastReceived(createTimeZoneChangedIntent())

        // Assure that timeZone wasn't changed.
        assertEquals(oldTz, calendar.timeZone)
    }

    // Unfortunately, we can't change current time zone programmatically. So we can't directly test
    // the handling of Intent.ACTION_DATE_CHANGED event. To check that the logic actually calls
    // RangeCalendarView.notifyTodayChanged(), we firstly set the today's date to a fake one, then
    // emulate the broadcast intent and check if the today's date is actually today.
    @Test
    fun dateChangedTest() = useCalendarView { calendar ->
        val testDate = PackedDate(year = 1, month = 1, dayOfMonth = 1)
        val expectedDate = PackedDate.today()

        calendar.adapter.setToday(testDate)
        RangeCalendarConfigObserver(calendar).onBroadcastReceived(createDateChangedIntent())

        assertEquals(expectedDate, calendar.adapter.today)
    }

    @Test
    fun disableDateChangeNotificationsTest() = useCalendarView { calendar ->
        val observer = RangeCalendarConfigObserver(calendar).apply {
            observeDateChanges = false
        }

        val testDate = PackedDate(year = 1, month = 1, dayOfMonth = 1)
        calendar.adapter.setToday(testDate)

        observer.onBroadcastReceived(createDateChangedIntent())

        // We disabled date change notifications, so today's date should remain the same
        assertEquals(testDate, calendar.adapter.today)
    }
}