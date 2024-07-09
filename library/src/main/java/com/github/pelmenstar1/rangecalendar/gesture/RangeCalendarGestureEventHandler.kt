package com.github.pelmenstar1.rangecalendar.gesture

import com.github.pelmenstar1.rangecalendar.SelectionAcceptanceStatus

/**
 * Provides various methods for sending events, that might happen during processing gesture types.
 */
interface RangeCalendarGestureEventHandler {
    fun reportSelect(operation: GestureSelectionOperation, gestureType: SelectionByGestureType): SelectionAcceptanceStatus

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