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