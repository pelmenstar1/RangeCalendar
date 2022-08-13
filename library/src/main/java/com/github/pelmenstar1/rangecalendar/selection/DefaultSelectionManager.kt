package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.*
import androidx.core.graphics.*
import com.github.pelmenstar1.rangecalendar.*
import com.github.pelmenstar1.rangecalendar.utils.addRoundRectCompat
import com.github.pelmenstar1.rangecalendar.utils.drawRoundRectCompat

internal class DefaultSelectionManager : SelectionManager {
    private object Radii {
        private val value = FloatArray(8)
        private var currentRadius: Float = 0f

        private var flags = 0

        private const val LT_SHIFT = 0
        private const val RT_SHIFT = 1
        private const val RB_SHIFT = 2
        private const val LB_SHIFT = 3

        fun clear() {
            flags = 0
        }

        private fun setFlag(shift: Int) {
            flags = flags or (1 shl shift)
        }

        private fun setFlag(shift: Int, condition: Boolean) {
            val bit = if (condition) 1 else 0

            flags = flags or (bit shl shift)
        }

        // left top
        fun lt() = setFlag(LT_SHIFT)
        fun lt(condition: Boolean) = setFlag(LT_SHIFT, condition)

        // right top
        fun rt() = setFlag(RT_SHIFT)
        fun rt(condition: Boolean) = setFlag(RT_SHIFT, condition)

        // right bottom
        fun rb() = setFlag(RB_SHIFT)
        fun rb(condition: Boolean) = setFlag(RB_SHIFT, condition)

        // left bottom
        fun lb() = setFlag(LB_SHIFT)
        fun lb(condition: Boolean) = setFlag(LB_SHIFT, condition)

        private fun createMask(shift: Int): Int {
            // If the bit at 'shift' position is set, returns 'all bits set' mask, otherwise 0
            // (flags shr shift and 1) part extracts state of bit at 'shift' position. It can be either 1 or 0.
            // Then it's multiplied by -0x1
            // (as signed integers are used, it's the only way to represent all bits set in decimal notation)
            // to get the mask. If the bit is 1, then -1 * 1 = -1 (all bits set). If it's 0, then -1 * 0 = 0
            return -0x1 * (flags shr shift and 1)
        }

        inline fun withRadius(radius: Float, block: Radii.() -> Unit) {
            clear()

            currentRadius = radius
            block(this)
        }

        fun radii(): FloatArray {
            val radiusBits = currentRadius.toBits()

            initCorner(radiusBits, 0, LT_SHIFT)
            initCorner(radiusBits, 2, RT_SHIFT)
            initCorner(radiusBits, 4, RB_SHIFT)
            initCorner(radiusBits, 6, LB_SHIFT)

            return value
        }

        private fun initCorner(radiusBits: Int, offset: Int, shift: Int) {
            // This is works, because 0.0f in binary representation is 0, so Float.fromBits(0) == 0.0f
            val cornerRadius = Float.fromBits(radiusBits and createMask(shift))

            value[offset] = cornerRadius
            value[offset + 1] = cornerRadius
        }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var path: Path? = null
    private val pathBounds = RectF()

    private var _prevState: DefaultSelectionState = DefaultSelectionState.None
    private var _currentState: DefaultSelectionState = DefaultSelectionState.None

    override val previousState: SelectionState
        get() = _prevState

    override val currentState: SelectionState
        get() = _currentState

    override fun setNoneState() {
        setStateInternal(DefaultSelectionState.None)
    }

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

        return DefaultSelectionState.CellState(cell, left, top, measureManager.cellSize)
    }

    private fun createWeekState(
        rangeStart: Int,
        rangeEnd: Int,
        measureManager: CellMeasureManager
    ): DefaultSelectionState.WeekState {
        val cellSize = measureManager.cellSize

        val left = measureManager.getCellLeft(rangeStart)
        val right = measureManager.getCellLeft(rangeEnd) + cellSize

        val top = measureManager.getCellTop(rangeStart)

        return DefaultSelectionState.WeekState(
            CellRange(rangeStart, rangeEnd),
            left, top, right, bottom = top + cellSize
        )
    }

