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
        val top: Float,
        val cellSize: Float
    ) : DefaultSelectionState(SelectionType.CELL, CellRange.cell(cell))

    class WeekState(
        range: CellRange,
        val startLeft: Float,
        val top: Float,
        val endRight: Float,
        val bottom: Float
    ) : DefaultSelectionState(SelectionType.WEEK, range)

    sealed class CustomRangeStateBase(
        type: SelectionType,
        range: CellRange,
        val startLeft: Float, val startTop: Float,
        val endRight: Float, val endTop: Float,
        val firstCellOnRowLeft: Float, val lastCellOnRowRight: Float,
        val cellSize: Float
    ) : DefaultSelectionState(type, range)

    class MonthState(
        range: CellRange,
        startLeft: Float, startTop: Float,
        endRight: Float, endTop: Float,
        firstCellOnRowLeft: Float, lastCellOnRowRight: Float,
        cellSize: Float
    ) : CustomRangeStateBase(
        SelectionType.MONTH,
        range,
        startLeft, startTop,
        endRight, endTop,
        firstCellOnRowLeft, lastCellOnRowRight,
        cellSize
    )

    class CustomRangeState(
        range: CellRange,
        startLeft: Float, startTop: Float,
        endRight: Float, endTop: Float,
        firstCellOnRowLeft: Float, lastCellOnRowRight: Float,
        cellSize: Float
    ) : CustomRangeStateBase(
        SelectionType.CUSTOM,
        range,
        startLeft, startTop,
        endRight, endTop,
        firstCellOnRowLeft, lastCellOnRowRight,
        cellSize
    )

    object None : DefaultSelectionState(SelectionType.NONE, CellRange.Invalid)
}