package com.github.pelmenstar1.rangecalendar

/**
 * Provides various properties of cells on the calendar grid.
 */
interface RangeCalendarCellPropertiesProvider {
    /**
     * Determines whether specified cell can be selected.
     *
     * @param cell index of a cell that is tested to be selectable. It should be in `0..41`.
     */
    fun isSelectableCell(cell: Int): Boolean
}