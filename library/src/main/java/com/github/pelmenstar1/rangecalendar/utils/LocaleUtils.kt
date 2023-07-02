package com.github.pelmenstar1.rangecalendar.utils

import android.content.Context
import android.content.res.Configuration
import android.icu.util.ULocale
import android.os.Build
import android.text.format.DateFormat
import android.util.Log
import com.github.pelmenstar1.rangecalendar.CompatDayOfWeek
import java.util.*

internal fun Configuration.getLocaleCompat(): Locale {
    return if (Build.VERSION.SDK_INT >= 24) {
        locales[0]
    } else {
        @Suppress("DEPRECATION")
        locale
    }
}

internal fun Context.getLocaleCompat(): Locale {
    return resources.configuration.getLocaleCompat()
}

internal fun getBestDatePatternCompat(locale: Locale, pattern: String): String {
    return if (Build.VERSION.SDK_INT >= 18) {
        DateFormat.getBestDateTimePattern(locale, pattern)
    } else {
        pattern
    }
}

internal fun Locale.getFirstDayOfWeek(): CompatDayOfWeek {
    val dow = Calendar.getInstance(this).firstDayOfWeek

    return CompatDayOfWeek.fromCalendarDayOfWeek(dow)
}