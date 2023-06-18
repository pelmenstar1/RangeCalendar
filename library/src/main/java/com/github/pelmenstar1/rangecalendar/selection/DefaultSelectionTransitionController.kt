package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import androidx.core.graphics.component1
import androidx.core.graphics.component2
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
                val start = state.start
                val end = state.end

                state.bounds.apply {
                    left = lerp(start.startLeft, end.startLeft, fraction)
                    top = lerp(start.startTop, end.startTop, fraction)

                    right = left + start.cellWidth
                    bottom = top + start.cellHeight
                }
            }

            is DefaultSelectionState.RangeToRange -> {
                val newStartCellDist = lerp(
                    state.startStateStartCellDistance,
                    state.endStateStartCellDistance,
                    fraction
                )

                val newEndCellDist = lerp(
                    state.startStateEndCellDistance,
                    state.endStateEndCellDistance,
                    fraction
                )

                val newStartCell = measureManager.getCellAndPointByDistance(newStartCellDist, point)
                val (newStartCellLeft, newStartCellTop) = point

                val newEndCell = measureManager.getCellAndPointByDistance(newEndCellDist, point)
                val (newEndCellRight, newEndCellTop) = point

                state.startLeft = newStartCellLeft
                state.startTop = newStartCellTop

                state.endRight = newEndCellRight
                state.endTop = newEndCellTop

                state.range = CellRange(newStartCell, newEndCell)

                transitionRadiiInRangeToRange(
                    state,
                    measureManager,
                    Cell(newStartCell),
                    Cell(newEndCell),
                    newStartCellDist,
                    newEndCellDist
                )
            }
        }
    }

    private fun transitionRadiiInRangeToRange(
        state: DefaultSelectionStateRangeInfo,
        measureManager: CellMeasureManager,
        startCell: Cell,
        endCell: Cell,
        startDistance: Float,
        endDistance: Float,
    ) {
        val startGridX = startCell.gridX
        val endGridX = endCell.gridX
        val gridYDiff = endCell.gridY - startCell.gridY

        val roundRadius = state.roundRadius
        val radii = state.radii

        val startXDist = measureManager.getXOfCellDistance(startDistance)
        val startCellFraction = measureManager.getCellFractionByDistance(startXDist)

        val endXDist = measureManager.getXOfCellDistance(endDistance)
        val endCellFraction = measureManager.getCellFractionByDistance(endXDist)

        Log.i("TransController", "startFr=$startCellFraction, endFr=$endCellFraction")

        radii.firstRowLb = if (startGridX == 0) {
            roundRadius * (1f - startCellFraction)
        } else {
            roundRadius
        }

        radii.firstRowRb = if (gridYDiff == 1 && endGridX == 6) {
            roundRadius * (1f - endCellFraction)
        } else {
            if (gridYDiff == 1) roundRadius else 0f
        }

        radii.lastRowLt = if (gridYDiff == 1 && startGridX == 0) {
            roundRadius * (startCellFraction)
        } else {
            if (gridYDiff == 1) roundRadius else 0f
        }

        radii.lastRowRt = if (endGridX == 6) {
            roundRadius * (1f - endCellFraction)
        } else {
            roundRadius
        }

        if (gridYDiff > 1) {
            radii.centerRectLt = if (startGridX == 0) {
                roundRadius * (1f - startCellFraction)
            } else {
                roundRadius
            }

            radii.centerRectRb = if (endGridX == 6) {
                roundRadius * (1f - endCellFraction)
            } else {
                roundRadius
            }
        } else {
            radii.centerRectLt = roundRadius
            radii.centerRectRb = roundRadius
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
            val rx = state.cellWidth * 0.5f
            val ry = state.cellHeight * 0.5f

            val cx = state.startLeft + rx
            val cy = state.startTop + ry

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