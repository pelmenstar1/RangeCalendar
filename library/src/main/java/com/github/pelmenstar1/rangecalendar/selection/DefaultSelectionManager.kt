package com.github.pelmenstar1.rangecalendar.selection

import com.github.pelmenstar1.rangecalendar.CalendarGridInfo
import com.github.pelmenstar1.rangecalendar.CellMeasureManager
import com.github.pelmenstar1.rangecalendar.GridConstants
import com.github.pelmenstar1.rangecalendar.complexRange.cell.CellComplexRange
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
        complexRange: CellComplexRange,
        measureManager: CellMeasureManager,
        gridInfo: CalendarGridInfo
    ): SelectionState {
        val selFragmentList = ArrayList<SelectionFragmentState>()

        complexRange.forEachFragment { start, endInclusive ->
            val shapeInfo = SelectionShapeInfo()
            fillSelectionShapeInfo(start, endInclusive, measureManager, gridInfo, shapeInfo)

            val selFragment = DefaultSelectionFragmentState(shapeInfo)
            selFragmentList.add(selFragment)
        }

        return DefaultSelectionState(selFragmentList, complexRange)
    }

    override fun updateConfiguration(
        state: SelectionState,
        measureManager: CellMeasureManager,
        gridInfo: CalendarGridInfo
    ) {
        for (fragment in state.fragments) {
            fragment as DefaultSelectionFragmentState

            val shapeInfo = fragment.shapeInfo

            fillSelectionShapeInfo(
                shapeInfo.rangeStart, shapeInfo.rangeEnd,
                measureManager,
                gridInfo,
                shapeInfo
            )
        }
    }

    private fun fillSelectionShapeInfo(
        rangeStart: Int, rangeEnd: Int,
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

        outShapeInfo.rangeStart = rangeStart
        outShapeInfo.rangeEnd = rangeEnd
        outShapeInfo.startLeft = startLeft
        outShapeInfo.startTop = startTop
        outShapeInfo.endRight = endRight
        outShapeInfo.endTop = endTop
        outShapeInfo.firstCellOnRowLeft = measureManager.getCellLeft(0)
        outShapeInfo.lastCellOnRowRight = measureManager.getCellLeft(GridConstants.COLUMN_COUNT - 1) + cellWidth
        outShapeInfo.cellWidth = cellWidth
        outShapeInfo.cellHeight = cellHeight
        outShapeInfo.roundRadius = measureManager.roundRadius

        initInMonthShapeIfNecessary(
            outShapeInfo,
            gridInfo.inMonthRangeStart, gridInfo.inMonthRangeEnd,
            measureManager
        )
    }

    private fun initInMonthShapeIfNecessary(
        shapeInfo: SelectionShapeInfo,
        inMonthRangeStart: Int,
        inMonthRangeEnd: Int,
        measureManager: CellMeasureManager
    ) {
        if (shapeInfo.rangeStart >= inMonthRangeStart && shapeInfo.rangeEnd <= inMonthRangeEnd) {
            shapeInfo.useInMonthShape = false
        } else {
            var inMonthShapeInfo = shapeInfo.inMonthShapeInfo

            if (inMonthShapeInfo == null) {
                inMonthShapeInfo = SelectionShapeInfo()
                shapeInfo.inMonthShapeInfo = inMonthShapeInfo
            }

            shapeInfo.useInMonthShape = true

            val cellWidth = measureManager.cellWidth

            inMonthShapeInfo.rangeStart = inMonthRangeStart
            inMonthShapeInfo.rangeStart = inMonthRangeEnd
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
    ): SelectionTransition? {
        // TODO: Implement it
        return null
    }

    override fun joinTransition(
        current: SelectionTransition,
        end: SelectionState?,
        measureManager: CellMeasureManager
    ): SelectionTransition? {
        // TODO: Implement it
        return null
    }
}