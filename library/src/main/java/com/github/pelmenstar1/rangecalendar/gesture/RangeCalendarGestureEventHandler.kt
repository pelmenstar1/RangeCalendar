package com.github.pelmenstar1.rangecalendar.gesture

import com.github.pelmenstar1.rangecalendar.SelectionAcceptanceStatus

interface RangeCalendarGestureEventHandler {
    fun selectRange(start: Int, end: Int, gestureType: SelectionByGestureType): SelectionAcceptanceStatus

    fun disallowParentInterceptEvent()

    fun reportStartSelectingRange()
    fun reportStartHovering(cell: Int)
    fun reportStopHovering()
}