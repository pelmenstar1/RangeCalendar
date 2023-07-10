package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.RectF
import com.github.pelmenstar1.rangecalendar.utils.rangeContains

internal interface SelectionShapeBasedState : SelectionState {
    val shapeInfo: SelectionShapeInfo
}

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

        override val hasDefinitiveRange: Boolean
            get() = true

        var currentStartCellDistance = 0f
        var currentEndCellDistance = 0f

        override fun overlaysCell(cellIndex: Int): Boolean {
            return shapeInfo.range.contains(Cell(cellIndex))
        }
    }

    class AppearAlpha(
        val baseState: DefaultSelectionState,
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

        override val hasDefinitiveRange: Boolean
            get() = true

        var alpha = 0f

        override fun overlaysCell(cellIndex: Int): Boolean {
            return baseState.rangeStart == cellIndex
        }
    }

    class DualAlpha(
        override val start: DefaultSelectionState,
        override val end: DefaultSelectionState
    ) : SelectionState.Transitive {
        var startAlpha = Float.NaN
        var endAlpha = Float.NaN

        override val rangeStart: Int
            get() = throwUndefinedRange()

        override val rangeEnd: Int
            get() = throwUndefinedRange()

        override val hasDefinitiveRange: Boolean
            get() = false

        override fun overlaysCell(cellIndex: Int): Boolean {
            return start.contains(Cell(cellIndex)) || end.contains(Cell(cellIndex))
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

        override val hasDefinitiveRange: Boolean
            get() = true

        override val rangeStart: Int
            get() = baseState.rangeStart

        override val rangeEnd: Int
            get() = baseState.rangeEnd

        val bounds = RectF()

        override fun overlaysCell(cellIndex: Int): Boolean {
            return baseState.rangeStart == cellIndex
        }
    }

    class CellDualBubble(
        override val start: DefaultSelectionState,
        override val end: DefaultSelectionState
    ) : SelectionState.Transitive {
        override val hasDefinitiveRange: Boolean
            get() = false

        override val rangeStart: Int
            get() = throwUndefinedRange()

        override val rangeEnd: Int
            get() = throwUndefinedRange()

        val startBounds = RectF()
        val endBounds = RectF()

        override fun overlaysCell(cellIndex: Int): Boolean {
            return start.rangeStart == cellIndex || end.rangeStart == cellIndex
        }
    }

    class CellMoveToCell(
        override val start: DefaultSelectionState,
        override val end: DefaultSelectionState
    ) : SelectionState.Transitive {
        override val hasDefinitiveRange: Boolean
            get() = true

        override var rangeStart: Int = 0
        override var rangeEnd: Int = 0

        var left: Float = Float.NaN
        var top: Float = Float.NaN

        override fun overlaysCell(cellIndex: Int): Boolean {
            val startCell = start.startCell
            val endCell = end.startCell

            val cellX = Cell(cellIndex).gridX
            val cellY = Cell(cellIndex).gridY

            val startX = startCell.gridX
            val startY = startCell.gridY

            val endX = endCell.gridX
            val endY = endCell.gridY

            return if (startY == endY) {
                cellY == startY && rangeContains(startX, endX, cellX)
            } else {
                // Then cells have same x on grid.
                cellX == startX && rangeContains(startY, endY, cellY)
            }
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