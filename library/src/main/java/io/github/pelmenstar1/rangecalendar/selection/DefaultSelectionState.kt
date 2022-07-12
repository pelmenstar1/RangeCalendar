package io.github.pelmenstar1.rangecalendar.selection

import io.github.pelmenstar1.rangecalendar.SelectionType

internal sealed class DefaultSelectionState(
    override val type: SelectionType,
    val range: CellRange
) : SelectionState {
    override val rangeStart: Int
        get() = range.start.index

    override val rangeEnd: Int
        get() = range.end.index

    class CellState(
        val cell: Cell,
        val left: Float,
        val top: Float
    ) : DefaultSelectionState(SelectionType.CELL, CellRange.cell(cell))

    class WeekState(
        range: CellRange,
        val startLeft: Float, val endRight: Float,
        val top: Float
    ) : DefaultSelectionState(SelectionType.WEEK, range)

    class MonthState(
        range: CellRange
    ): DefaultSelectionState(SelectionType.MONTH, range)

    class CustomRangeState(
        range: CellRange
    ): DefaultSelectionState(SelectionType.CUSTOM, range)

    object None : DefaultSelectionState(SelectionType.NONE, CellRange.Invalid)
}