package com.github.pelmenstar1.rangecalendar

/**
 * Responsible for providing accessibility-related information about a particular cell of the [RangeCalendarView].
 */
interface RangeCalendarCellAccessibilityInfoProvider {
    /**
     * Gets content description for the cell that represents specified date.
     *
     * @param year year of the date.
     * @param month month of the date, in range `[1; 12]`
     * @param dayOfMonth day of the date, 1-based.
     */
    fun getContentDescription(year: Int, month: Int, dayOfMonth: Int): CharSequence
}