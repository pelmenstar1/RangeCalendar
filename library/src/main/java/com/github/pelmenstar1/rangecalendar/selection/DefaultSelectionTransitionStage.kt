package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.RectF

internal sealed class DefaultSelectionTransitionStage : SelectionTransitionStage {
    class TransformFragmentToFragment(
        val startShape: SelectionShapeInfo,
        val endShape: SelectionShapeInfo,
        val startShapeStartCellDistance: Float,
        val startShapeEndCellDistance: Float,
        val endShapeStartCellDistance: Float,
        val endShapeEndCellDistance: Float,
        val shapeInfo: SelectionShapeInfo
    ) : DefaultSelectionTransitionStage() {
        var currentStartCellDistance = Float.NaN
        var currentEndCellDistance = Float.NaN

        override fun overlaysRect(bounds: RectF): Boolean {
            return shapeInfo.overlaysRect(bounds)
        }
    }

    class AppearAlpha(
        val shapeInfo: SelectionShapeInfo,
        val isReversed: Boolean
    ) : DefaultSelectionTransitionStage() {
        var alpha = 0f

        override fun overlaysRect(bounds: RectF): Boolean {
            return shapeInfo.overlaysRect(bounds)
        }
    }

    class CellAppearBubble(
        val shapeInfo: SelectionShapeInfo,
        val isReversed: Boolean
    ) : DefaultSelectionTransitionStage() {
        val bounds = RectF()

        override fun overlaysRect(bounds: RectF): Boolean {
            return RectF.intersects(this.bounds, bounds)
        }
    }

    class MoveCellToCell(
        val startShape: SelectionShapeInfo,
        val endShape: SelectionShapeInfo,
        val shapeInfo: SelectionShapeInfo
    ): DefaultSelectionTransitionStage() {
        override fun overlaysRect(bounds: RectF): Boolean {
            return shapeInfo.overlaysRect(bounds)
        }
    }

    class Join(
        val originShapes: Array<SelectionShapeInfo>,
        val destinationShape: SelectionShapeInfo,
        val currentShapes: Array<SelectionShapeInfo>
    ) : DefaultSelectionTransitionStage() {
        override fun overlaysRect(bounds: RectF): Boolean {
            return currentShapes.any { it.overlaysRect(bounds) }
        }
    }

    class Split(
        val originShape: SelectionShapeInfo,
        val destinationShapes: Array<SelectionShapeInfo>,
        val currentShapes: Array<SelectionShapeInfo>
    ) : DefaultSelectionTransitionStage() {
        override fun overlaysRect(bounds: RectF): Boolean {
            return currentShapes.any { it.overlaysRect(bounds) }
        }
    }

    class NoOp(
        val shapeInfoArray: Array<SelectionShapeInfo>
    ): DefaultSelectionTransitionStage() {
        override fun overlaysRect(bounds: RectF): Boolean {
            return shapeInfoArray.any { it.overlaysRect(bounds) }
        }
    }
}