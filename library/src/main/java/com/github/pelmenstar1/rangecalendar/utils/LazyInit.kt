package com.github.pelmenstar1.rangecalendar.utils

internal inline fun <T> getLazyValue(value: T?, create: () -> T, set: (T) -> Unit): T {
    return if (value == null) {
        val newValue = create()
        set(newValue)

        newValue
    } else {
        value
    }
}