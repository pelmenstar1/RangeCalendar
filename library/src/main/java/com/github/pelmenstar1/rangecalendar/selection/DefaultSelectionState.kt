package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.PointF
import android.graphics.RectF
import com.github.pelmenstar1.rangecalendar.SelectionType
import com.github.pelmenstar1.rangecalendar.utils.distanceSquare
import com.github.pelmenstar1.rangecalendar.utils.lerp
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.math.min
import kotlin.math.max

internal sealed class DefaultSelectionState(
    override val type: SelectionType,
    val range: CellRange
) : SelectionState {
    override val rangeStart: Int
        get() = range.start.index

    override val rangeEnd: Int
        get() = range.end.index

    class CellState(
        val cell: Cell,
        val left: Float,
        val top: Float,
        val cellWidth: Float,
        val cellHeight: Float
    ) : DefaultSelectionState(SelectionType.CELL, CellRange.cell(cell)) {
        class AppearAlpha(val baseState: CellState, val isReversed: Boolean) : SelectionState.Transitive {
            override val start: SelectionState
                get() = baseState

            override val end: SelectionState
                get() = baseState

            var alpha = 0f

            override fun handleTransition(fraction: Float) {
                alpha = fraction.reversedIf(isReversed)
            }
        }

        class AppearBubble(val baseState: CellState, val isReversed: Boolean) : BoundsTransitionBase() {
            override val start: SelectionState
                get() = baseState

            override val end: SelectionState
                get() = baseState

            override fun handleTransition(fraction: Float) {
                val f = fraction.reversedIf(isReversed)

                bubbleAnimation(baseState, f, bounds)
            }
        }

        class DualAlpha(override val start: CellState, override val end: CellState) : SelectionState.Transitive {
            var startAlpha = Float.NaN
            var endAlpha = Float.NaN

            override fun handleTransition(fraction: Float) {
                startAlpha = 1f - fraction
                endAlpha = fraction
            }
        }

        class DualBubble(override val start: CellState, override val end: CellState) : SelectionState.Transitive {
            val startBounds = RectF()
            val endBounds = RectF()

            override fun handleTransition(fraction: Float) {
                bubbleAnimation(start, 1f - fraction, startBounds)
                bubbleAnimation(end, fraction, endBounds)
            }
        }

        class MoveToCell(override val start: CellState, override val end: CellState) : BoundsTransitionBase() {
            val cellWidth = start.cellWidth
            val cellHeight = start.cellHeight

            override fun handleTransition(fraction: Float) {
                bounds.apply {
                    left = lerp(start.left, end.left, fraction)
                    top = lerp(start.top, end.top, fraction)

                    right = left + cellWidth
                    bottom = top + cellHeight
                }
            }
        }

        class ToWeekOnRow(
            override val start: CellState,
            override val end: WeekState,
            private val isReversed: Boolean
        ) : BoundsTransitionBase() {
            val cellWidth = start.cellWidth
            val cellHeight = start.cellHeight

            init {
                bounds.apply {
                    top = start.top
                    bottom = top + cellHeight
                }
            }

            override fun handleTransition(fraction: Float) {
                val f = fraction.reversedIf(isReversed)
                val startLeft = start.left

                bounds.apply {
                    left = lerp(startLeft, end.startLeft, f)
                    right = lerp(startLeft + cellWidth, end.endRight, f)
                }
            }
        }
    }

    class WeekState(
        range: CellRange,
        val startLeft: Float,
        val top: Float,
        val endRight: Float,
        val bottom: Float
    ) : DefaultSelectionState(SelectionType.WEEK, range) {
        class FromCenter(val baseState: WeekState, val isReversed: Boolean) : BoundsTransitionBase() {
            override val start: SelectionState
                get() = baseState

            override val end: SelectionState
                get() = baseState

            private val weekCenterX: Float
            private val halfWeekWidth: Float

            init {
                val startLeft = baseState.startLeft
                val endRight = baseState.endRight

                halfWeekWidth = (endRight - startLeft) * 0.5f
                weekCenterX = startLeft + halfWeekWidth

                bounds.top = baseState.top
                bounds.bottom = baseState.bottom
            }

            override fun handleTransition(fraction: Float) {
                val f = fraction.reversedIf(isReversed)

                val dx = halfWeekWidth * f
                val cx = weekCenterX

                bounds.apply {
                    left = cx - dx
                    right = cx + dx
                }
            }
        }

        class ToWeek(
            override val start: WeekState,
            override val end: WeekState
        ) : SelectionState.Transitive {
            val startTransition = FromCenter(start, isReversed = true)
            val endTransition = FromCenter(end, isReversed = false)

            override fun handleTransition(fraction: Float) {
                startTransition.handleTransition(fraction)
                endTransition.handleTransition(fraction)
            }
        }
    }

    sealed class CustomRangeStateBase(
        type: SelectionType,
        range: CellRange,
        val startLeft: Float, val startTop: Float,
        val endRight: Float, val endTop: Float,
        val firstCellOnRowLeft: Float, val lastCellOnRowRight: Float,
        val cellWidth: Float,
        val cellHeight: Float
    ) : DefaultSelectionState(type, range) {
        class Alpha(val baseState: CustomRangeStateBase, val isReversed: Boolean) : SelectionState.Transitive {
            override val start: SelectionState
                get() = baseState

            override val end: SelectionState
                get() = baseState

            var alpha = Float.NaN

            override fun handleTransition(fraction: Float) {
                alpha = fraction.reversedIf(isReversed)
            }
        }
    }

    class MonthState(
        range: CellRange,
        startLeft: Float, startTop: Float,
        endRight: Float, endTop: Float,
        firstCellOnRowLeft: Float, lastCellOnRowRight: Float,
        cellWidth: Float, cellHeight: Float,
    ) : CustomRangeStateBase(
        SelectionType.MONTH,
        range,
        startLeft, startTop,
        endRight, endTop,
        firstCellOnRowLeft, lastCellOnRowRight,
        cellWidth, cellHeight
    )

    class CustomRangeState(
        range: CellRange,
        startLeft: Float, startTop: Float,
        endRight: Float, endTop: Float,
        firstCellOnRowLeft: Float, lastCellOnRowRight: Float,
        cellWidth: Float, cellHeight: Float,
    ) : CustomRangeStateBase(
        SelectionType.CUSTOM,
        range,
        startLeft, startTop,
        endRight, endTop,
        firstCellOnRowLeft, lastCellOnRowRight,
        cellWidth, cellHeight
    )

    object None : DefaultSelectionState(SelectionType.NONE, CellRange.Invalid)

    class CellToWeek(
        override val start: CellState,
        override val end: WeekState,
        val isReversed: Boolean
    ) : SelectionState.Transitive {
        val weekTransition = WeekState.FromCenter(end, isReversed = false)
        var cellAlpha = 0f

        override fun handleTransition(fraction: Float) {
            val f = fraction.reversedIf(isReversed)

            weekTransition.handleTransition(f)
            cellAlpha = 1f - f
        }
    }

    class CellToMonth(
        override val start: CellState,
        override val end: MonthState,
        val cx: Float, val cy: Float,
        val finalRadius: Float,
        val isReversed: Boolean
    ) : SelectionState.Transitive {
        private val startRadius = min(start.cellWidth, start.cellHeight) * 0.5f

        var radius = Float.NaN

        override fun handleTransition(fraction: Float) {
            val f = fraction.reversedIf(isReversed)

            radius = lerp(startRadius, finalRadius, f)
        }
    }

    abstract class BoundsTransitionBase : SelectionState.Transitive {
        val bounds = RectF()
    }

    companion object {
        private val tempPoint = PointF()

        private fun Float.reversedIf(flag: Boolean) = if (flag) 1f - this else this

        private fun bubbleAnimation(state: CellState, fraction: Float, outRect: RectF) {
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

        private fun lerpRangeToRange(
            startRange: CellRange,
            endRange: CellRange,
            fraction: Float
        ): CellRange {
            val interpolatedStart = lerp(
                startRange.start.index.toFloat(),
                endRange.start.index.toFloat(),
                fraction
            ).toInt()
            val interpolatedEnd = ceil(
                lerp(
                    startRange.end.index.toFloat(),
                    endRange.end.index.toFloat(),
                    fraction
                )
            ).toInt()

            return CellRange(interpolatedStart, interpolatedEnd)
        }

        fun cellToMonth(
            start: CellState,
            end: MonthState,
            measureManager: CellMeasureManager,
            isReversed: Boolean
        ): CellToMonth {
            val cx = start.left + measureManager.cellWidth * 0.5f
            val cy = start.top + measureManager.cellHeight * 0.5f

            val radius = getCircleRadiusForCellMonthAnimation(end, cx, cy, measureManager)

            return CellToMonth(start, end, cx, cy, radius, isReversed)
        }

        private fun getCircleRadiusForCellMonthAnimation(
            state: MonthState,
            x: Float, y: Float,
            measureManager: CellMeasureManager
        ): Float {
            // Find min radius for circle (with center at (x; y)) to fully fit in month selection.
            val cellHeight = state.cellHeight

            val distToLeftCorner = x - state.firstCellOnRowLeft
            val distToRightCorner = state.lastCellOnRowRight - x
            val distToTopCorner = y - measureManager.getCellLeft(0)
            val distToBottomCorner = measureManager.getCellTop(41) + cellHeight - y

            val startLeft = state.startLeft
            val startTop = state.startTop

            val endRight = state.endRight
            val endTop = state.endTop

            val distToStartCellSq = distanceSquare(startLeft, startTop, x, y)
            val distToEndCellSq = distanceSquare(endRight, endTop + cellHeight, x, y)

            var maxDist = max(distToLeftCorner, distToRightCorner)
            maxDist = max(maxDist, distToTopCorner)
            maxDist = max(maxDist, distToBottomCorner)

            // Save on expensive sqrt() call: max(sqrt(a), sqrt(b)) => sqrt(max(a, b))
            maxDist = max(maxDist, sqrt(max(distToStartCellSq, distToEndCellSq)))

            return maxDist
        }
    }
}