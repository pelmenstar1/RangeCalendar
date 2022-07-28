package io.github.pelmenstar1.rangecalendar.selection

import io.github.pelmenstar1.rangecalendar.SelectionType

/**
 * A set of properties which are needed to represent selection state.
 *
 * Implementation of the interface is excepted to be immutable.
 * Also, data in the implementation should be enough to render the state on canvas.
 * Exception is only transition between state.
 */
interface SelectionState {
    /**
     * Represents a transitive state between two [SelectionState] instances.
     *
     * It does not implements [SelectionState] interface, because it might not have any selection type or definitive range.
     * But it should contain enough information to render itself on canvas.
     */
    interface Transitive {
        /**
         * The state from which the transition starts.
         */
        val start: SelectionState

        /**
         * The state to which the transition should come.
         */
        val end: SelectionState

        /**
         * Changes the internal information to represent the transition between [start] and [end] in the phase specified by [fraction].
         *
         * @param fraction specifies fraction of the transition.
         * Should be in range `[0; 1]`.
         * If it's 0, it means the transition should be in initial phase.
         * If it's 1, it means the transition should be final phase.
         */
        fun handleTransition(fraction: Float)
    }

    /**
     * Type of selection.
     */
    val type: SelectionType

    /**
     * Index of the cell from which selection begins. Inclusive.
     */
    val rangeStart: Int

    /**
     * Index of the end cell in selection range. **Inclusive**
     */
    val rangeEnd: Int
}

internal val SelectionState.startCell: Cell
    get() = Cell(rangeStart)

internal val SelectionState.endCell: Cell
    get() = Cell(rangeEnd)

internal val SelectionState.range: CellRange
    get() = CellRange(rangeStart, rangeEnd)