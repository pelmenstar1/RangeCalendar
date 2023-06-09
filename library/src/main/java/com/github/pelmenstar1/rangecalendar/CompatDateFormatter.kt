package com.github.pelmenstar1.rangecalendar

import android.content.Context
import android.os.Build
import com.github.pelmenstar1.rangecalendar.utils.getLocaleCompat
import java.text.FieldPosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class CompatDateFormatter(context: Context, private val pattern: String) {
    // If API >= 24, the type should be android.icu.text.SimpleDateFormat,
    // otherwise java.text.SimpleDateFormat.
    private var dateFormatter: Any? = null

    private val calendarOrDate: Any = if (Build.VERSION.SDK_INT >= 24) {
        android.icu.util.Calendar.getInstance()
    } else {
        Date()
    }

    private val stringBuffer = StringBuffer(32)

    init {
        refreshFormatter(context.getLocaleCompat())
    }

    private fun refreshFormatter(currentLocale: Locale) {
        // Find the best format if we can
        val bestFormat = if (Build.VERSION.SDK_INT >= 18) {
            android.text.format.DateFormat.getBestDateTimePattern(currentLocale, pattern)
        } else {
            pattern
        }

        dateFormatter = if (Build.VERSION.SDK_INT >= 24) {
            android.icu.text.SimpleDateFormat(bestFormat, currentLocale)
        } else {
            SimpleDateFormat(bestFormat, currentLocale)
        }
    }

    fun onLocaleChanged(newLocale: Locale) {
        refreshFormatter(newLocale)
    }

    fun format(date: PackedDate): String {
        val millis = date.toEpochDay() * PackedDate.MILLIS_IN_DAY

        val buffer = stringBuffer
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