package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.*
import androidx.core.graphics.*
import com.github.pelmenstar1.rangecalendar.*
import com.github.pelmenstar1.rangecalendar.utils.addRoundRectCompat
import com.github.pelmenstar1.rangecalendar.utils.drawRoundRectCompat

internal class DefaultSelectionManager : SelectionManager {
    private var _prevState: DefaultSelectionState = DefaultSelectionState.None
    private var _currentState: DefaultSelectionState = DefaultSelectionState.None

    override val previousState: SelectionState
        get() = _prevState

    override val currentState: SelectionState
        get() = _currentState

    override fun setNoneState() {
        setStateInternal(DefaultSelectionState.None)
    }

    override fun createRenderer(): SelectionRenderer = DefaultSelectionRenderer()

    override fun setState(
        type: SelectionType,
        rangeStart: Int,
        rangeEnd: Int,
        measureManager: CellMeasureManager
    ) {
        val state = when (type) {
            SelectionType.CELL -> createCellState(Cell(rangeStart), measureManager)
            SelectionType.WEEK -> createWeekState(rangeStart, rangeEnd, measureManager)
            SelectionType.MONTH, SelectionType.CUSTOM -> {
                createCustomRangeBaseState(type, rangeStart, rangeEnd, measureManager)
            }
            SelectionType.NONE -> throw IllegalArgumentException("Type can't be NONE")
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
            is DefaultSelectionState.WeekState -> {
                createWeekState(state.rangeStart, state.rangeEnd, measureManager)
            }
            is DefaultSelectionState.CustomRangeStateBase -> {
                val selType =
                    if (state is DefaultSelectionState.MonthState) SelectionType.MONTH else SelectionType.CUSTOM

                createCustomRangeBaseState(selType, state.rangeStart, state.rangeEnd, measureManager)
            }
            DefaultSelectionState.None -> DefaultSelectionState.None
        }
    }

    private fun createCellState(cell: Cell, measureManager: CellMeasureManager): DefaultSelectionState.CellState {
        val left = measureManager.getCellLeft(cell)
        val top = measureManager.getCellTop(cell)

        return DefaultSelectionState.CellState(cell, left, top, measureManager.cellWidth, measureManager.cellHeight)
    }

    private fun createWeekState(
        rangeStart: Int,
        rangeEnd: Int,
        measureManager: CellMeasureManager
    ): DefaultSelectionState.WeekState {
        val left = measureManager.getCellLeft(rangeStart)
        val right = measureManager.getCellRight(rangeEnd)

        val top = measureManager.getCellTop(rangeStart)

        return DefaultSelectionState.WeekState(
            CellRange(rangeStart, rangeEnd),
            left, top, right, bottom = top + measureManager.cellHeight
        )
    }

    private fun createCustomRangeBaseState(
        type: SelectionType,
        rangeStart: Int,
        rangeEnd: Int,
        measureManager: CellMeasureManager
    ): DefaultSelectionState.CustomRangeStateBase {
        val cellWidth = measureManager.cellWidth
        val cellHeight = measureManager.cellHeight

        val startLeft = measureManager.getCellLeft(rangeStart)
        val startTop = measureManager.getCellTop(rangeStart)

        val endRight = measureManager.getCellLeft(rangeEnd) + cellWidth
        val endTop = measureManager.getCellTop(rangeEnd)

        val firstCellOnRowLeft = measureManager.getCellLeft(0)
        val lastCellOnRowRight = measureManager.getCellLeft(6) + cellWidth

        val range = CellRange(rangeStart, rangeEnd)

        return if (type == SelectionType.MONTH) {
            DefaultSelectionState.MonthState(
                range,
                startLeft, startTop,
                endRight, endTop,
                firstCellOnRowLeft, lastCellOnRowRight,
                cellWidth, cellHeight
            )
        } else {
            DefaultSelectionState.CustomRangeState(
                range,
                startLeft, startTop,
                endRight, endTop,
                firstCellOnRowLeft, lastCellOnRowRight,
                cellWidth, cellHeight
            )
        }
    }

