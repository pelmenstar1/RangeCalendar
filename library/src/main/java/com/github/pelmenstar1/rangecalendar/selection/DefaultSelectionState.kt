package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.RectF
import com.github.pelmenstar1.rangecalendar.utils.rangeContains

internal class DefaultSelectionState(val shapeInfo: SelectionShapeInfo) : SelectionState {
    override val rangeStart: Int
        get() = shapeInfo.range.start.index

    override val rangeEnd: Int
        get() = shapeInfo.range.end.index

    class RangeToRange(
        override val start: DefaultSelectionState,
        override val end: DefaultSelectionState,
        val startStateStartCellDistance: Float,
        val startStateEndCellDistance: Float,
        val endStateStartCellDistance: Float,
        val endStateEndCellDistance: Float,
        val shapeInfo: SelectionShapeInfo
    ) : SelectionState.Transitive {
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

        val bounds = RectF()

        override fun overlaysCell(cellIndex: Int): Boolean {
            return baseState.rangeStart == cellIndex
        }
    }

    class CellDualBubble(
        override val start: DefaultSelectionState,
        override val end: DefaultSelectionState
    ) : SelectionState.Transitive {
        val startBounds = RectF()
        val endBounds = RectF()

        override fun overlaysCell(cellIndex: Int): Boolean {
            return start.rangeStart == cellIndex || end.rangeEnd == cellIndex
        }
    }

    class CellMoveToCell(
        override val start: DefaultSelectionState,
        override val end: DefaultSelectionState
    ) : SelectionState.Transitive {
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
    }
}