    private fun createCustomRangeBaseState(
        type: SelectionType,
        rangeStart: Int,
        rangeEnd: Int,
        measureManager: CellMeasureManager
    ): DefaultSelectionState.CustomRangeStateBase {
        val cellSize = measureManager.cellSize

        val startLeft = measureManager.getCellLeft(rangeStart)
        val startTop = measureManager.getCellTop(rangeStart)

        val endRight = measureManager.getCellLeft(rangeEnd) + cellSize
        val endTop = measureManager.getCellTop(rangeEnd)

        val firstCellOnRowLeft = measureManager.getCellLeft(0)
        val lastCellOnRowRight = measureManager.getCellLeft(6) + cellSize

        val range = CellRange(rangeStart, rangeEnd)

        return if (type == SelectionType.MONTH) {
            DefaultSelectionState.MonthState(
                range,
                startLeft, startTop,
                endRight, endTop,
                firstCellOnRowLeft, lastCellOnRowRight,
                cellSize
            )
        } else {
            DefaultSelectionState.CustomRangeState(
                range,
                startLeft, startTop,
                endRight, endTop,
                firstCellOnRowLeft, lastCellOnRowRight,
                cellSize
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

    override fun draw(
        canvas: Canvas,
        options: SelectionRenderOptions
    ) {
        when (val state = _currentState) {
            is DefaultSelectionState.CellState -> drawCell(canvas, state, options)
            is DefaultSelectionState.WeekState -> drawWeekSelection(canvas, state, options)
            is DefaultSelectionState.CustomRangeStateBase -> drawCustomRange(canvas, state, options)
            else -> {}
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

    override fun drawTransition(canvas: Canvas, state: SelectionState.Transitive, options: SelectionRenderOptions) {
        val isHandled = drawCellAppearTransition(canvas, state, options)
        if (isHandled) {
            return
        }

        when (state) {
            is DefaultSelectionState.CellState.DualAlpha -> {
                drawCell(canvas, state.start, options, state.startAlpha)
                drawCell(canvas, state.end, options, state.endAlpha)
            }
            is DefaultSelectionState.CellState.DualBubble -> {
                drawRectOnRow(canvas, state.startBounds, options)
                drawRectOnRow(canvas, state.endBounds, options)
            }

            is DefaultSelectionState.BoundsTransitionBase -> {
                drawRectOnRow(canvas, state.bounds, options)
            }

            is DefaultSelectionState.CellToWeek -> {
                drawRectOnRow(canvas, state.weekTransition.bounds, options)
                drawCell(canvas, state.start, options, state.cellAlpha)
            }

            is DefaultSelectionState.CellToMonth -> {
                updateCustomRangePath(state.end, options)

                useSelectionFill(canvas, options, pathBounds, alpha = 1f) {
                    canvas.withSave {
                        path?.let(::clipPath)
                        drawCircle(state.cx, state.cy, state.radius, paint)
                    }
                }
            }

            is DefaultSelectionState.WeekState.ToWeek -> {
                drawRectOnRow(canvas, state.startTransition.bounds, options)
                drawRectOnRow(canvas, state.endTransition.bounds, options)
            }

            is DefaultSelectionState.CustomRangeStateBase.Alpha -> {
                drawCustomRange(canvas, state.baseState, options, state.alpha)
            }
        }
    }

    private fun drawCellAppearTransition(
        canvas: Canvas,
        state: SelectionState.Transitive,
        options: SelectionRenderOptions
    ): Boolean {
        return when (state) {
            is DefaultSelectionState.CellState.AppearAlpha -> {
                drawCell(canvas, state.baseState, options, state.alpha)

                true
            }
            is DefaultSelectionState.CellState.AppearBubble -> {
                drawRectOnRow(canvas, state.bounds, options)

                true
            }

            else -> false
        }
    }

    private fun drawCell(
        canvas: Canvas,
        state: DefaultSelectionState.CellState,
        options: SelectionRenderOptions,
        alpha: Float = 1f
    ) {
        val cellSize = state.cellSize
        val left = state.left
        val top = state.top

        drawRectOnRow(
            canvas,
            left, top, right = left + cellSize, bottom = top + cellSize,
            options, alpha
        )
    }

    private fun drawWeekSelection(
        canvas: Canvas,
        state: DefaultSelectionState.WeekState,
        options: SelectionRenderOptions,
    ) {
        drawRectOnRow(
            canvas,
            state.startLeft, state.top,
            state.endRight, state.bottom,
            options,
            alpha = 1f
        )
    }

    private fun drawCustomRange(
        canvas: Canvas,
        state: DefaultSelectionState.CustomRangeStateBase,
        options: SelectionRenderOptions,
        alpha: Float = 1f
    ) {
        val (rangeStart, rangeEnd) = state.range

        // If start and end of the range are on the same row, there could be applied some optimizations
        // that allow drawing the range with using Path.
        if (rangeStart.sameY(rangeEnd)) {
            val top = state.startTop

            drawRectOnRow(
                canvas,
                state.startLeft, state.startTop,
                state.endRight, bottom = top + state.cellSize,
                options, alpha
            )
        } else {
            updateCustomRangePath(state, options)

            useSelectionFill(canvas, options, pathBounds, alpha) {
                path?.let { drawPath(it, paint) }
            }
        }
    }

    private fun drawRectOnRow(
        canvas: Canvas,
        bounds: RectF,
        options: SelectionRenderOptions,
        alpha: Float = 1f
    ) {
        val (left, top, right, bottom) = bounds

        drawRectOnRow(canvas, left, top, right, bottom, options, alpha)
    }

    private fun drawRectOnRow(
        canvas: Canvas,
        left: Float, top: Float, right: Float, bottom: Float,
        options: SelectionRenderOptions,
        alpha: Float = 1f
    ) {
        useSelectionFill(canvas, options, left, top, right, bottom, alpha) {
            drawRoundRectCompat(
                left, top, right, bottom,
                options.roundRadius, paint
            )
        }
    }

    private inline fun useSelectionFill(
        canvas: Canvas,
        options: SelectionRenderOptions,
        setBounds: Fill.() -> Unit,
        alpha: Float,
        block: Canvas.() -> Unit
    ) {
        val fill = options.fill

        if (options.fillGradientBoundsType == SelectionFillGradientBoundsType.SHAPE) {
            fill.setBounds()
        }

        fill.drawWith(canvas, paint, alpha, block)
    }

    private inline fun useSelectionFill(
        canvas: Canvas,
        options: SelectionRenderOptions,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        alpha: Float,
        block: Canvas.() -> Unit
    ) = useSelectionFill(canvas, options, { setBounds(left, top, right, bottom) }, alpha, block)

    private inline fun useSelectionFill(
        canvas: Canvas,
        options: SelectionRenderOptions,
        bounds: RectF,
        alpha: Float,
        block: Canvas.() -> Unit
    ) = useSelectionFill(canvas, options, { setBounds(bounds) }, alpha, block)

    private fun updateCustomRangePath(
        state: DefaultSelectionState.CustomRangeStateBase,
        options: SelectionRenderOptions
    ) {
        val path = getEmptyPath()

        val (start, end) = state.range
        val startLeft = state.startLeft
        val startTop = state.startTop

        val endRight = state.endRight
        val endTop = state.endTop

        val firstCellLeft = state.firstCellOnRowLeft
        val lastCellRight = state.lastCellOnRowRight

        val cellSize = state.cellSize
        val rr = options.roundRadius

        val startBottom = startTop + cellSize
        val gridYDiff = end.gridY - start.gridY

        if (gridYDiff == 0) {
            pathBounds.set(startLeft, startTop, endRight, startBottom)

            path.addRoundRect(pathBounds, rr, rr, Path.Direction.CW)
        } else {
            val endBottom = endTop + cellSize

            pathBounds.set(firstCellLeft, startTop, lastCellRight, endBottom)

            Radii.withRadius(rr) {
                lt()
                rt()
                lb(start.gridX != 0)
                rb(gridYDiff == 1 && end.gridX != 6)

                path.addRoundRectCompat(startLeft, startTop, lastCellRight, startBottom, radii())
            }

            Radii.withRadius(rr) {
                rb()
                lb()
                rt(end.gridX != 6)
                lt(gridYDiff == 1 && start.gridX != 0)

                path.addRoundRectCompat(
                    firstCellLeft, if (gridYDiff == 1) startBottom else endTop,
                    endRight, endBottom,
                    radii()
                )
            }

            if (gridYDiff > 1) {
                Radii.withRadius(rr) {
                    lt(start.gridX != 0)
                    rb(end.gridX != 6)

                    path.addRoundRectCompat(
                        firstCellLeft, startBottom,
                        lastCellRight, endTop,
                        radii()
                    )
                }
            }
        }
    }

    private fun getEmptyPath(): Path {
        var path = path

        if (path == null) {
            path = Path()
            this.path = path
        } else {
            path.rewind()
        }

        return path
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