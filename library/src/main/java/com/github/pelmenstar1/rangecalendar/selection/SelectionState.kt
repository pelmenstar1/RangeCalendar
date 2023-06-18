package com.github.pelmenstar1.rangecalendar.selection

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
         * Determines whether selection determined by the transitive state overlays a cell specified by [cellIndex].
         *
         * @param cellIndex index of the cell, should be in range 0..41
         */
        fun overlaysCell(cellIndex: Int): Boolean
    }

    /**
     * Index of the cell from which selection begins. Inclusive.
     */
    val rangeStart: Int

    /**
     * Index of the end cell in selection range. **Inclusive**
     */
    val rangeEnd: Int
}

internal inline val SelectionState.startCell: Cell
    get() = Cell(rangeStart)

internal inline val SelectionState.endCell: Cell
    get() = Cell(rangeEnd)

internal val SelectionState.range: CellRange
    get() = CellRange(rangeStart, rangeEnd)

internal val SelectionState.isNone: Boolean
    get() = rangeStart > rangeEnd

internal fun SelectionState.contains(cell: Cell): Boolean {
    return cell.index in rangeEnd..rangeStart
}

internal fun SelectionState.isSingleCell(cell: Cell): Boolean {
    return rangeStart == cell.index && rangeEnd == cell.index
}