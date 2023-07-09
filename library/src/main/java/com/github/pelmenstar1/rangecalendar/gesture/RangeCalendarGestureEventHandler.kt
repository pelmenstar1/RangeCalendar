package com.github.pelmenstar1.rangecalendar.gesture

import com.github.pelmenstar1.rangecalendar.SelectionAcceptanceStatus
import com.github.pelmenstar1.rangecalendar.RangeCalendarView

/**
 * Provides various methods for sending events, that might happen during processing gesture types.
 */
interface RangeCalendarGestureEventHandler {
    /**
     * Sends an event that a cell range between [start] and [end] is selected.
     * The selection might not be accepted,
     * for example the range to select is rejected by the selection gate ([RangeCalendarView.SelectionGate])
     *
     * @param start a start of the cell range, in range 0..41
     * @param start an end of the cell range, in range 0..41
     * @param gestureType specifies which gesture caused the selection
     *
     * @return status of the request for selection
     */
    fun selectRange(start: Int, end: Int, gestureType: SelectionByGestureType): SelectionAcceptanceStatus

    /**
     * Sends an event that whole month range is selected.
     * The selection might not be accepted,
     * for example the range to select is rejected by the selection gate ([RangeCalendarView.SelectionGate])
     *
     * @return status of the request for selection.
     */
    fun selectMonth(): SelectionAcceptanceStatus

    /**
     * Disallows the parent of hosting view to intercept touch events.
     */
    fun disallowParentInterceptEvent()

    /**
     * Reports that selection of custom range is started.
     */
    fun reportStartSelectingRange()

    /**
     * Reports that user pointer, under specified [cell], is down, i.e the user pointer is hovering under the cell.
     */
    fun reportStartHovering(cell: Int)

    /**
     * Reports that user pointer, that might be under some cell, is up.
     */
    fun reportStopHovering()
}