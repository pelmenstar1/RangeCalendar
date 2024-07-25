package com.github.pelmenstar1.rangecalendar.selection

import android.util.Log
import com.github.pelmenstar1.rangecalendar.CalendarGridInfo
import com.github.pelmenstar1.rangecalendar.CellMeasureManager
import com.github.pelmenstar1.rangecalendar.GridConstants
import com.github.pelmenstar1.rangecalendar.complexRange.cell.CellComplexRange
import com.github.pelmenstar1.rangecalendar.complexRange.cell.CellFragment
import com.github.pelmenstar1.rangecalendar.complexRange.cell.CellFragmentProximityDetector
import com.github.pelmenstar1.rangecalendar.complexRange.cell.transition.CellComplexRangeTransitionManager
import com.github.pelmenstar1.rangecalendar.complexRange.cell.transition.CellTransitionOperation
import com.github.pelmenstar1.rangecalendar.utils.getLazyValue

internal class DefaultSelectionManager : SelectionManager {
    private var _renderer: DefaultSelectionRenderer? = null
    private var _transitionController: DefaultSelectionTransitionOrchestrator? = null
    private val cellTransitionManager = CellComplexRangeTransitionManager(
        CellFragmentProximityDetector.neverMove()
    )

    override val renderer: SelectionRenderer
        get() = getLazyValue(_renderer, ::DefaultSelectionRenderer) { _renderer = it }


