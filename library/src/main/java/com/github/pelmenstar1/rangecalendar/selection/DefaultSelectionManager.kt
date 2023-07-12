package com.github.pelmenstar1.rangecalendar.selection

import android.util.Log
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

    override fun canJoinTransitions(current: SelectionState.Transitive, end: SelectionState.Transitive): Boolean {
        return when {
            current is DefaultSelectionState.RangeToRange && end is DefaultSelectionState.RangeToRange -> true
            current is DefaultSelectionState.CellMoveToCell && end is DefaultSelectionState.CellMoveToCell -> {
                val currentStateStart = Cell(current.start.rangeStart)
                val currentStateEnd = Cell(current.end.rangeStart)

                val endStateStart = Cell(end.start.rangeStart)
                val endStateEnd = Cell(end.end.rangeStart)

                val currentStateStartY = currentStateStart.gridY
                val currentStateEndY = currentStateEnd.gridY

                val endStateStartY = endStateStart.gridY
                val endStateEndY = endStateEnd.gridY

                // We can only join cell-move-to-cell transitions if they're both moving on single row or column
                // and column/row of the current transition is the same as column/row of the end transition.
                val result = if (currentStateStartY == currentStateEndY) {
                    currentStateStartY == endStateStartY && endStateStartY == endStateEndY
                }  else {
                    currentStateStart.sameX(endStateStart)
                }

                //Log.i("DefaultSelectionManager", "canJoinTransitions: $result")

                result
            }
            else -> false
        }
    }

    override fun joinTransitions(
        current: SelectionState.Transitive,
        end: SelectionState.Transitive
    ): SelectionState.Transitive {
        return when {
            current is DefaultSelectionState.RangeToRange && end is DefaultSelectionState.RangeToRange -> {
                DefaultSelectionState.RangeToRange(
                    current, end,
                    current.currentStartCellDistance, current.currentEndCellDistance,
                    end.endStateStartCellDistance, end.endStateEndCellDistance,
                    current.shapeInfo // reuse shapeInfo from the start state.
                )
            }
            current is DefaultSelectionState.CellMoveToCell && end is DefaultSelectionState.CellMoveToCell -> {
                createCellMoveToCellTransition(current, end.end)
            }
            else -> throw RuntimeException("Unexpected joining transitions")
        }
    }

    private fun isCellMoveToCellTransitionOnX(state: DefaultSelectionState.CellMoveToCell): Boolean {
        return state.start.shapeInfo.range.start.sameY(state.end.shapeInfo.range.end)
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

        //Log.i("DefaultSelectionManager", "cmtc (create): start.left=${startShapeInfo.startLeft}")

        val shapeInfo = SelectionShapeInfo(
            range = CellRange.Invalid,
            // startLeft and startTop are changed on the first animation frame.
            startLeft = 0f, startTop = 0f,
            // endRight, endTop, firstCellOnRowLeft, lastCellOnRowRight are not used in cell-move-to-cell transition
            endRight = 0f, endTop = 0f,
            firstCellOnRowLeft = 0f, lastCellOnRowRight = 0f,
            cellWidth = startShapeInfo.cellWidth, cellHeight = startShapeInfo.cellHeight,
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