package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.PointF
import android.graphics.RectF
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import com.github.pelmenstar1.rangecalendar.utils.lerp

class DefaultSelectionTransitionController : SelectionTransitionController {
    override fun handleTransition(state: SelectionState.Transitive, measureManager: CellMeasureManager, fraction: Float) {
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
                val newStartCellDist = lerp(state.startStateStartCellDistance, state.endStateStartCellDistance, fraction)
                val newEndCellDist = lerp(state.startStateEndCellDistance, state.endStateEndCellDistance, fraction)

                val newStartCell = measureManager.getCellAndPointByDistance(newStartCellDist, point)
                val (newStartCellLeft, newStartCellTop) = point

                val newEndCell = measureManager.getCellAndPointByDistance(newEndCellDist, point)
                val (newEndCellRight, newEndCellTop) = point

                state.startLeft = newStartCellLeft
                state.startTop = newStartCellTop

                state.endRight = newEndCellRight
                state.endTop = newEndCellTop

                state.range = CellRange(newStartCell, newEndCell)
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