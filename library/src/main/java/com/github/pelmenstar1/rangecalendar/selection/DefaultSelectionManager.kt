package com.github.pelmenstar1.rangecalendar.selection

import com.github.pelmenstar1.rangecalendar.CellMeasureManager
import com.github.pelmenstar1.rangecalendar.utils.getLazyValue

internal class DefaultSelectionManager : SelectionManager {
    private var _prevState: DefaultSelectionState = DefaultSelectionState.None
    private var _currentState: DefaultSelectionState = DefaultSelectionState.None
    private var _renderer: DefaultSelectionRenderer? = null
    private var _transitionController: DefaultSelectionTransitionController? = null

    override val previousState: SelectionState
        get() = _prevState

    override val currentState: SelectionState
        get() = _currentState

    override val renderer: SelectionRenderer
        get() = getLazyValue(_renderer, ::DefaultSelectionRenderer) { _renderer = it }

    override val transitionController: SelectionTransitionController
        get() = getLazyValue(
            _transitionController,
            ::DefaultSelectionTransitionController
        ) { _transitionController = it }

    override fun setNoneState() {
        setStateInternal(DefaultSelectionState.None)
    }

    override fun setState(
        rangeStart: Int,
        rangeEnd: Int,
        measureManager: CellMeasureManager
    ) {
        setStateInternal(createRangeState(rangeStart, rangeEnd, measureManager))
    }

    override fun updateConfiguration(measureManager: CellMeasureManager) {
        updateStateConfiguration(_prevState, measureManager)
        updateStateConfiguration(_currentState, measureManager)
    }

    private fun updateStateConfiguration(state: DefaultSelectionState, measureManager: CellMeasureManager) {
        val shapeInfo = state.shapeInfo
        val (rangeStart, rangeEnd) = shapeInfo.range

        // Update measurements if the state is defined
        if (rangeStart.index <= rangeEnd.index) {
            fillSelectionShapeInfo(rangeStart.index, rangeEnd.index, measureManager, shapeInfo)
        }
    }

    private fun createStateOnConfigurationUpdate(
        state: DefaultSelectionState,
        measureManager: CellMeasureManager,
    ): DefaultSelectionState {
        val rangeStart = state.rangeStart
        val rangeEnd = state.rangeEnd

        return if (rangeStart > rangeEnd) {
            DefaultSelectionState.None
        } else {
            createRangeState(rangeStart, rangeEnd, measureManager)
        }
    }

    private fun fillSelectionShapeInfo(
        rangeStart: Int,
        rangeEnd: Int,
        measureManager: CellMeasureManager,
        outShapeInfo: SelectionShapeInfo
    ) {
        val cellWidth = measureManager.cellWidth

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

        outShapeInfo.range = CellRange(rangeStart, rangeEnd)
        outShapeInfo.startLeft = startLeft
        outShapeInfo.startTop = startTop
        outShapeInfo.endRight = endRight
        outShapeInfo.endTop = endTop
        outShapeInfo.firstCellOnRowLeft = measureManager.getCellLeft(0)
        outShapeInfo.lastCellOnRowRight = measureManager.getCellLeft(6) + cellWidth
        outShapeInfo.cellWidth = cellWidth
        outShapeInfo.cellHeight = measureManager.cellHeight
        outShapeInfo.roundRadius = measureManager.roundRadius
    }

    private fun createRangeState(
        rangeStart: Int,
        rangeEnd: Int,
        measureManager: CellMeasureManager
    ): DefaultSelectionState {
        val shapeInfo = SelectionShapeInfo()
        fillSelectionShapeInfo(rangeStart, rangeEnd, measureManager, shapeInfo)

        return DefaultSelectionState(shapeInfo)
    }

    private fun setStateInternal(state: DefaultSelectionState) {
        _prevState = _currentState
        _currentState = state
    }

    override fun createTransition(
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions
    ): SelectionState.Transitive? {
        val prevState = _prevState
        val currentState = _currentState

        val prevStart = prevState.rangeStart
        val prevEnd = prevState.rangeEnd

        val currentStart = currentState.rangeStart
        val currentEnd = currentState.rangeEnd

        return when {
            // previous state is none
            prevStart > prevEnd -> when {
                // There's no transition between none and none.
                currentStart > currentEnd -> null

                // current state is single-cell
                currentStart == currentEnd -> createCellAppearTransition(currentState, options, isReversed = false)
                else -> createRangeToRangeTransition(prevState, currentState, measureManager)
            }

            // previous state is single-cell
            prevStart == prevEnd -> when {
                // current state is none
                currentStart > currentEnd -> createCellAppearTransition(prevState, options, isReversed = true)

                // current state is single-cell
                currentStart == currentEnd -> {
                    val prevCell = Cell(prevStart)
                    val currentCell = Cell(currentStart)

                    if (prevCell.sameX(currentCell) || prevCell.sameY(currentCell)) {
                        createCellMoveToCellTransition(prevState, currentState)
                    } else {
                        createDualCellAppearTransition(prevState, currentState, options)
                    }
                }

                else -> createRangeToRangeTransition(prevState, currentState, measureManager)
            }

            else -> {
                // current state is none
                if (currentStart > currentEnd) {
                    DefaultSelectionState.AppearAlpha(prevState, isReversed = true)
                } else {
                    createRangeToRangeTransition(prevState, currentState, measureManager)
                }
            }
        }
    }

    override fun joinTransition(
        current: SelectionState.Transitive,
        end: SelectionState,
        measureManager: CellMeasureManager
    ): SelectionState.Transitive? {
        end as DefaultSelectionState

        val endStateStart = end.rangeStart
        val endStateEnd = end.rangeEnd

        return when (current) {
            is DefaultSelectionState.RangeToRange -> {
                if (endStateStart > endStateEnd) { // end state is none
                    DefaultSelectionState.AppearAlpha(current, isReversed = true)
                } else { // end state is single cell
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
                if (endStateStart > endStateEnd) { // end state is none
                    DefaultSelectionState.AppearAlpha(current, isReversed = true)
                } else {
                    val currentStateStart = current.start.shapeInfo.range.start
                    val currentStateEnd = current.end.shapeInfo.range.end

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

        val startLeft = startShapeInfo.startLeft
        val startTop = startShapeInfo.startTop
        val cellWidth = startShapeInfo.cellWidth

        val shapeInfo = SelectionShapeInfo(
            range = startShapeInfo.range,
            startLeft, startTop,
            endRight = startLeft + cellWidth, endTop = startTop,
            firstCellOnRowLeft = 0f, lastCellOnRowRight = 0f,

            cellWidth, cellHeight = startShapeInfo.cellHeight,
            roundRadius = startShapeInfo.roundRadius
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
                roundRadius = prevShapeInfo.roundRadius
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