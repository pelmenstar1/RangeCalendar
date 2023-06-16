package com.github.pelmenstar1.rangecalendar

internal fun getDaysInMonth(ym: YearMonth): Int {
    return getDaysInMonth(ym.year, ym.month)
}

internal fun getDaysInMonth(year: Int, month: Int): Int {
    return when (month) {
        2 -> if (isLeapYear(year)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
}

internal fun isLeapYear(year: Int): Boolean {
    return (year and 3) == 0 && (year % 100 != 0 || year % 400 == 0)
}