    private fun setStateInternal(state: DefaultSelectionState) {
        _prevState = _currentState
        _currentState = state
    }

    override fun hasTransition(): Boolean {
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
    }

    override fun createTransition(
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions
    ): SelectionState.Transitive {
        return when (val prevState = _prevState) {
            DefaultSelectionState.None -> {
                when (val currentState = _currentState) {
                    DefaultSelectionState.None -> throwTransitionUnsupported(prevState, currentState)
                    is DefaultSelectionState.CellState -> {
                        createCellAppearTransition(currentState, options, isReversed = false)
                    }
                    is DefaultSelectionState.WeekState -> {
                        DefaultSelectionState.WeekState.FromCenter(currentState, isReversed = false)
                    }
                    is DefaultSelectionState.CustomRangeStateBase -> {
                        DefaultSelectionState.CustomRangeStateBase.Alpha(currentState, isReversed = false)
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
                                    DefaultSelectionState.CellState.DualAlpha(prevState, currentState)

                                CellAnimationType.BUBBLE ->
                                    DefaultSelectionState.CellState.DualBubble(prevState, currentState)
                            }
                        }
                    }
                    is DefaultSelectionState.WeekState -> {
                        createCellToWeekTransition(prevState, currentState, isReversed = false)
                    }
                    is DefaultSelectionState.MonthState -> {
                        DefaultSelectionState.cellToMonth(prevState, currentState, measureManager, isReversed = false)
                    }
                    else -> throwTransitionUnsupported(prevState, currentState)
                }
            }
            is DefaultSelectionState.WeekState -> {
                when (val currentState = _currentState) {
                    DefaultSelectionState.None -> {
                        DefaultSelectionState.WeekState.FromCenter(prevState, isReversed = true)
                    }
                    is DefaultSelectionState.CellState -> {
                        createCellToWeekTransition(currentState, prevState, isReversed = true)
                    }
                    is DefaultSelectionState.WeekState -> {
                        DefaultSelectionState.WeekState.ToWeek(prevState, currentState)
                    }
                    else -> throwTransitionUnsupported(prevState, currentState)
                }
            }
            is DefaultSelectionState.MonthState -> {
                when (val currentState = _currentState) {
                    DefaultSelectionState.None -> {
                        DefaultSelectionState.CustomRangeStateBase.Alpha(prevState, isReversed = true)
                    }
                    is DefaultSelectionState.CellState -> {
                        DefaultSelectionState.cellToMonth(currentState, prevState, measureManager, isReversed = true)
                    }
                    else -> throwTransitionUnsupported(prevState, currentState)
                }
            }
            is DefaultSelectionState.CustomRangeState -> {
                when (val currentState = _currentState) {
                    DefaultSelectionState.None -> {
                        DefaultSelectionState.CustomRangeStateBase.Alpha(prevState, isReversed = true)
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
            CellAnimationType.ALPHA -> DefaultSelectionState.CellState.AppearAlpha(state, isReversed)
            CellAnimationType.BUBBLE -> DefaultSelectionState.CellState.AppearBubble(state, isReversed)
        }
    }

    private fun createCellToWeekTransition(
        startState: DefaultSelectionState.CellState,
        endState: DefaultSelectionState.WeekState,
        isReversed: Boolean
    ): SelectionState.Transitive {
        return if (startState.cell.sameY(endState.range.start)) {
            DefaultSelectionState.CellState.ToWeekOnRow(startState, endState, isReversed)
        } else {
            DefaultSelectionState.CellToWeek(startState, endState, isReversed)
        }
    }

    companion object {
        private val tempPoint = PointF()

        private fun throwTransitionUnsupported(
            prevState: SelectionState, currentState: SelectionState
        ): Nothing {
            throw IllegalStateException("${prevState.type} can't be transitioned to ${currentState.type} by this manager")
        }
    }
}