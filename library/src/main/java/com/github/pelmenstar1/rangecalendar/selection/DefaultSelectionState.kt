package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.RectF

internal interface DefaultSelectionStateRangeInfo {
    val range: CellRange

    val startLeft: Float
    val startTop: Float
    val endRight: Float
    val endTop: Float

    val firstCellOnRowLeft: Float
    val lastCellOnRowRight: Float

    val cellWidth: Float
    val cellHeight: Float
}

internal class DefaultSelectionState(
    override val range: CellRange,
    override val startLeft: Float, override val startTop: Float,
    override val endRight: Float, override val endTop: Float,
    override val firstCellOnRowLeft: Float, override val lastCellOnRowRight: Float,
    override val cellWidth: Float,
    override val cellHeight: Float
) : SelectionState, DefaultSelectionStateRangeInfo {
    override val rangeStart: Int
        get() = range.start.index

    override val rangeEnd: Int
        get() = range.end.index

    class RangeToRange(
        override val start: DefaultSelectionState,
        override val end: DefaultSelectionState,
        val startStateStartCellDistance: Float,
        val startStateEndCellDistance: Float,
        val endStateStartCellDistance: Float,
        val endStateEndCellDistance: Float
    ) : SelectionState.Transitive, DefaultSelectionStateRangeInfo {
        override var range: CellRange = CellRange.Invalid
        override var startLeft = 0f
        override var startTop = 0f

        override var endRight = 0f
        override var endTop = 0f

        override val firstCellOnRowLeft: Float
            get() = start.firstCellOnRowLeft

        override val lastCellOnRowRight: Float
            get() = start.lastCellOnRowRight

        override val cellWidth: Float
            get() = start.cellWidth

        override val cellHeight: Float
            get() = start.cellHeight
    }

    class AppearAlpha(
        val baseState: DefaultSelectionState,
        val isReversed: Boolean
    ) : SelectionState.Transitive {
        override val start: SelectionState
            get() = baseState

        override val end: SelectionState
            get() = baseState

        var alpha = 0f
    }

    class DualAlpha(
        override val start: DefaultSelectionState,
        override val end: DefaultSelectionState
    ) : SelectionState.Transitive {
        var startAlpha = Float.NaN
        var endAlpha = Float.NaN
    }

    class CellAppearBubble(
        val baseState: DefaultSelectionState,
        val isReversed: Boolean
    ) : BoundsTransitionBase() {
        override val start: SelectionState
            get() = baseState

        override val end: SelectionState
            get() = baseState
    }

    class CellDualBubble(
        override val start: DefaultSelectionState,
        override val end: DefaultSelectionState
    ) : SelectionState.Transitive {
        val startBounds = RectF()
        val endBounds = RectF()
    }

    class CellMoveToCell(
        override val start: DefaultSelectionState,
        override val end: DefaultSelectionState
    ) : BoundsTransitionBase()

    abstract class BoundsTransitionBase : SelectionState.Transitive {
        val bounds = RectF()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null || javaClass != other.javaClass) return false

        other as DefaultSelectionState

        return range == other.range
    }

    override fun hashCode(): Int = rangeStart * 31 + rangeEnd

    override fun toString(): String {
        return "DefaultSelectionState(rangeStart=$rangeStart, rangeEnd=$rangeEnd)"
    }

    companion object {
        val None = DefaultSelectionState(CellRange.Invalid, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    }
}