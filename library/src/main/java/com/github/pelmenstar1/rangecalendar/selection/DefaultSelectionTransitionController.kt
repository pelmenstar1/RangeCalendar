package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.PointF
import android.graphics.RectF
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import com.github.pelmenstar1.rangecalendar.CellMeasureManager
import com.github.pelmenstar1.rangecalendar.utils.lerp

class DefaultSelectionTransitionController : SelectionTransitionController {
    override fun handleTransition(
        state: SelectionState.Transitive,
        measureManager: CellMeasureManager,
        fraction: Float
    ) {
        when (state) {
            // Cell transitions
            is DefaultSelectionState.AppearAlpha -> {
                state.alpha = fraction.reversedIf(state.isReversed)
            }

            is DefaultSelectionState.DualAlpha -> {
                state.startAlpha = 1f - fraction
                state.endAlpha = fraction
            }

            is DefaultSelectionState.CellAppearBubble -> {
                val f = fraction.reversedIf(state.isReversed)

                bubbleAnimation(state.baseState, f, state.bounds)
            }

            is DefaultSelectionState.CellDualBubble -> {
                bubbleAnimation(state.start, 1f - fraction, state.startBounds)
                bubbleAnimation(state.end, fraction, state.endBounds)
            }

            is DefaultSelectionState.CellMoveToCell -> {
                val start = state.start.shapeInfo
                val end = state.end.shapeInfo

                val currentLeft = lerp(start.startLeft, end.startLeft, fraction)
                val currentTop = lerp(start.startTop, end.startTop, fraction)

                state.left = currentLeft
                state.top = currentTop

                state.cell = measureManager.getCellAt(
                    currentLeft,
                    currentTop,
                    relativity = CellMeasureManager.CoordinateRelativity.GRID
                )
            }

            is DefaultSelectionState.RangeToRange -> {
                val newStartCellDist =
                    lerp(state.startStateStartCellDistance, state.endStateStartCellDistance, fraction)
                val newEndCellDist = lerp(state.startStateEndCellDistance, state.endStateEndCellDistance, fraction)

                state.currentStartCellDistance = newStartCellDist
                state.currentEndCellDistance = newEndCellDist

                val newStartCell = measureManager.getCellAndPointByDistance(newStartCellDist, point)
                val (newStartCellLeft, newStartCellTop) = point

                val newEndCell = measureManager.getCellAndPointByDistance(newEndCellDist, point)
                val (newEndCellRight, newEndCellTop) = point

                state.shapeInfo.apply {
                    startLeft = newStartCellLeft
                    startTop = newStartCellTop

                    endRight = newEndCellRight
                    endTop = newEndCellTop

                    range = CellRange(newStartCell, newEndCell)
                }
            }
        }
    }

    companion object {
        private val point = PointF()

        private fun Float.reversedIf(flag: Boolean) = if (flag) 1f - this else this

        private fun bubbleAnimation(
            state: DefaultSelectionState,
            fraction: Float,
            outRect: RectF
        ) {
            val shapeInfo = state.shapeInfo

            val rx = shapeInfo.cellWidth * 0.5f
            val ry = shapeInfo.cellHeight * 0.5f

            val cx = shapeInfo.startLeft + rx
            val cy = shapeInfo.startTop + ry

            val frx = rx * fraction
            val fry = ry * fraction

            outRect.apply {
                left = cx - frx
                top = cy - fry
                right = cx + frx
                bottom = cy + fry
            }
        }
    }
}