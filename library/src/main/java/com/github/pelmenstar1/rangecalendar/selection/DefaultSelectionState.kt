package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.RectF
import com.github.pelmenstar1.rangecalendar.SelectionType

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
        val cellWidth: Float,
        val cellHeight: Float
    ) : DefaultSelectionState(SelectionType.CELL, CellRange.cell(cell)) {
        class AppearAlpha(
            val baseState: CellState,
            val isReversed: Boolean
        ) : SelectionState.Transitive {
            override val start: SelectionState
                get() = baseState

            override val end: SelectionState
                get() = baseState

            var alpha = 0f
        }

        class AppearBubble(
            val baseState: CellState,
            val isReversed: Boolean
        ) : BoundsTransitionBase() {
            override val start: SelectionState
                get() = baseState

            override val end: SelectionState
                get() = baseState
        }

        class DualAlpha(
            override val start: CellState,
            override val end: CellState
        ) : SelectionState.Transitive {
            var startAlpha = Float.NaN
            var endAlpha = Float.NaN
        }

        class DualBubble(
            override val start: CellState,
            override val end: CellState
        ) : SelectionState.Transitive {
            val startBounds = RectF()
            val endBounds = RectF()
        }

        class MoveToCell(
            override val start: CellState,
            override val end: CellState
        ) : BoundsTransitionBase()

        class ToWeekOnRow(
            override val start: CellState,
            override val end: WeekState,
            val isReversed: Boolean
        ) : BoundsTransitionBase()
    }

    class WeekState(
        range: CellRange,
        val startLeft: Float,
        val top: Float,
        val endRight: Float,
        val bottom: Float
    ) : DefaultSelectionState(SelectionType.WEEK, range) {
        class FromCenter(
            val baseState: WeekState,
            val isReversed: Boolean
        ) : BoundsTransitionBase() {
            override val start: SelectionState
                get() = baseState

            override val end: SelectionState
                get() = baseState
        }

        class ToWeek(
            override val start: WeekState,
            override val end: WeekState
        ) : SelectionState.Transitive {
            val startBounds = RectF()
            val endBounds = RectF()
        }
    }

    sealed class CustomRangeStateBase(
        type: SelectionType,
        range: CellRange,
        val startLeft: Float, val startTop: Float,
        val endRight: Float, val endTop: Float,
        val firstCellOnRowLeft: Float, val lastCellOnRowRight: Float,
        val cellWidth: Float,
        val cellHeight: Float
    ) : DefaultSelectionState(type, range) {
        class Alpha(
            val baseState: CustomRangeStateBase,
            val isReversed: Boolean
        ) : SelectionState.Transitive {
            override val start: SelectionState
                get() = baseState

            override val end: SelectionState
                get() = baseState

            var alpha = Float.NaN
        }
    }

    class MonthState(
        range: CellRange,
        startLeft: Float, startTop: Float,
        endRight: Float, endTop: Float,
        firstCellOnRowLeft: Float, lastCellOnRowRight: Float,
        cellWidth: Float, cellHeight: Float,
    ) : CustomRangeStateBase(
        SelectionType.MONTH,
        range,
        startLeft, startTop,
        endRight, endTop,
        firstCellOnRowLeft, lastCellOnRowRight,
        cellWidth, cellHeight
    )

    class CustomRangeState(
        range: CellRange,
        startLeft: Float, startTop: Float,
        endRight: Float, endTop: Float,
        firstCellOnRowLeft: Float, lastCellOnRowRight: Float,
        cellWidth: Float, cellHeight: Float,
    ) : CustomRangeStateBase(
        SelectionType.CUSTOM,
        range,
        startLeft, startTop,
        endRight, endTop,
        firstCellOnRowLeft, lastCellOnRowRight,
        cellWidth, cellHeight
    )

    object None : DefaultSelectionState(SelectionType.NONE, CellRange.Invalid)

    class CellToWeek(
        override val start: CellState,
        override val end: WeekState,
        val isReversed: Boolean
    ) : SelectionState.Transitive {
        val weekBounds = RectF()
        var cellAlpha = 0f
    }

    class CellToMonth(
        override val start: CellState,
        override val end: MonthState,
        val cx: Float, val cy: Float,
        val startRadius: Float,
        val finalRadius: Float,
        val isReversed: Boolean
    ) : SelectionState.Transitive {
        var radius = Float.NaN
    }

    abstract class BoundsTransitionBase : SelectionState.Transitive {
        val bounds = RectF()
    }
}