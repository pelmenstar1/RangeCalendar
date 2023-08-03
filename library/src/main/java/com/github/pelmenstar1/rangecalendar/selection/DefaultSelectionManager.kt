package com.github.pelmenstar1.rangecalendar.selection

import com.github.pelmenstar1.rangecalendar.CalendarGridInfo
import com.github.pelmenstar1.rangecalendar.CellMeasureManager
import com.github.pelmenstar1.rangecalendar.GridConstants
import com.github.pelmenstar1.rangecalendar.utils.getLazyValue

internal class DefaultSelectionManager : SelectionManager {
    private var _renderer: DefaultSelectionRenderer? = null
    private var _transitionController: DefaultSelectionTransitionController? = null

    override val renderer: SelectionRenderer
        get() = getLazyValue(_renderer, ::DefaultSelectionRenderer) { _renderer = it }

    override val transitionController: SelectionTransitionController
        get() = getLazyValue(
            _transitionController,
            ::DefaultSelectionTransitionController
        ) { _transitionController = it }

    override fun createState(
        rangeStart: Int,
        rangeEnd: Int,
        measureManager: CellMeasureManager,
        gridInfo: CalendarGridInfo
    ): SelectionState {
        val shapeInfo = SelectionShapeInfo()
        fillSelectionShapeInfo(rangeStart, rangeEnd, measureManager, gridInfo, shapeInfo)

        return DefaultSelectionState(shapeInfo)
    }

    override fun updateConfiguration(
        state: SelectionState,
        measureManager: CellMeasureManager,
        gridInfo: CalendarGridInfo
    ) {
        state as DefaultSelectionState

        val shapeInfo = state.shapeInfo
        val (rangeStart, rangeEnd) = shapeInfo.range

        fillSelectionShapeInfo(rangeStart.index, rangeEnd.index, measureManager, gridInfo, shapeInfo)
    }

    private fun fillSelectionShapeInfo(
        rangeStart: Int,
        rangeEnd: Int,
        measureManager: CellMeasureManager,
        gridInfo: CalendarGridInfo,
        outShapeInfo: SelectionShapeInfo
    ) {
        val cellWidth = measureManager.cellWidth
        val cellHeight = measureManager.cellHeight

        val startLeft = measureManager.getCellLeft(rangeStart)
        val startTop = measureManager.getCellTop(rangeStart)

        var endRight: Float
        val endTop: Float

        if (rangeStart == rangeEnd) {
            endRight = startLeft
            endTop = startTop
        } else {
            endRight = measureManager.getCellLeft(rangeEnd)
            endTop = measureManager.getCellTop(rangeEnd)
        }

        endRight += cellWidth

        val range = CellRange(rangeStart, rangeEnd)

        outShapeInfo.range = CellRange(rangeStart, rangeEnd)
        outShapeInfo.startLeft = startLeft
        outShapeInfo.startTop = startTop
        outShapeInfo.endRight = endRight
        outShapeInfo.endTop = endTop
        outShapeInfo.firstCellOnRowLeft = measureManager.getCellLeft(0)
        outShapeInfo.lastCellOnRowRight = measureManager.getCellLeft(GridConstants.COLUMN_COUNT - 1) + cellWidth
        outShapeInfo.cellWidth = cellWidth
        outShapeInfo.cellHeight = cellHeight
        outShapeInfo.roundRadius = measureManager.roundRadius

        initInMonthShapeIfNecessary(outShapeInfo, gridInfo.inMonthRange, measureManager)
    }

    private fun initInMonthShapeIfNecessary(
        shapeInfo: SelectionShapeInfo,
        inMonthRange: CellRange,
        measureManager: CellMeasureManager
    ) {
        if (inMonthRange.completelyContains(shapeInfo.range)) {
            shapeInfo.useInMonthShape = false
        } else {

            var inMonthShapeInfo = shapeInfo.inMonthShapeInfo

            if (inMonthShapeInfo == null) {
                inMonthShapeInfo = SelectionShapeInfo()
                shapeInfo.inMonthShapeInfo = inMonthShapeInfo
            }

            shapeInfo.useInMonthShape = true

            val inMonthRangeStart = inMonthRange.start.index
            val inMonthRangeEnd = inMonthRange.end.index

            val cellWidth = measureManager.cellWidth

            inMonthShapeInfo.range = inMonthRange
            inMonthShapeInfo.startLeft = measureManager.getCellLeft(inMonthRangeStart)
            inMonthShapeInfo.startTop = measureManager.getCellTop(inMonthRangeStart)
            inMonthShapeInfo.endRight = measureManager.getCellLeft(inMonthRangeEnd) + cellWidth
            inMonthShapeInfo.endTop = measureManager.getCellTop(inMonthRangeEnd)
            inMonthShapeInfo.firstCellOnRowLeft = measureManager.getCellLeft(0)
            inMonthShapeInfo.lastCellOnRowRight =
                measureManager.getCellLeft(GridConstants.COLUMN_COUNT - 1) + cellWidth
            inMonthShapeInfo.cellWidth = cellWidth
            inMonthShapeInfo.cellHeight = measureManager.cellHeight
        }
    }

