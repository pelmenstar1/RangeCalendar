package com.github.pelmenstar1.rangecalendar

import android.icu.text.DisplayContext
import android.os.Build
import java.text.FieldPosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class CompatDateFormatter(locale: Locale, pattern: String) {
    // If API level >= 24, the type is android.icu.text.SimpleDateFormat
    // Otherwise, java.text.SimpleDateFormat
    private var dateFormatter: Any

    // If API level >= 24, the type is
    // Otherwise, java.util.Date
    private val tempDateOrCalendar: Any

    private val stringBuffer = StringBuffer(32)

    var locale: Locale = locale
        set(value) {
            field = value

            dateFormatter = createFormatter(pattern, value)
        }

    var pattern = pattern
        set(value) {
            field = value

            if (Build.VERSION.SDK_INT >= 24) {
                (dateFormatter as android.icu.text.SimpleDateFormat).applyPattern(value)
            } else {
                (dateFormatter as SimpleDateFormat).applyPattern(value)
            }
        }

    init {
        dateFormatter = createFormatter(pattern, locale)

        tempDateOrCalendar = if (Build.VERSION.SDK_INT >= 24) {
            android.icu.util.Calendar.getInstance()
        } else {
            Date()
        }
    }

    private fun createFormatter(pattern: String, locale: Locale): Any {
        return if (Build.VERSION.SDK_INT >= 24) {
            android.icu.text.SimpleDateFormat(pattern, locale).apply {
                // CompatDateFormatter is only used in "standalone" context.
                // This is basically needed for standalone months because on some
                // devices the capitalization is wrong.
                setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE)
            }
        } else {
            SimpleDateFormat(pattern, locale)
        }
    }

    fun format(date: PackedDate): String {
        val millis = date.toEpochDay() * PackedDate.MILLIS_IN_DAY

        val buffer = stringBuffer.also {
            // Setting length to 0, moves cursor to the beginning but doesn't invalidate internal char array.
            it.setLength(0)
        }

        if (Build.VERSION.SDK_INT >= 24) {
            val calendar = tempDateOrCalendar as android.icu.util.Calendar
            calendar.timeInMillis = millis

            (dateFormatter as android.icu.text.SimpleDateFormat).format(calendar, buffer, FIELD_POS)
        } else {
            val utilDate = tempDateOrCalendar as Date
            utilDate.time = millis

            (dateFormatter as SimpleDateFormat).format(utilDate, buffer, FIELD_POS)
        }

        return buffer.toString()
    }

    companion object {
        private val FIELD_POS = FieldPosition(0)
    }
}