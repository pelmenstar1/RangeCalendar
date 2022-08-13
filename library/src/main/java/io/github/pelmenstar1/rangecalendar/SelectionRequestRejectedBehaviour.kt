package io.github.pelmenstar1.rangecalendar

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
    CLEAR_CURRENT_SELECTION
}