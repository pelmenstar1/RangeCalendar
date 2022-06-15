package io.github.pelmenstar1.rangecalendar

/**
 * Defines all supported types of selection.
 */
enum class SelectionType {
    /**
     * None, no selection.
     */
    NONE,

    /**
     * Cell is selected.
     */
    CELL,

    /**
     * Week is selected.
     */
    WEEK,

    /**
     * Month is selected.
     */
    MONTH,

    /**
     * Custom-range selection.
     */
    CUSTOM;

    companion object {
        /**
         * Returns [SelectionType] which has specified ordinal.
         *
         * @param index ordinal of [SelectionType]
         * @throws IllegalArgumentException if index is negative or greater than enum value with largest ordinal
         */
        fun ofOrdinal(index: Int) = when (index) {
            0 -> NONE
            1 -> CELL
            2 -> WEEK
            3 -> MONTH
            4 -> CUSTOM
            else -> throw IllegalArgumentException("index is out of bounds")
        }
    }
}