package com.github.pelmenstar1.rangecalendar.selection

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
        val state = if (rangeStart == rangeEnd) {
            createCellState(Cell(rangeStart), measureManager)
        } else {
            createRangeState(rangeStart, rangeEnd, measureManager)
        }

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
        return when (state) {
            is DefaultSelectionState.CellState -> {
                createCellState(state.cell, measureManager)
            }

            is DefaultSelectionState.RangeState -> {
                createRangeState(state.rangeStart, state.rangeEnd, measureManager)
            }

            DefaultSelectionState.None -> DefaultSelectionState.None
        }
    }

    private fun createCellState(
        cell: Cell,
        measureManager: CellMeasureManager
    ): DefaultSelectionState.CellState {
        val left = measureManager.getCellLeft(cell)
        val top = measureManager.getCellTop(cell)

        return DefaultSelectionState.CellState(
            cell,
            left, top,
            measureManager.cellWidth, measureManager.cellHeight
        )
    }

    private fun createRangeState(
        rangeStart: Int,
        rangeEnd: Int,
        measureManager: CellMeasureManager
    ): DefaultSelectionState.RangeState {
        val cellWidth = measureManager.cellWidth
        val cellHeight = measureManager.cellHeight

        val startLeft = measureManager.getCellLeft(rangeStart)
        val startTop = measureManager.getCellTop(rangeStart)

        val endRight = measureManager.getCellLeft(rangeEnd) + cellWidth
        val endTop = measureManager.getCellTop(rangeEnd)

        val firstCellOnRowLeft = measureManager.getCellLeft(0)
        val lastCellOnRowRight = measureManager.getCellLeft(6) + cellWidth

        val range = CellRange(rangeStart, rangeEnd)

        return DefaultSelectionState.RangeState(
            range,
            startLeft, startTop,
            endRight, endTop,
            firstCellOnRowLeft, lastCellOnRowRight,
            cellWidth, cellHeight
        )
    }

    private fun setStateInternal(state: DefaultSelectionState) {
        _prevState = _currentState
        _currentState = state
    }

    override fun hasTransition(): Boolean {
        val prevState = _prevState
        val currentState = _currentState

        val prevStart = prevState.rangeStart
        val prevEnd = prevState.rangeEnd
        val currentStart = currentState.rangeStart
        val currentEnd = currentState.rangeEnd

        return false

        /*
        val prevType = _prevState.type
        val currentType = _currentState.type

        return when (prevType) {
            // There's no transition between none and none.
            SelectionType.NONE -> currentType != SelectionType.NONE

            // Cell selection has transition with every selection except custom range.
            SelectionType.CELL -> currentType != SelectionType.CUSTOM

            // Week selection doesn't have transition with month and custom range.
            SelectionType.WEEK -> currentType != SelectionType.MONTH && currentType != SelectionType.CUSTOM

            // Month selection has transition only with none and cell.
            SelectionType.MONTH -> currentType == SelectionType.NONE || currentType == SelectionType.CELL

            // Custom range selection has transition only with none.
            SelectionType.CUSTOM -> currentType == SelectionType.NONE
        }

         */
    }

    override fun createTransition(
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions
    ): SelectionState.Transitive {
        return when (val prevState = _prevState) {
            DefaultSelectionState.None -> {
                when (val currentState = _currentState) {
                    DefaultSelectionState.None -> throwTransitionUnsupported(
                        prevState,
                        currentState
                    )

                    is DefaultSelectionState.CellState -> {
                        createCellAppearTransition(currentState, options, isReversed = false)
                    }

                    /*
                    is DefaultSelectionState.WeekState -> {
                        DefaultSelectionState.WeekState.FromCenter(currentState, isReversed = false)
                    }
                     */

                    is DefaultSelectionState.RangeState -> {
                        DefaultSelectionState.RangeState.Alpha(
                            currentState,
                            isReversed = false
                        )
                    }
                }
            }

            is DefaultSelectionState.CellState -> {
                when (val currentState = _currentState) {
                    DefaultSelectionState.None -> {
                        createCellAppearTransition(prevState, options, isReversed = true)
                    }

                    is DefaultSelectionState.CellState -> {
                        val prevCell = prevState.cell
                        val currentCell = currentState.cell

                        if (prevCell.sameX(currentCell) || prevCell.sameY(currentCell)) {
                            DefaultSelectionState.CellState.MoveToCell(prevState, currentState)
                        } else {
                            when (options.cellAnimationType) {
                                CellAnimationType.ALPHA ->
                                    DefaultSelectionState.CellState.DualAlpha(
                                        prevState,
                                        currentState
                                    )

                                CellAnimationType.BUBBLE ->
                                    DefaultSelectionState.CellState.DualBubble(
                                        prevState,
                                        currentState
                                    )
                            }
                        }
                    }

                    /*
                    is DefaultSelectionState.WeekState -> {
                        createCellToWeekTransition(prevState, currentState, isReversed = false)
                    }

                    is DefaultSelectionState.MonthState -> {
                        createCellToMonthTransition(
                            prevState,
                            currentState,
                            measureManager,
                            isReversed = false
                        )
                    }
                    */

                    else -> throwTransitionUnsupported(prevState, currentState)
                }
            }

            is DefaultSelectionState.RangeState -> {
                when (val currentState = _currentState) {
                    DefaultSelectionState.None -> {
                        DefaultSelectionState.RangeState.Alpha(
                            prevState,
                            isReversed = true
                        )
                    }

                    else -> throwTransitionUnsupported(prevState, currentState)
                }
            }
        }
    }

    private fun createCellAppearTransition(
        state: DefaultSelectionState.CellState,
        options: SelectionRenderOptions,
        isReversed: Boolean
    ): SelectionState.Transitive {
        return when (options.cellAnimationType) {
            CellAnimationType.ALPHA ->
                DefaultSelectionState.CellState.AppearAlpha(state, isReversed)

            CellAnimationType.BUBBLE ->
                DefaultSelectionState.CellState.AppearBubble(state, isReversed)
        }
    }

    companion object {
        private fun throwTransitionUnsupported(
            prevState: SelectionState, currentState: SelectionState
        ): Nothing {
            throw IllegalStateException("$prevState can't be transitioned to $currentState by this manager")
        }
    }
}