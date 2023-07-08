package com.github.pelmenstar1.rangecalendar

/**
 * Defines types of selection acceptance statuses.
 */
enum class SelectionAcceptanceStatus {
    /**
     * Selection is accepted and the range is changed.
     *
     * @see [ACCEPTED_SAME_RANGE]
     */
    ACCEPTED,

    /**
     * Selection is accepted but the selection range is not changed.
     * Situation when the range is changed and when it's not are differentiated
     * because sometimes different handling of these two situations is necessary
     */
    ACCEPTED_SAME_RANGE,

    /**
     * Selection is rejected because one of these reasons:
     * - The range is empty after narrowing it to enabled cell range and/or in-month cell range
     * - The range is rejected by the gate ([RangeCalendarView.SelectionGate])
     * - The range is not accepted because [ClickOnCellSelectionBehavior] is [ClickOnCellSelectionBehavior.CLEAR] and selection is triggered by user gesture
     */
    REJECTED
}