    override fun createState(
        complexRange: CellComplexRange,
        measureManager: CellMeasureManager,
        gridInfo: CalendarGridInfo
    ): SelectionState {
        val selFragmentList = ArrayList<SelectionFragmentState>()

        complexRange.forEachFragment { start, endInclusive ->
            selFragmentList.add(createFragmentState(start, endInclusive, measureManager, gridInfo))
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

    private fun createFragmentShapeInfo(
        start: Int, endInclusive: Int,
        measureManager: CellMeasureManager,
        gridInfo: CalendarGridInfo
    ): SelectionShapeInfo {
        return SelectionShapeInfo().also {
            fillSelectionShapeInfo(start, endInclusive, measureManager, gridInfo, it)
        }
    }

    private fun createFragmentState(
        start: Int, endInclusive: Int,
        measureManager: CellMeasureManager,
        gridInfo: CalendarGridInfo
    ): DefaultSelectionFragmentState {
        return DefaultSelectionFragmentState(
            createFragmentShapeInfo(start, endInclusive, measureManager, gridInfo)
        )
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
        outShapeInfo.lastCellOnRowRight =
            measureManager.getCellLeft(GridConstants.COLUMN_COUNT - 1) + cellWidth
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

    override fun createTransitionOrchestrator(
        transition: SelectionTransition,
        measureManager: CellMeasureManager
    ): SelectionTransitionOrchestrator {
        return DefaultSelectionTransitionOrchestrator(transition, measureManager)
    }

    override fun createTransition(
        previousState: SelectionState?,
        currentState: SelectionState?,
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions,
        gridInfo: CalendarGridInfo
    ): SelectionTransition? {
        previousState as DefaultSelectionState?
        currentState as DefaultSelectionState?

        val prevComplexRange = getComplexRangeOrEmpty(previousState)
        val currentComplexRange = getComplexRangeOrEmpty(currentState)

        val transitionData =
            cellTransitionManager.createTransition(prevComplexRange, currentComplexRange)

        Log.i("DefaultSelectionManager", "prevComplexRange: $prevComplexRange; currentComplexRange: $currentComplexRange; transitionData: $transitionData")
        if (transitionData.groups.isEmpty()) {
            return null
        }

        val groups = ArrayList<SelectionTransitionGroup>(transitionData.groups.size)

        for (group in transitionData.groups) {
            val stages = ArrayList<SelectionTransitionStage>(group.operations.size)

            for (op in group.operations) {
                stages.add(
                    createTransitionStageFromOperation(
                        op,
                        previousState, currentState,
                        measureManager,
                        options,
                        gridInfo
                    )
                )
            }

            groups.add(SelectionTransitionGroup(stages))
        }


        return SelectionTransition(groups)
    }

    private fun createTransitionStageFromOperation(
        op: CellTransitionOperation,
        previousState: DefaultSelectionState?,
        currentState: DefaultSelectionState?,
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions,
        gridInfo: CalendarGridInfo
    ): SelectionTransitionStage {
        return when (op) {
            is CellTransitionOperation.Insert -> {
                createInsertRemoveTransition(
                    currentState,
                    op.fragment,
                    measureManager, options, gridInfo,
                    isReversed = false
                )
            }

            is CellTransitionOperation.Remove -> {
                createInsertRemoveTransition(
                    currentState,
                    op.fragment,
                    measureManager, options, gridInfo,
                    isReversed = true
                )
            }

            is CellTransitionOperation.Transform -> {
                val originShape = getOrCreateFragmentShapeInfo(
                    previousState,
                    op.origin,
                    measureManager,
                    gridInfo
                )
                val destShape = getOrCreateFragmentShapeInfo(
                    currentState,
                    op.destination,
                    measureManager,
                    gridInfo
                )

                createTransformFragmentToFragmentTransition(originShape, destShape, measureManager)
            }

            is CellTransitionOperation.Join -> {
                val originFragments = op.origin.fragments()

                val originShapes = getOrCreateFragmentShapeInfoArray(
                    previousState,
                    originFragments,
                    measureManager, gridInfo
                )
                val destinationShape = getOrCreateFragmentShapeInfo(
                    currentState,
                    op.destination,
                    measureManager, gridInfo
                )
                val currentShapes = createShapeArray(originFragments.size)

                DefaultSelectionTransitionStage.Join(originShapes, destinationShape, currentShapes)
            }

            is CellTransitionOperation.Split -> {
                val destFragments = op.destination.fragments()

                val originShape = getOrCreateFragmentShapeInfo(
                    currentState,
                    op.origin,
                    measureManager, gridInfo
                )
                val destShapes = getOrCreateFragmentShapeInfoArray(
                    previousState,
                    destFragments,
                    measureManager, gridInfo
                )
                val currentShapes = createShapeArray(destFragments.size)

                DefaultSelectionTransitionStage.Split(originShape, destShapes, currentShapes)
            }

            is CellTransitionOperation.NoOp -> {
                val shapeInfoArray = getOrCreateFragmentShapeInfoArray(
                    currentState,
                    op.fragments,
                    measureManager, gridInfo
                )

                DefaultSelectionTransitionStage.NoOp(shapeInfoArray)
            }
        }
    }

    override fun joinTransition(
        current: SelectionTransition,
        end: SelectionState?,
        measureManager: CellMeasureManager
    ): SelectionTransition? {
        // TODO: Implement it
        return null
    }

    private fun createInsertRemoveTransition(
        currentState: DefaultSelectionState?,
        fragment: CellFragment,
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions,
        gridInfo: CalendarGridInfo,
        isReversed: Boolean
    ): SelectionTransitionStage {
        val shapeInfo = getOrCreateFragmentShapeInfo(
            currentState,
            fragment,
            measureManager,
            gridInfo
        )

        return if (fragment.isSingleCell) {
            createCellAppearTransition(shapeInfo, options, isReversed)
        } else {
            DefaultSelectionTransitionStage.AppearAlpha(shapeInfo, isReversed)
        }
    }

    private fun createCellAppearTransition(
        shapeInfo: SelectionShapeInfo,
        options: SelectionRenderOptions,
        isReversed: Boolean
    ): SelectionTransitionStage {
        return when (options.cellAnimationType) {
            CellAnimationType.ALPHA -> DefaultSelectionTransitionStage.AppearAlpha(
                shapeInfo,
                isReversed
            )

            CellAnimationType.BUBBLE -> DefaultSelectionTransitionStage.CellAppearBubble(
                shapeInfo,
                isReversed
            )
        }
    }

    private fun createCellMoveToCellTransition(
        startShapeInfo: SelectionShapeInfo,
        endShapeInfo: SelectionShapeInfo
    ): DefaultSelectionTransitionStage {
        val startLeft = startShapeInfo.startLeft
        val startTop = startShapeInfo.startTop
        val cellWidth = startShapeInfo.cellWidth

        val shapeInfo = SelectionShapeInfo(
            rangeStart = startShapeInfo.rangeStart, rangeEnd = startShapeInfo.rangeEnd,
            startLeft, startTop,
            endRight = startLeft + cellWidth, endTop = startTop,
            firstCellOnRowLeft = 0f, lastCellOnRowRight = 0f,

            cellWidth, cellHeight = startShapeInfo.cellHeight,
            roundRadius = startShapeInfo.roundRadius,
            useInMonthShape = startShapeInfo.useInMonthShape || endShapeInfo.useInMonthShape,
            inMonthShapeInfo = startShapeInfo.inMonthShapeInfo ?: endShapeInfo.inMonthShapeInfo
        )

        return DefaultSelectionTransitionStage.MoveCellToCell(
            startShapeInfo,
            endShapeInfo,
            shapeInfo
        )
    }

    private fun createTransformFragmentToFragmentTransition(
        prevShape: SelectionShapeInfo,
        currentShape: SelectionShapeInfo,
        measureManager: CellMeasureManager
    ): DefaultSelectionTransitionStage {
        val prevStart = prevShape.rangeStart
        val prevEnd = prevShape.rangeEnd

        val currentStart = currentShape.rangeStart
        val currentEnd = currentShape.rangeEnd

        val cw = prevShape.cellWidth

        val startStateStartCellDist = measureManager.getCellDistance(prevStart)

        // Do not compute the cell distance if we already know one.
        var startStateEndCellDist = if (prevStart == prevEnd) {
            startStateStartCellDist
        } else {
            measureManager.getCellDistance(prevEnd)
        }

        // end cell distance should point to the right side of the cell
        startStateEndCellDist += cw

        val endStateStartCellDist = if (currentStart == prevStart) {
            startStateStartCellDist
        } else {
            measureManager.getCellDistance(currentStart)
        }

        val endStateEndCellDist = if (currentEnd == prevEnd) {
            startStateEndCellDist
        } else {
            measureManager.getCellDistance(currentEnd) + cw
        }

        val shapeInfo = SelectionShapeInfo(
            // range, startLeft, startTop, endRight, endTop are changed on the first transition frame.
            // Do not init them.
            rangeStart = 0, rangeEnd = 0,
            startLeft = 0f, startTop = 0f,
            endRight = 0f, endTop = 0f,
            firstCellOnRowLeft = prevShape.firstCellOnRowLeft,
            lastCellOnRowRight = prevShape.lastCellOnRowRight,
            cellWidth = cw, cellHeight = prevShape.cellHeight,
            roundRadius = prevShape.roundRadius,
            useInMonthShape = prevShape.useInMonthShape || currentShape.useInMonthShape,
            inMonthShapeInfo = prevShape.inMonthShapeInfo ?: currentShape.inMonthShapeInfo
        )

        return DefaultSelectionTransitionStage.TransformFragmentToFragment(
            prevShape, currentShape,
            startStateStartCellDist, startStateEndCellDist,
            endStateStartCellDist, endStateEndCellDist,
            shapeInfo
        )
    }

    private fun getComplexRangeOrEmpty(state: SelectionState?): CellComplexRange {
        return state?.complexRange ?: CellComplexRange.Empty
    }

    private fun getOrCreateFragmentShapeInfo(
        selectionState: DefaultSelectionState?,
        targetFragment: CellFragment,
        measureManager: CellMeasureManager,
        gridInfo: CalendarGridInfo
    ): SelectionShapeInfo {
        val rangeStart = targetFragment.start
        val rangeEnd = targetFragment.endInclusive

        if (selectionState != null) {
            val fragments = selectionState.fragments

            for (fragmentState in fragments) {
                if (fragmentState.rangeStart == rangeStart && fragmentState.rangeEnd == rangeEnd) {
                    return (fragmentState as DefaultSelectionFragmentState).shapeInfo
                }
            }
        }

        return createFragmentShapeInfo(rangeStart, rangeEnd, measureManager, gridInfo)
    }

    // result is always filled with non-null values
    @Suppress("UNCHECKED_CAST")
    private fun getOrCreateFragmentShapeInfoArray(
        selectionState: DefaultSelectionState?,
        fragments: List<CellFragment>,
        measureManager: CellMeasureManager,
        gridInfo: CalendarGridInfo
    ): Array<SelectionShapeInfo> {
        val result = arrayOfNulls<SelectionShapeInfo>(fragments.size)

        for (i in fragments.indices) {
            val fragment = fragments[i]

            result[i] = getOrCreateFragmentShapeInfo(
                selectionState, fragment, measureManager, gridInfo)
        }

        return result as Array<SelectionShapeInfo>
    }

    private fun createShapeArray(size: Int): Array<SelectionShapeInfo> {
        return Array(size) { SelectionShapeInfo() }
    }

}