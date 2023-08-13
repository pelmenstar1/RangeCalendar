package com.github.pelmenstar1.rangecalendar

import com.github.pelmenstar1.rangecalendar.selection.CellRange

class CalendarGridInfo {
    var inMonthRangeStart = -1
    var inMonthRangeEnd = -1

    internal var inMonthRange: CellRange
        get() = CellRange(inMonthRangeStart, inMonthRangeEnd)
        set(value) {
            inMonthRangeStart = value.start.index
            inMonthRangeEnd = value.end.index
        }
}