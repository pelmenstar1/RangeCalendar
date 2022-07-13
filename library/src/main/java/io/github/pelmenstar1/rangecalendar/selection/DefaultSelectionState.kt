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

    abstract class CustomRangeStateBase(
        type: SelectionType,
        range: CellRange,
        val startLeft: Float, val startTop: Float,
        val endRight: Float, val endTop: Float,
        val firstCellLeft: Float, val lastCellRight: Float
    ): DefaultSelectionState(type, range)

    class MonthState(
        range: CellRange,
        startLeft: Float, startTop: Float,
        endRight: Float, endTop: Float,
        firstCellLeft: Float, lastCellRight: Float
    ): CustomRangeStateBase(SelectionType.MONTH, range, startLeft, startTop, endRight, endTop, firstCellLeft, lastCellRight)

    class CustomRangeState(
        range: CellRange,
        startLeft: Float, startTop: Float,
        endRight: Float, endTop: Float,
        firstCellLeft: Float, lastCellRight: Float
    ): CustomRangeStateBase(SelectionType.CUSTOM, range, startLeft, startTop, endRight, endTop, firstCellLeft, lastCellRight)

    object None : DefaultSelectionState(SelectionType.NONE, CellRange.Invalid)
}