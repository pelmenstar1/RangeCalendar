package com.github.pelmenstar1.rangecalendar

/**
 * Contains all supported behaviours when selection request is rejected.
 */
enum class SelectionRequestRejectedBehaviour {
    /**
     * Current selection is not cleared and preserved.
     */
    PRESERVE_CURRENT_SELECTION,

    /**
     * Current selection is cleared.
     */
    CLEAR_CURRENT_SELECTION;

    companion object {
        fun fromOrdinal(value: Int) = when (value) {
            0 -> PRESERVE_CURRENT_SELECTION
            1 -> CLEAR_CURRENT_SELECTION
            else -> throw IllegalArgumentException("value")
        }
    }
}