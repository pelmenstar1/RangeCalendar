package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import com.github.pelmenstar1.rangecalendar.CellMeasureManager
import com.github.pelmenstar1.rangecalendar.utils.lerp

internal class DefaultSelectionTransitionOrchestrator(
    private val transition: SelectionTransition,
    private val measureManager: CellMeasureManager
) : SelectionTransitionOrchestrator {
    private val lastStageIndices = IntArray(transition.groups.size)

    override fun handleTransition(fraction: Float) {
        // Run stages in each group in parallel.
        //
        // for-loop on Iterable is allocating an Iterator, which is not good, because
        // this method is running on each animation frame. Assumes that given group list supports
        // efficient random-access and use for-loop with index
        val groups = transition.groups

        for (i in groups.indices) {
            val group = groups[i]

            val stages = group.stages
            val stageCount = stages.size

            // As fraction >= 0 and stageCount >= 0, then fraction * stageCount >= 0,
            // then toInt() is acting as 'floor'.
            val stageIndexRaw = fraction * stageCount
            var stageIndex = stageIndexRaw.toInt()

            // Rescale fraction of particular stage.
            var stageFraction = stageIndexRaw - (stageIndex * stageCount).toFloat()

            if (stageIndex >= stageCount) {
                stageIndex = stageCount - 1
                stageFraction = 1f
            }

            handleTransitionStage(stages[stageIndex], stageFraction)

            val lastStageIndex = lastStageIndices[i]

            if (lastStageIndex != stageIndex) {
                if (lastStageIndex >= 0) {
                    handleTransitionStage(stages[lastStageIndex], 1f)
                }

                lastStageIndices[i] = stageIndex
                transition.setCurrentStageIndex(i, stageIndex)
            }
        }
    }

    private fun handleTransitionStage(
        stage: SelectionTransitionStage,
        fraction: Float
    ) {
        when (stage) {
            is DefaultSelectionTransitionStage.AppearAlpha -> {
                stage.alpha = fraction.reversedIf(stage.isReversed)
            }
            is DefaultSelectionTransitionStage.CellAppearBubble -> {
                bubbleAnimation(stage.shapeInfo, fraction.reversedIf(stage.isReversed), stage.bounds)
            }
            is DefaultSelectionTransitionStage.MoveCellToCell -> {
                val startShapeInfo = stage.startShape
                val endShapeInfo = stage.endShape
                val currentShapeInfo = stage.shapeInfo

                val currentLeft = lerp(startShapeInfo.startLeft, endShapeInfo.startLeft, fraction)
                val currentTop = lerp(startShapeInfo.startTop, endShapeInfo.startTop, fraction)

                val cell = measureManager.getCellAt(
                    currentLeft, currentTop,
                    relativity = CellMeasureManager.CoordinateRelativity.GRID
                )

                currentShapeInfo.apply {
                    startLeft = currentLeft
                    startTop = currentTop
                    endRight = currentLeft + currentShapeInfo.cellWidth

                    setRange(cell, cell)
                }

            }
            is DefaultSelectionTransitionStage.TransformFragmentToFragment -> {
                val newStartCellDist =
                    lerp(stage.startShapeStartCellDistance, stage.endShapeStartCellDistance, fraction)
                val newEndCellDist =
                    lerp(stage.startShapeEndCellDistance, stage.endShapeEndCellDistance, fraction)

                stage.currentStartCellDistance = newStartCellDist
                stage.currentEndCellDistance = newEndCellDist

                val newStartCell = measureManager.getCellAndPointByDistance(newStartCellDist, point)
                val (newStartCellLeft, newStartCellTop) = point

                val newEndCell = measureManager.getCellAndPointByDistance(newEndCellDist, point)
                val (newEndCellRight, newEndCellTop) = point

                stage.shapeInfo.apply {
                    startLeft = newStartCellLeft
                    startTop = newStartCellTop

                    endRight = newEndCellRight
                    endTop = newEndCellTop

                    setRange(newStartCell, newEndCell)
                }
            }
        }
    }

    companion object {
        private val point = PointF()
        private const val TAG = "DefaultSelectionTransitionOrchestrator"

        private fun Float.reversedIf(flag: Boolean) = if (flag) 1f - this else this

        private fun bubbleAnimation(
            shapeInfo: SelectionShapeInfo,
            fraction: Float,
            outRect: RectF
        ) {
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