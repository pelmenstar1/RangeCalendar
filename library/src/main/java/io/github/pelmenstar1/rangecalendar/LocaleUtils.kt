package io.github.pelmenstar1.rangecalendar

import android.content.Context
import android.os.Build
import java.util.*

fun Context.getLocaleCompat(): Locale {
    val config = resources.configuration

    return if (Build.VERSION.SDK_INT >= 24) {
        config.locales[0]
    } else {
        @Suppress("DEPRECATION")
        config.locale
    }
}