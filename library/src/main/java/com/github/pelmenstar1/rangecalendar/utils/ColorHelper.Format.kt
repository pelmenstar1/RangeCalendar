package com.github.pelmenstar1.rangecalendar.utils

import androidx.annotation.ColorInt

internal fun StringBuilder.appendColor(@ColorInt color: Int) {
    append('#')
    append(String.format("%08X", color))
}

internal fun StringBuilder.appendColors(colors: IntArray, originAlphas: ByteArray?) {
    append('[')

    val size = colors.size
    for (i in 0 until size) {
        var color = colors[i]

        if (originAlphas != null) {
            color = color.withAlpha(originAlphas[i])
        }

        appendColor(color)

        if (i < size - 1) {
            append(", ")
        }
    }

    append(']')
}