    override fun createTransition(
        previousState: SelectionState?,
        currentState: SelectionState?,
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions
    ): SelectionState.Transitive? {
        previousState as DefaultSelectionState?
        currentState as DefaultSelectionState?

        if (previousState == null) {
            return when {
                // There's no transition between none and none.
                currentState == null -> null

                // current state is single-cell
                currentState.rangeStart == currentState.rangeEnd ->
                    createCellAppearTransition(currentState, options, isReversed = false)

                else -> DefaultSelectionState.AppearAlpha(currentState, isReversed = false)
            }
        } else {
            val prevStart = previousState.rangeStart
            val prevEnd = previousState.rangeEnd

            // previous state is single-cell
            if (prevStart == prevEnd) {
                if (currentState == null) {
                    return createCellAppearTransition(previousState, options, isReversed = true)
                }

                val currentStart = currentState.rangeStart
                val currentEnd = currentState.rangeEnd

                // current state is single-cell
                return if (currentStart == currentEnd) {
                    val prevCell = Cell(prevStart)
                    val currentCell = Cell(currentStart)

                    if (prevCell.sameX(currentCell) || prevCell.sameY(currentCell)) {
                        createCellMoveToCellTransition(previousState, currentState)
                    } else {
                        createDualCellAppearTransition(previousState, currentState, options)
                    }
                } else {
                    createRangeToRangeTransition(previousState, currentState, measureManager)
                }
            } else {
                return if (currentState == null) {
                    DefaultSelectionState.AppearAlpha(previousState, isReversed = true)
                } else {
                    createRangeToRangeTransition(previousState, currentState, measureManager)
                }
            }
        }
    }

    override fun joinTransition(
        current: SelectionState.Transitive,
        end: SelectionState?,
        measureManager: CellMeasureManager
    ): SelectionState.Transitive? {
        end as DefaultSelectionState?

        return when (current) {
            is DefaultSelectionState.RangeToRange -> {
                // end state is none
                if (end == null) {
                    DefaultSelectionState.AppearAlpha(current, isReversed = true)
                } else {
                    val endStateStart = end.rangeStart
                    val endStateEnd = end.rangeEnd

                    val endStateStartCellDist = measureManager.getCellDistance(endStateStart)

                    var endStateEndCellDist = if (endStateStart == endStateEnd) {
                        // Do not compute cell distance of endStateEnd if we already know endStateStartCellDist
                        endStateStartCellDist
                    } else {
                        measureManager.getCellDistance(endStateEnd)
                    }

                    endStateEndCellDist += measureManager.cellWidth

                    DefaultSelectionState.RangeToRange(
                        current, end,
                        current.currentStartCellDistance, current.currentEndCellDistance,
                        endStateStartCellDist, endStateEndCellDist,
                        current.shapeInfo // reuse shapeInfo of current
                    )
                }
            }

            is DefaultSelectionState.CellMoveToCell -> {
                // end state is none
                if (end == null) {
                    DefaultSelectionState.AppearAlpha(current, isReversed = true)
                } else {
                    val currentStateStart = current.start.shapeInfo.range.start
                    val currentStateEnd = current.end.shapeInfo.range.end

                    val endStateStart = end.rangeStart
                    val endStateEnd = end.rangeEnd

                    val currentStateStartY = currentStateStart.gridY
                    val currentStateEndY = currentStateEnd.gridY

                    var result: SelectionState.Transitive? = null

                    if (endStateStart == endStateEnd) { // end state is single cell
                        // Create CellMoveToCell only if end cell is on the same row/column as current CellMoveToCell state.
                        val canCreateCellMoveToCell = if (currentStateStartY == currentStateEndY) {
                            currentStateEndY == Cell(endStateStart).gridY
                        } else {
                            currentStateStart.gridX == Cell(endStateStart).gridX
                        }

                        if (canCreateCellMoveToCell) {
                            result = DefaultSelectionState.CellMoveToCell(current, end, current.shapeInfo.clone())
                        }
                    } else {
                        val cellGridY = Cell(endStateStart).gridY

                        if (currentStateStartY == currentStateEndY && currentStateStartY == cellGridY) {
                            val currentShapeInfo = current.shapeInfo
                            val cellWidth = measureManager.cellWidth

                            val currentStateStartDist = measureManager.getCellDistanceByPoint(
                                currentShapeInfo.startLeft, currentShapeInfo.startTop
                            )
                            val currentStateEndDist = currentStateStartDist + cellWidth

                            val endStateStartCellDist = measureManager.getCellDistance(endStateStart)
                            val endStateEndCellDist = measureManager.getCellDistance(endStateEnd) + cellWidth

                            result = DefaultSelectionState.RangeToRange(
                                current, end,
                                currentStateStartDist, currentStateEndDist,
                                endStateStartCellDist, endStateEndCellDist,
                                current.shapeInfo
                            )
                        }
                    }

                    // Fallback to using dual alpha when we can't use anything else.
                    if (result == null) {
                        result = DefaultSelectionState.DualAlpha(current, end)
                    }

                    result
                }
            }

            else -> return null
        }
    }

