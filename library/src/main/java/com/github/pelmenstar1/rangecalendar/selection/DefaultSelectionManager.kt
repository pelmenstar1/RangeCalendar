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

        val endRight = measureManager.getCellLeft(rangeEnd) + cellWidth
        val endTop = measureManager.getCellTop(rangeEnd)

        val firstCellOnRowLeft = measureManager.getCellLeft(0)
        val lastCellOnRowRight = measureManager.getCellLeft(6) + cellWidth

        val gridTop = measureManager.getCellTop(0)

        val pathInfo = SelectionShapeInfo(
            range = CellRange(rangeStart, rangeEnd),
            startLeft, startTop,
            endRight, endTop,
            firstCellOnRowLeft, lastCellOnRowRight,
            gridTop,
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
                    DefaultSelectionState.CellMoveToCell(prevState, currentState)
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
                gridTop = prevShapeInfo.gridTop

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