package com.github.pelmenstar1.rangecalendar

import java.text.FieldPosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class CompatDateFormatter(locale: Locale, pattern: String) {
    private var dateFormatter: SimpleDateFormat
    private val tempDate = Date()

    private val stringBuffer = StringBuffer(32)

    var locale: Locale = locale
        set(value) {
            field = value

            dateFormatter = SimpleDateFormat(pattern, value)
        }

    var pattern = pattern
        set(value) {
            field = value

            dateFormatter.applyPattern(value)
        }

    init {
        dateFormatter = SimpleDateFormat(pattern, locale)
    }

    fun format(date: PackedDate): String {
        val millis = date.toEpochDay() * PackedDate.MILLIS_IN_DAY

        val buffer = stringBuffer.also {
            // Setting length to 0, moves cursor to the beginning but doesn't invalidate internal char array.
            it.setLength(0)
        }

        val utilDate = tempDate.also { it.time = millis }

        return dateFormatter.format(utilDate, buffer, FIELD_POS).toString()
    }

    companion object {
        private val FIELD_POS = FieldPosition(0)
    }
}