    private fun createCellAppearTransition(
        state: DefaultSelectionState,
        options: SelectionRenderOptions,
        isReversed: Boolean
    ): SelectionState.Transitive {
        return when (options.cellAnimationType) {
            CellAnimationType.ALPHA -> DefaultSelectionState.AppearAlpha(state, isReversed)
            CellAnimationType.BUBBLE -> DefaultSelectionState.CellAppearBubble(state, isReversed)
        }
    }

    private fun createDualCellAppearTransition(
        prevState: DefaultSelectionState,
        currentState: DefaultSelectionState,
        options: SelectionRenderOptions
    ): SelectionState.Transitive {
        return when (options.cellAnimationType) {
            CellAnimationType.ALPHA -> DefaultSelectionState.DualAlpha(prevState, currentState)
            CellAnimationType.BUBBLE -> DefaultSelectionState.CellDualBubble(prevState, currentState)
        }
    }

    private fun createCellMoveToCellTransition(
        startState: SelectionShapeBasedState,
        endState: SelectionShapeBasedState
    ): DefaultSelectionState.CellMoveToCell {
        val startShapeInfo = startState.shapeInfo
        val endShapeInfo = endState.shapeInfo

        val startLeft = startShapeInfo.startLeft
        val startTop = startShapeInfo.startTop
        val cellWidth = startShapeInfo.cellWidth

        val shapeInfo = SelectionShapeInfo(
            range = startShapeInfo.range,
            startLeft, startTop,
            endRight = startLeft + cellWidth, endTop = startTop,
            firstCellOnRowLeft = 0f, lastCellOnRowRight = 0f,

            cellWidth, cellHeight = startShapeInfo.cellHeight,
            roundRadius = startShapeInfo.roundRadius,
            useInMonthShape = startShapeInfo.useInMonthShape || endShapeInfo.useInMonthShape,
            inMonthShapeInfo = startShapeInfo.inMonthShapeInfo ?: endShapeInfo.inMonthShapeInfo
        )

        return DefaultSelectionState.CellMoveToCell(startState, endState, shapeInfo)
    }

    private fun createRangeToRangeTransition(
        prevState: DefaultSelectionState,
        currentState: DefaultSelectionState,
        measureManager: CellMeasureManager
    ): SelectionState.Transitive {
        val prevRange = prevState.range
        val currentRange = currentState.range

        return if (prevRange.hasIntersectionWith(currentRange)) {
            val (prevStart, prevEnd) = prevRange
            val (currentStart, currentEnd) = currentRange

            val prevShapeInfo = prevState.shapeInfo
            val currentShapeInfo = currentState.shapeInfo
            val cw = prevShapeInfo.cellWidth

            // Do not compute the cell distance if we already know one.
            val startStateStartCellDist = measureManager.getCellDistance(prevStart.index)
            var startStateEndCellDist = if (prevStart.index == prevEnd.index) {
                startStateStartCellDist
            } else {
                measureManager.getCellDistance(prevEnd.index)
            }

            // end cell distance should point to the right side of the cell
            startStateEndCellDist += cw

            val endStateStartCellDist = if (currentStart.index == prevStart.index) {
                startStateStartCellDist
            } else {
                measureManager.getCellDistance(currentStart.index)
            }

            val endStateEndCellDist = if (currentEnd.index == prevEnd.index) {
                startStateEndCellDist
            } else {
                measureManager.getCellDistance(currentEnd.index) + cw
            }

            val shapeInfo = SelectionShapeInfo(
                // range, startLeft, startTop, endRight, endTop are changed on the first transition frame.
                // Do not init them.
                range = CellRange.Invalid,
                startLeft = 0f, startTop = 0f,
                endRight = 0f, endTop = 0f,
                firstCellOnRowLeft = prevShapeInfo.firstCellOnRowLeft,
                lastCellOnRowRight = prevShapeInfo.lastCellOnRowRight,
                cellWidth = cw, cellHeight = prevShapeInfo.cellHeight,
                roundRadius = prevShapeInfo.roundRadius,
                useInMonthShape = prevShapeInfo.useInMonthShape || currentShapeInfo.useInMonthShape,
                inMonthShapeInfo = prevShapeInfo.inMonthShapeInfo ?: currentShapeInfo.inMonthShapeInfo
            )

            DefaultSelectionState.RangeToRange(
                prevState, currentState,
                startStateStartCellDist, startStateEndCellDist,
                endStateStartCellDist, endStateEndCellDist,
                shapeInfo
            )
        } else {
            DefaultSelectionState.DualAlpha(prevState, currentState)
        }
    }
}