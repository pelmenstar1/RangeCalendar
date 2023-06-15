package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.RectF
import com.github.pelmenstar1.rangecalendar.utils.lerp

class DefaultSelectionTransitionController : SelectionTransitionController {
    override fun handleTransition(state: SelectionState.Transitive, fraction: Float) {
        when (state) {
            // Cell transitions
            is DefaultSelectionState.CellState.AppearAlpha -> {
                state.alpha = fraction.reversedIf(state.isReversed)
            }

            is DefaultSelectionState.CellState.AppearBubble -> {
                val f = fraction.reversedIf(state.isReversed)

                bubbleAnimation(state.baseState, f, state.bounds)
            }

            is DefaultSelectionState.CellState.DualAlpha -> {
                state.startAlpha = 1f - fraction
                state.endAlpha = fraction
            }

            is DefaultSelectionState.CellState.DualBubble -> {
                bubbleAnimation(state.start, 1f - fraction, state.startBounds)
                bubbleAnimation(state.end, fraction, state.endBounds)
            }

            is DefaultSelectionState.CellState.MoveToCell -> {
                val start = state.start
                val end = state.end

                state.bounds.apply {
                    left = lerp(start.left, end.left, fraction)
                    top = lerp(start.top, end.top, fraction)

                    right = left + start.cellWidth
                    bottom = top + end.cellWidth
                }
            }


            // Custom range

            is DefaultSelectionState.RangeState.Alpha -> {
                state.alpha = fraction.reversedIf(state.isReversed)
            }
        }
    }

    companion object {
        private fun Float.reversedIf(flag: Boolean) = if (flag) 1f - this else this

        private fun bubbleAnimation(
            state: DefaultSelectionState.CellState,
            fraction: Float,
            outRect: RectF
        ) {
            val rx = state.cellWidth * 0.5f
            val ry = state.cellHeight * 0.5f

            val cx = state.left + rx
            val cy = state.top + ry

            val frx = rx * fraction
            val fry = ry * fraction

            outRect.apply {
                left = cx - frx
                top = cy - fry
                right = cx + frx
                bottom = cy + fry
            }
        }

        /*
        private fun weekFromCenter(
            baseState: DefaultSelectionState.WeekState,
            fraction: Float,
            outBounds: RectF
        ) {
            val startLeft = baseState.startLeft
            val endRight = baseState.endRight
            val rowCenterX = (endRight + startLeft) * 0.5f
            val halfRowWidth = (endRight - startLeft) * 0.5f

            val dx = halfRowWidth * fraction

            outBounds.apply {
                left = rowCenterX - dx
                top = baseState.top
                right = rowCenterX + dx
                bottom = baseState.bottom
            }
        }

         */
    }
}