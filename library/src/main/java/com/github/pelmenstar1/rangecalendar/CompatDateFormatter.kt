package com.github.pelmenstar1.rangecalendar

import android.content.Context
import android.os.Build
import com.github.pelmenstar1.rangecalendar.utils.getLocaleCompat
import java.text.FieldPosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class CompatDateFormatter(context: Context, initialPattern: String) {
    // If API level >= 24, the type is android.icu.text.SimpleDateFormat,
    // otherwise java.text.SimpleDateFormat.
    private var dateFormatter: Any? = null

    // On API levels >= 24, android.icu.text.SimpleDateFormat is used whose format method accepts android.icu.util.Calendar
    // and uses it without any transformations. Meanwhile, on older API levels, java.text.SimpleDateFormat is used whose
    // format method accepts java.util.Date and uses it without any transformations.
    private val calendarOrDate: Any = if (Build.VERSION.SDK_INT >= 24) {
        android.icu.util.Calendar.getInstance()
    } else {
        Date()
    }

    private val stringBuffer = StringBuffer(32)

    var locale: Locale = context.getLocaleCompat()
        set(value) {
            field = value

            refreshFormatter()
        }

    var pattern = initialPattern
        set(value) {
            field = value

            refreshFormatter()
        }

    init {
        refreshFormatter()
    }

    private fun refreshFormatter() {
        dateFormatter = if (Build.VERSION.SDK_INT >= 24) {
            android.icu.text.SimpleDateFormat(pattern, locale)
        } else {
            SimpleDateFormat(pattern, locale)
        }
    }

    fun format(date: PackedDate): String {
        val millis = date.toEpochDay() * PackedDate.MILLIS_IN_DAY

        val buffer = stringBuffer

        // Setting length to 0, moves cursor to the beginning but doesn't invalidate internal char array.
        buffer.setLength(0)

        // Use the appropriate formatter
        if (Build.VERSION.SDK_INT >= 24) {
            val formatter = dateFormatter as android.icu.text.SimpleDateFormat
            val calendar = calendarOrDate as android.icu.util.Calendar

            calendar.timeInMillis = millis

            formatter.format(calendar, buffer, FIELD_POS)
        } else {
            val formatter = dateFormatter as SimpleDateFormat
            val javaUtilDate = calendarOrDate as Date

            javaUtilDate.time = millis

            formatter.format(javaUtilDate, buffer, FIELD_POS)
        }

        return buffer.toString()
    }

    companion object {
        private val FIELD_POS = FieldPosition(0)
    }
}