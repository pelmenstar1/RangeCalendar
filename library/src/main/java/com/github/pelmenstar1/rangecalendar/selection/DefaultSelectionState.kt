package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.RectF

internal sealed class DefaultSelectionState(val range: CellRange) : SelectionState {
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
    ) : DefaultSelectionState(CellRange.single(cell)) {
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
    }

    class RangeState(
        range: CellRange,
        val startLeft: Float, val startTop: Float,
        val endRight: Float, val endTop: Float,
        val firstCellOnRowLeft: Float, val lastCellOnRowRight: Float,
        val cellWidth: Float,
        val cellHeight: Float
    ) : DefaultSelectionState(range) {
        class Alpha(
            val baseState: RangeState,
            val isReversed: Boolean
        ) : SelectionState.Transitive {
            override val start: SelectionState
                get() = baseState

            override val end: SelectionState
                get() = baseState

            var alpha = Float.NaN
        }
    }

    object None : DefaultSelectionState(CellRange.Invalid)

    abstract class BoundsTransitionBase : SelectionState.Transitive {
        val bounds = RectF()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null || javaClass != other.javaClass) return false

        other as DefaultSelectionState

        return range == other.range
    }

    override fun hashCode(): Int {
        var result = rangeStart
        result = result * 31 + rangeEnd

        return result
    }

    override fun toString(): String {
        return "DefaultSelectionState(rangeStart=$rangeStart, rangeEnd=$rangeEnd)"
    }
}