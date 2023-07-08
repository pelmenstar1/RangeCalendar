package com.github.pelmenstar1.rangecalendar.gesture

import com.github.pelmenstar1.rangecalendar.ClickOnCellSelectionBehavior

/**
 * Defines some general types of gestures that triggers selection.
 *
 * Currently, only [SelectionByGestureType.SINGLE_CELL_ON_CLICK] matters as internally [ClickOnCellSelectionBehavior] support relies on that.
 */
enum class SelectionByGestureType {
    /**
     * Single click that selects a cell.
     */
    SINGLE_CELL_ON_CLICK,

    /**
     * A gesture when the pointer moves and select custom cell range.
     */
    LONG_SELECTION,

    /**
     * Other type of gesture.
     */
    OTHER
}
