package io.github.pelmenstar1.rangecalendar.selection

import io.github.pelmenstar1.rangecalendar.SelectionType

interface SelectionState {
    val type: SelectionType
    val rangeStart: Int
    val rangeEnd: Int
}

internal val SelectionState.startCell: Cell
    get() = Cell(rangeStart)

internal val SelectionState.endCell: Cell
    get() = Cell(rangeEnd)

internal val SelectionState.range: CellRange
    get() = CellRange(rangeStart, rangeEnd)