package com.github.pelmenstar1.rangecalendar

import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import com.github.pelmenstar1.rangecalendar.utils.getLocaleCompat
import java.util.Calendar

internal class DefaultRangeCalendarCellAccessibilityInfoProvider(context: Context) : RangeCalendarCellAccessibilityInfoProvider {
    private val tempCalendar = Calendar.getInstance()
    private val dateFormat = if (Build.VERSION.SDK_INT >= 18) {
        DateFormat.getBestDateTimePattern(context.getLocaleCompat(), RAW_DATE_FORMAT)
    } else {
        RAW_DATE_FORMAT
    }

    override fun getContentDescription(year: Int, month: Int, dayOfMonth: Int): CharSequence {
        // In Calendar, month is 0-based.
        tempCalendar.set(year, month - 1, dayOfMonth)

        return DateFormat.format(dateFormat, tempCalendar)
    }

    companion object {
        private const val RAW_DATE_FORMAT = "dd MMMM yyyy"
    }
}