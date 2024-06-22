package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.PointF
import android.graphics.RectF
import com.github.pelmenstar1.rangecalendar.CellMeasureManager

class DefaultSelectionTransitionController : SelectionTransitionController {
    override fun handleTransition(
        transition: SelectionTransition,
        measureManager: CellMeasureManager,
        fraction: Float
    ) {
        // TODO: Implement it
    }

    companion object {
        private val point = PointF()

        private fun Float.reversedIf(flag: Boolean) = if (flag) 1f - this else this

        private fun bubbleAnimation(
            state: DefaultSelectionFragmentState,
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