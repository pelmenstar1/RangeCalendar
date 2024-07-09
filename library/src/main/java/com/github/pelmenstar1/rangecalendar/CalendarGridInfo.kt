package com.github.pelmenstar1.rangecalendar

class CalendarGridInfo {
    private var _inMonthRangeStart = -1
    private var _inMonthRangeEnd = -1

    val inMonthRangeStart: Int
        get() = _inMonthRangeStart

    val inMonthRangeEnd: Int
        get() = _inMonthRangeEnd

    fun setInMonthRange(start: Int, endInclusive: Int) {
        require(start <= endInclusive) { "Invalid range" }

        _inMonthRangeStart = start
        _inMonthRangeEnd = endInclusive
    }
}