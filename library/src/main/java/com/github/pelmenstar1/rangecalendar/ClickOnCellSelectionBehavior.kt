package com.github.pelmenstar1.rangecalendar

/**
 * Defines all possible behaviors when user clicks on already selected cell.
 */
enum class ClickOnCellSelectionBehavior {
    /**
     * Nothing happens. The cell remains selected.
     */
    NONE,

    /**
     * Unselects the cell.
     */
    CLEAR;

    companion object {
        /**
         * Returns [ClickOnCellSelectionBehavior] which has specified ordinal.
         *
         * @param index ordinal of [ClickOnCellSelectionBehavior]
         * @throws IllegalArgumentException if index is negative or greater than enum value with largest ordinal
         */
        fun ofOrdinal(index: Int) = when (index) {
            0 -> NONE
            1 -> CLEAR
            else -> throw IllegalArgumentException("index is out of range")
        }
    }
}