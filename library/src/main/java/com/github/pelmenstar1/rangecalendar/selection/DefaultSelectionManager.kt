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
        val state = createRangeState(rangeStart, rangeEnd, measureManager)

        setStateInternal(state)
    }

    override fun updateConfiguration(measureManager: CellMeasureManager) {
        _prevState = createStateOnConfigurationUpdate(_prevState, measureManager)
        _currentState = createStateOnConfigurationUpdate(_currentState, measureManager)
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

    private fun createRangeState(
        rangeStart: Int,
        rangeEnd: Int,
        measureManager: CellMeasureManager
    ): DefaultSelectionState {
        val cellWidth = measureManager.cellWidth
        val cellHeight = measureManager.cellHeight

        val startLeft = measureManager.getCellLeft(rangeStart)
        val startTop = measureManager.getCellTop(rangeStart)

        val endRight: Float
        val endTop: Float

        if (rangeStart == rangeEnd) {
            endRight = startLeft + cellWidth
            endTop = startTop
        } else {
            endRight = measureManager.getCellLeft(rangeEnd) + cellWidth
            endTop = measureManager.getCellTop(rangeEnd)
        }

        val firstCellOnRowLeft = measureManager.getCellLeft(0)
        val lastCellOnRowRight = measureManager.getCellLeft(6) + cellWidth

        val pathInfo = SelectionShapeInfo(
            range = CellRange(rangeStart, rangeEnd),
            startLeft, startTop,
            endRight, endTop,
            firstCellOnRowLeft, lastCellOnRowRight,
            cellWidth, cellHeight,
            measureManager.roundRadius
        )

        return DefaultSelectionState(pathInfo)
    }

    private fun setStateInternal(state: DefaultSelectionState) {
        _prevState = _currentState
        _currentState = state
    }

    override fun hasTransition(): Boolean {
        val prevState = _prevState
        val currentState = _currentState

        return when {
            // If previous state is none, it can be transitioned to any state except none.
            prevState.range.isInvalid -> currentState.range.isValid

            else -> true
        }
    }

    override fun createTransition(
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions
    ): SelectionState.Transitive {
        val prevState = _prevState
        val currentState = _currentState

        val prevStart = prevState.rangeStart
        val prevEnd = prevState.rangeEnd

        val currentStart = currentState.rangeStart
        val currentEnd = currentState.rangeEnd

        // previous state is none.
        return if (prevStart > prevEnd) {
            // transition between none and none is forbidden.
            if (currentStart > currentEnd) {
                throw IllegalStateException("$prevState can't be transitioned to $currentState by this manager")
            }

            // current state is single-cell.
            if (currentStart == currentEnd) {
                createCellAppearTransition(currentState, options, isReversed = false)
            } else {
                createRangeToRangeTransition(prevState, currentState, measureManager)
            }
        } else if (prevStart == prevEnd) { // previous state is single-cell
            // current state is none.
            return if (currentStart > currentEnd) {
                createCellAppearTransition(prevState, options, isReversed = true)
            } else if (currentStart == currentEnd) { // current state is single-cell
                val prevCell = Cell(prevStart)
                val currentCell = Cell(currentStart)

                if (prevCell.sameX(currentCell) || prevCell.sameY(currentCell)) {
                    createCellMoveToCellTransition(prevState, currentState)
                } else {
                    when (options.cellAnimationType) {
                        CellAnimationType.ALPHA ->
                            DefaultSelectionState.DualAlpha(prevState, currentState)

                        CellAnimationType.BUBBLE ->
                            DefaultSelectionState.CellDualBubble(prevState, currentState)
                    }
                }
            } else {
                createRangeToRangeTransition(prevState, currentState, measureManager)
            }
        } else {
            // current state is none
            if (currentStart > currentEnd) {
                DefaultSelectionState.AppearAlpha(prevState, isReversed = true)
            } else {
                createRangeToRangeTransition(prevState, currentState, measureManager)
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
                } else  { // end state is single cell
                    val endStateStartCellDist = measureManager.getCellDistance(endStateStart)
                    var endStateEndCellDist = if (endStateStart == endStateEnd) {
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
                        val cellGridX = Cell(endStateStart).gridX
                        val cellGridY = Cell(endStateStart).gridY

                        var canCreateCellMoveToCell = false

                        if (currentStateStartY == currentStateEndY) {
                            if (currentStateEndY == cellGridY) {
                                canCreateCellMoveToCell = true
                            }
                        } else {
                            val currentStateStartX = currentStateStart.gridX

                            canCreateCellMoveToCell = currentStateStartX == cellGridX
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

                    if (result == null) {
                        result = DefaultSelectionState.DualAlpha(current, end)
                    }

                    result
                }
            }

            else -> return null
        }
    }

    private fun isCellMoveToCellTransitionOnRow(state: DefaultSelectionState.CellMoveToCell): Boolean {
        return state.start.range.start.sameY(state.end.range.start)
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

            val cw = measureManager.cellWidth

            val startStateStartCellDistance = measureManager.getCellDistance(prevStart.index)
            val startStateEndCellDistance = measureManager.getCellDistance(prevEnd.index) + cw

            val endStateStartCellDistance = measureManager.getCellDistance(currentStart.index)
            val endStateEndCellDistance = measureManager.getCellDistance(currentEnd.index) + cw

            val prevShapeInfo = prevState.shapeInfo

            val shapeInfo = SelectionShapeInfo().apply {
                // These are not supposed to be changed during the animation. Init them now.
                firstCellOnRowLeft = prevShapeInfo.firstCellOnRowLeft
                lastCellOnRowRight = prevShapeInfo.lastCellOnRowRight

                cellWidth = cw
                cellHeight = measureManager.cellHeight

                roundRadius = measureManager.roundRadius
            }

            DefaultSelectionState.RangeToRange(
                prevState, currentState,
                startStateStartCellDistance, startStateEndCellDistance,
                endStateStartCellDistance, endStateEndCellDistance,
                shapeInfo
            )
        } else {
            DefaultSelectionState.DualAlpha(prevState, currentState)
        }
    }
}