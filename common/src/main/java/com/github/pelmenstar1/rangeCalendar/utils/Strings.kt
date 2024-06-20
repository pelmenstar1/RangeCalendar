package com.github.pelmenstar1.rangecalendar.utils

fun StringBuilder.appendFourDigits(value: Int) {
    when {
        value < 10 -> {
            append("000")
            append(value)
        }
        value < 100 -> {
            append("00")
            append(value)
        }
        value < 1000 -> {
            append('0')
            append(value)
        }
        else -> {
            append(value)
        }
    }
}

fun StringBuilder.appendTwoDigits(value: Int) {
    if (value < 10) {
        append('0')
        append(value)
    } else {
        append(value)
    }
}