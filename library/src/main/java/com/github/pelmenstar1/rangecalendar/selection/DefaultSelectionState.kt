package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.RectF

internal interface SelectionShapeBasedState : SelectionState {
    val shapeInfo: SelectionShapeInfo
}

internal val SelectionShapeBasedState.range: CellRange
    get() = shapeInfo.range

internal class DefaultSelectionState(override val shapeInfo: SelectionShapeInfo) : SelectionShapeBasedState {
    override val rangeStart: Int
        get() = shapeInfo.range.start.index

    override val rangeEnd: Int
        get() = shapeInfo.range.end.index

    class RangeToRange(
        override val start: SelectionShapeBasedState,
        override val end: SelectionShapeBasedState,
        val startStateStartCellDistance: Float,
        val startStateEndCellDistance: Float,
        val endStateStartCellDistance: Float,
        val endStateEndCellDistance: Float,
        override val shapeInfo: SelectionShapeInfo
    ) : SelectionShapeBasedState, SelectionState.Transitive {
        override val rangeStart: Int
            get() = shapeInfo.range.start.index

        override val rangeEnd: Int
            get() = shapeInfo.range.end.index

        override val isRangeDefined: Boolean
            get() = true

        var currentStartCellDistance = 0f
        var currentEndCellDistance = 0f

        override fun overlaysRect(bounds: RectF): Boolean {
            return shapeInfo.overlaysRect(bounds)
        }
    }

    class AppearAlpha(
        val baseState: SelectionShapeBasedState,
        val isReversed: Boolean
    ) : SelectionState.Transitive {
        override val start: SelectionState
            get() = baseState

        override val end: SelectionState
            get() = baseState

        override val rangeStart: Int
            get() = baseState.rangeStart

        override val rangeEnd: Int
            get() = baseState.rangeEnd

        override val isRangeDefined: Boolean
            get() = true

        var alpha = 0f

        override fun overlaysRect(bounds: RectF): Boolean {
            return baseState.shapeInfo.overlaysRect(bounds)
        }
    }

    class DualAlpha(
        override val start: SelectionShapeBasedState,
        override val end: SelectionShapeBasedState
    ) : SelectionState.Transitive {
        var startAlpha = Float.NaN
        var endAlpha = Float.NaN

        override val rangeStart: Int
            get() = throwUndefinedRange()

        override val rangeEnd: Int
            get() = throwUndefinedRange()

        override val isRangeDefined: Boolean
            get() = false

        override fun overlaysRect(bounds: RectF): Boolean {
            return start.shapeInfo.overlaysRect(bounds) || end.shapeInfo.overlaysRect(bounds)
        }
    }

    class CellAppearBubble(
        val baseState: DefaultSelectionState,
        val isReversed: Boolean
    ) : SelectionState.Transitive {
        override val start: SelectionState
            get() = baseState

        override val end: SelectionState
            get() = baseState

        override val isRangeDefined: Boolean
            get() = true

        override val rangeStart: Int
            get() = baseState.rangeStart

        override val rangeEnd: Int
            get() = baseState.rangeEnd

        val bounds = RectF()

        override fun overlaysRect(bounds: RectF): Boolean {
            return RectF.intersects(this.bounds, bounds)
        }
    }

    class CellDualBubble(
        override val start: DefaultSelectionState,
        override val end: DefaultSelectionState
    ) : SelectionState.Transitive {
        override val isRangeDefined: Boolean
            get() = false

        override val rangeStart: Int
            get() = throwUndefinedRange()

        override val rangeEnd: Int
            get() = throwUndefinedRange()

        val startBounds = RectF()
        val endBounds = RectF()

        override fun overlaysRect(bounds: RectF): Boolean {
            return RectF.intersects(startBounds, bounds) || RectF.intersects(endBounds, bounds)
        }
    }

    class CellMoveToCell(
        override val start: SelectionShapeBasedState,
        override val end: SelectionShapeBasedState,
        override val shapeInfo: SelectionShapeInfo
    ) : SelectionShapeBasedState, SelectionState.Transitive {
        override val isRangeDefined: Boolean
            get() = true

        override val rangeStart: Int
            get() = shapeInfo.range.start.index

        override val rangeEnd: Int
            get() = rangeStart

        override fun overlaysRect(bounds: RectF): Boolean {
            return shapeInfo.overlaysRect(bounds)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null || javaClass != other.javaClass) return false

        other as DefaultSelectionState

        return shapeInfo.range == other.shapeInfo.range
    }

    override fun hashCode(): Int = rangeStart * 31 + rangeEnd

    override fun toString(): String {
        return "DefaultSelectionState(rangeStart=$rangeStart, rangeEnd=$rangeEnd)"
    }

    companion object {
        val None = DefaultSelectionState(SelectionShapeInfo())

        private fun throwUndefinedRange(): Nothing {
            throw IllegalStateException("The selection transitive state doesn't have defined range")
        }
    }
}