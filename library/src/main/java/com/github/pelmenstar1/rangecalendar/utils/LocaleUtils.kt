package com.github.pelmenstar1.rangecalendar.utils

import android.content.Context
import android.os.Build
import java.util.*

internal fun Context.getLocaleCompat(): Locale {
    val config = resources.configuration

    return if (Build.VERSION.SDK_INT >= 24) {
        config.locales[0]
    } else {
        @Suppress("DEPRECATION")
        config.locale
    }
}