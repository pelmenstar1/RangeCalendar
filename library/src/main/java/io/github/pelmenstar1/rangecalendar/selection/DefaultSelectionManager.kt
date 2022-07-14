package io.github.pelmenstar1.rangecalendar.selection

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.withSave
import io.github.pelmenstar1.rangecalendar.*
import io.github.pelmenstar1.rangecalendar.PackedRectF
import io.github.pelmenstar1.rangecalendar.utils.addRoundRectCompat
import io.github.pelmenstar1.rangecalendar.utils.distanceSquare
import io.github.pelmenstar1.rangecalendar.utils.drawRoundRectCompat
import io.github.pelmenstar1.rangecalendar.utils.lerp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

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
    private var pathBounds = PackedRectF(0)
    private var pathRange = CellRange.Invalid
    private var pathCellSize = Float.NaN
    private var pathRoundRadius = Float.NaN

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
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions
    ) {
        val range = CellRange(rangeStart, rangeEnd)
        val cellSize = options.cellSize

        val state = when (type) {
            SelectionType.CELL -> {
                val left = measureManager.getCellLeft(rangeStart)
                val top = measureManager.getCellTop(rangeStart)

                DefaultSelectionState.CellState(Cell(rangeStart), left, top)
            }
            SelectionType.WEEK -> {
                val left = measureManager.getCellLeft(rangeStart)
                val right = measureManager.getCellLeft(rangeEnd) + cellSize

                val top = measureManager.getCellTop(rangeStart)

                DefaultSelectionState.WeekState(range, left, right, top)
            }
            SelectionType.MONTH, SelectionType.CUSTOM -> {
                val startLeft = measureManager.getCellLeft(rangeStart)
                val startTop = measureManager.getCellTop(rangeStart)

                val endRight = measureManager.getCellLeft(rangeEnd) + cellSize
                val endTop = measureManager.getCellTop(rangeEnd)

                val firstCellOnRowLeft = measureManager.getCellLeft(0)
                val lastCellOnRowRight = measureManager.getCellLeft(6) + cellSize

                if (type == SelectionType.MONTH) {
                    DefaultSelectionState.MonthState(
                        range,
                        startLeft, startTop,
                        endRight, endTop,
                        firstCellOnRowLeft, lastCellOnRowRight
                    )
                } else {
                    DefaultSelectionState.CustomRangeState(
                        range,
                        startLeft, startTop,
                        endRight, endTop,
                        firstCellOnRowLeft, lastCellOnRowRight
                    )
                }
            }
            SelectionType.NONE -> throw IllegalArgumentException("Type can't be NONE")
        }

        setStateInternal(state)
    }

    private fun setStateInternal(state: DefaultSelectionState) {
        _prevState = _currentState
        _currentState = state
    }

    override fun hasTransition(): Boolean {
        return if (_prevState.type == SelectionType.NONE) {
            // There's no transition between none and none.
            _currentState.type != SelectionType.NONE
        } else {
            true
        }
    }

    override fun draw(
        canvas: Canvas,
        options: SelectionRenderOptions,
        alpha: Float
    ) {
        when (val state = _currentState) {
            is DefaultSelectionState.CellState -> {
                drawCell(canvas, state, options, alpha)
            }
            is DefaultSelectionState.WeekState -> {
                drawWeekSelection(canvas, state, options, alpha)
            }
            is DefaultSelectionState.CustomRangeStateBase -> {
                drawCustomRange(canvas, state, options, alpha)
            }
            else -> {}
        }
    }

    override fun drawTransition(
        canvas: Canvas,
        measureManager: CellMeasureManager, options: SelectionRenderOptions,
        fraction: Float
    ) {
        when (_prevState) {
            is DefaultSelectionState.None -> drawFromNoneTransition(canvas, fraction, options)
            is DefaultSelectionState.CellState -> drawFromCellTransition(
                canvas,
                fraction,
                measureManager,
                options
            )
            is DefaultSelectionState.WeekState -> drawFromWeekTransition(
                canvas,
                fraction,
                measureManager,
                options
            )
            is DefaultSelectionState.MonthState -> drawFromMonthTransition(
                canvas,
                fraction,
                measureManager,
                options
            )
            is DefaultSelectionState.CustomRangeState -> drawFromCustomRangeTransition(
                canvas,
                fraction,
                measureManager,
                options
            )
        }
    }

    private fun drawFromNoneTransition(
        canvas: Canvas,
        fraction: Float,
        options: SelectionRenderOptions
    ) {
        when (val currentState = _currentState) {
            is DefaultSelectionState.CellState -> {
                drawCell(canvas, currentState, options, alpha = fraction)
            }
            is DefaultSelectionState.WeekState -> {
                drawWeekSelectionFromCenter(canvas, currentState, options, fraction)
            }
            is DefaultSelectionState.CustomRangeStateBase -> {
                drawCustomRange(canvas, currentState, options, alpha = fraction)
            }
            else -> throwTransitionUnsupported(_prevState, currentState)
        }
    }

    private fun drawFromCellTransition(
        canvas: Canvas,
        fraction: Float,
        measureManager: CellMeasureManager, options: SelectionRenderOptions
    ) {
        val prevState = _prevState as DefaultSelectionState.CellState

        when (val currentState = _currentState) {
            is DefaultSelectionState.None -> {
                // It's reversed alpha animation to fade the cell away.
                drawCell(canvas, prevState, options, alpha = 1f - fraction)
            }

            is DefaultSelectionState.CellState -> {
                when {
                    prevState.cell.sameY(currentState.cell) -> {
                        // As two cells are on the same row, they have same Y, thus there should be transition only X-axis
                        drawCellToCell(
                            canvas,
                            prevState, currentState,
                            xFraction = fraction, yFraction = 1f,
                            options
                        )
                    }
                    prevState.cell.sameX(currentState.cell) -> {
                        // As two cells are on the same column, they have same X, thus there should be transition only Y-axis
                        drawCellToCell(
                            canvas,
                            prevState, currentState,
                            xFraction = 1f, yFraction = fraction,
                            options
                        )
                    }
                    else -> {
                        // If cells are identical, it means that either options or measureManager configuration has changed
                        // and presumably we have to only move cell to the new position.
                        if (prevState.cell == currentState.cell) {
                            drawCellToCell(
                                canvas,
                                prevState,
                                currentState,
                                xFraction = fraction,
                                yFraction = fraction,
                                options
                            )
                        } else {
                            // Previous cell should fade away and current one should become gradually visible.
                            drawCell(canvas, prevState, options, alpha = 1f - fraction)
                            drawCell(canvas, currentState, options, alpha = fraction)
                        }
                    }
                }
            }
            is DefaultSelectionState.WeekState -> {
                // Checks whether previous cell is in range of current week. Based on the assumption that
                // week range fill all the row.
                if (prevState.cell.sameY(currentState.startCell)) {
                    drawRangeToRangeTransition(
                        canvas,
                        prevState.range, currentState.range,
                        measureManager, options,
                        fraction
                    )
                } else {
                    drawCellToWeekSelection(canvas, prevState, currentState, options, fraction)
                }
            }
            is DefaultSelectionState.MonthState -> {
                drawCellToMonthSelection(
                    canvas,
                    prevState.cell,
                    currentState,
                    measureManager,
                    options,
                    fraction
                )
            }
            is DefaultSelectionState.CustomRangeState -> {
                drawCellToCustomRangeTransition(
                    canvas,
                    prevState,
                    currentState,
                    measureManager, options,
                    fraction
                )
            }
        }
    }

    private fun drawFromWeekTransition(
        canvas: Canvas,
        fraction: Float,
        measureManager: CellMeasureManager, options: SelectionRenderOptions
    ) {
        val prevState = _prevState as DefaultSelectionState.WeekState

        when (val currentState = _currentState) {
            is DefaultSelectionState.None -> {
                // It's reversed alpha animation to fade the week range away.
                drawWeekSelectionFromCenter(canvas, prevState, options, 1f - fraction)
            }
            is DefaultSelectionState.CellState -> {
                // Checks whether current cell is in range of previous week. Based on the assumption that
                // week range fill all the row.
                if (prevState.startCell.sameY(currentState.cell)) {
                    drawRangeToRangeTransition(
                        canvas,
                        prevState.range, currentState.range,
                        measureManager, options,
                        fraction
                    )
                } else {
                    // Inverses cell-to-week transition.
                    drawCellToWeekSelection(canvas, currentState, prevState, options, 1f - fraction)
                }
            }
            is DefaultSelectionState.WeekState -> {
                // Previous week should fade away and in that time current week should gradually become visible.
                drawWeekSelectionFromCenter(canvas, prevState, options, 1f - fraction)
                drawWeekSelectionFromCenter(canvas, currentState, options, fraction)
            }
            is DefaultSelectionState.CustomRangeStateBase -> {
                drawRangeToRangeTransition(
                    canvas,
                    prevState.range, currentState.range,
                    measureManager, options, fraction
                )
            }
        }
    }

    private fun drawFromMonthTransition(
        canvas: Canvas,
        fraction: Float,
        measureManager: CellMeasureManager, options: SelectionRenderOptions
    ) {
        val prevState = _prevState as DefaultSelectionState.MonthState

        when (val currentState = _currentState) {
            is DefaultSelectionState.None -> {
                // It's reversed alpha animation to fade the month range away.
                drawCustomRange(canvas, prevState, options, alpha = 1f - fraction)
            }
            is DefaultSelectionState.CellState -> {
                // Inverses cell-to-month transition.
                drawCellToMonthSelection(
                    canvas,
                    currentState.cell,
                    prevState,
                    measureManager, options, 1f - fraction
                )
            }
            is DefaultSelectionState.WeekState -> {
                drawRangeToRangeTransition(
                    canvas,
                    prevState.range, currentState.range,
                    measureManager, options, fraction
                )
            }
            is DefaultSelectionState.CustomRangeStateBase -> {
                drawRangeToRangeTransition(
                    canvas,
                    prevState.range, currentState.range,
                    measureManager, options, fraction
                )
            }
        }
    }

    private fun drawFromCustomRangeTransition(
        canvas: Canvas,
        fraction: Float,
        measureManager: CellMeasureManager, options: SelectionRenderOptions
    ) {
        val prevState = _prevState as DefaultSelectionState.CustomRangeState

        when (val currentState = _currentState) {
            is DefaultSelectionState.None -> {
                // It's reversed alpha animation to fade the custom range away.
                drawCustomRange(
                    canvas,
                    prevState,
                    options,
                    alpha = 1f - fraction
                )
            }
            is DefaultSelectionState.CellState -> {
                drawCellToCustomRangeTransition(
                    canvas,
                    startState = currentState,
                    endState = prevState,
                    measureManager, options,
                    1f - fraction
                )
            }
            else -> {
                drawRangeToRangeTransition(
                    canvas,
                    prevState.range,
                    currentState.range,
                    measureManager,
                    options,
                    fraction
                )
            }
        }
    }

    private fun drawCell(
        canvas: Canvas,
        state: DefaultSelectionState.CellState,
        options: SelectionRenderOptions,
        alpha: Float = 1f
    ) {
        val cellSize = options.cellSize

        val left = state.left
        val top = state.top

        drawRectOnRow(
            canvas,
            left, top, right = left + cellSize, options, alpha
        )
    }

    private fun drawCellToCell(
        canvas: Canvas,
        start: DefaultSelectionState.CellState, end: DefaultSelectionState.CellState,
        xFraction: Float, yFraction: Float,
        options: SelectionRenderOptions,
        alpha: Float = 1f
    ) {
        val cellSize = options.cellSize

        val left = lerp(start.left, end.left, xFraction)
        val top = lerp(start.top, end.top, yFraction)

        drawRectOnRow(
            canvas, left, top, right = left + cellSize,
            options,
            alpha
        )
    }

    private fun drawCellToWeekSelection(
        c: Canvas,
        start: DefaultSelectionState.CellState,
        end: DefaultSelectionState.WeekState,
        options: SelectionRenderOptions,
        fraction: Float
    ) {
        // Previous cell should fade away and in that time, week should gradually become visible.
        drawCell(c, start, options, alpha = 1f - fraction)

        drawWeekSelectionFromCenter(c, end, options, fraction)
    }

    private fun drawWeekSelectionFromCenter(
        canvas: Canvas,
        state: DefaultSelectionState.WeekState,
        options: SelectionRenderOptions,
        fraction: Float,
        alpha: Float = 1f
    ) {
        val centerX = (state.startLeft + state.endRight) * 0.5f

        val left = lerp(centerX, state.startLeft, fraction)
        val right = lerp(centerX, state.endRight, fraction)

        drawRectOnRow(canvas, left, state.top, right, options, alpha)
    }

    private fun drawWeekSelection(
        canvas: Canvas,
        state: DefaultSelectionState.WeekState,
        options: SelectionRenderOptions,
        alpha: Float = 1f
    ) {
        val left = state.startLeft
        val right = state.endRight

        val top = state.top

        drawRectOnRow(
            canvas, left, top, right,
            options, alpha
        )
    }

    private fun drawCustomRange(
        canvas: Canvas,
        state: DefaultSelectionState.CustomRangeStateBase,
        options: SelectionRenderOptions,
        alpha: Float = 1f
    ) {
        val cellSize = options.cellSize
        val roundRadius = options.roundRadius
        val range = state.range

        val (rangeStart, rangeEnd) = range

        // If start and end of the range are on the same row, there could be applied some optimizations
        // that allow drawing the range with using Path.
        if (rangeStart.sameY(rangeEnd)) {
            drawRectOnRow(canvas, state.startLeft, state.startTop, state.endRight, options, alpha)
        } else {
            // Update Path only if some properties are changed.
            if(!(pathRange == range && pathCellSize == cellSize && pathRoundRadius == roundRadius)) {
                pathRange = range
                pathCellSize = cellSize
                pathRoundRadius = roundRadius

                updateCustomRangePath(
                    range,
                    state.firstCellOnRowLeft, state.lastCellOnRowRight,
                    state.startLeft, state.startTop,
                    state.endRight, state.endTop,
                    options,
                    preferWithoutPath = false
                )
            }

            path?.let {
                options.prepareSelectionFill(pathBounds, alpha)
                canvas.drawPath(it, paint)
            }
        }
    }

    private fun drawCellToMonthSelection(
        canvas: Canvas,
        cell: Cell,
        state: DefaultSelectionState.CustomRangeStateBase,
        measureManager: CellMeasureManager, options: SelectionRenderOptions,
        fraction: Float
    ) {
        updateCustomRangePath(
            state.range,
            state.firstCellOnRowLeft, state.lastCellOnRowRight,
            state.startLeft, state.startTop,
            state.endRight, state.endTop,
            options,
            preferWithoutPath = false
        )

        val cellLeft = measureManager.getCellLeft(cell)
        val cellTop = measureManager.getCellTop(cell)

        val halfCellSize = options.cellSize * 0.5f

        val x = cellLeft + halfCellSize
        val y = cellTop + halfCellSize

        val finalRadius = getCircleRadiusForCellMonthAnimation(state, x, y, measureManager, options)
        val currentRadius = lerp(halfCellSize, finalRadius, fraction)

        options.prepareSelectionFill(pathBounds, alpha = 1f)

        canvas.withSave {
            path?.let(::clipPath)
            drawCircle(x, y, currentRadius, paint)
        }
    }

    private fun drawRectOnRow(
        canvas: Canvas,
        left: Float, top: Float, right: Float,
        options: SelectionRenderOptions,
        alpha: Float = 1f
    ) {
        val bottom = top + options.cellSize
        options.prepareSelectionFill(left, top, right, bottom, alpha)

        canvas.drawRoundRectCompat(
            left, top, right, bottom,
            options.roundRadius, paint
        )
    }

    private fun drawCellToCustomRangeTransition(
        canvas: Canvas,
        startState: DefaultSelectionState.CellState,
        endState: DefaultSelectionState.CustomRangeState,
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions,
        fraction: Float
    ) {
        val endRange = endState.range
        val cell = startState.cell

        // Animation become not so pretty, if end range doesn't contain the cell to start from.
        if (endRange.contains(cell)) {
            drawRangeToRangeTransition(
                canvas,
                startState.range, endRange,
                measureManager, options,
                fraction
            )
        } else {
            // If the range doesn't contain the cell, then apply more simple transition:
            // the cell should fade away and the range should become visible.

            drawCustomRange(
                canvas,
                endState,
                options,
                alpha = fraction
            )

            drawCell(
                canvas,
                startState,
                options,
                alpha = 1f - fraction
            )
        }
    }

    private fun drawRangeToRangeTransition(
        canvas: Canvas,
        startRange: CellRange, endRange: CellRange,
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions,
        fraction: Float
    ) {
        // The transition works so:
        // start of startRange should gradually become start of endRange,
        // end of startRange should gradually become end of endRange.

        measureManager.lerpCellLeft(
            startRange.start.index,
            endRange.start.index,
            fraction,
            tempPoint
        )

        val (startLeft, startTop) = tempPoint

        measureManager.lerpCellRight(startRange.end.index, endRange.end.index, fraction, tempPoint)

        val (endRight, endTop) = tempPoint

        val firstCellOnRowLeft = measureManager.getCellLeft(0)

        // 6 cell is the last cell on row.
        val lastCellOnRowRight = measureManager.getCellLeft(6) + options.cellSize

        val canDrawWithoutPath = updateCustomRangePath(
            lerpRangeToRange(startRange, endRange, fraction),
            firstCellOnRowLeft, lastCellOnRowRight,
            startLeft, startTop,
            endRight, endTop,
            options,
            preferWithoutPath = true
        )

        // The range could be drawn without path only if it's located on the same row.
        if (canDrawWithoutPath) {
            drawRectOnRow(canvas, startLeft, startTop, endRight, options)
        } else {
            options.prepareSelectionFill(pathBounds, alpha = 1f)

            path?.let { canvas.drawPath(it, paint) }
        }
    }

    private inline fun SelectionRenderOptions.prepareSelectionFill(setBounds: Fill.() -> Unit, alpha: Float) {
        fill.run {
            if(fillGradientBoundsType == SelectionFillGradientBoundsType.SHAPE) {
                setBounds()
            }

            applyToPaint(paint, alpha)
        }
    }

    private fun SelectionRenderOptions.prepareSelectionFill(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        alpha: Float
    ) = prepareSelectionFill({ setBounds(left, top, right, bottom) }, alpha)

    private fun SelectionRenderOptions.prepareSelectionFill(
        bounds: PackedRectF,
        alpha: Float
    ) = prepareSelectionFill({ setBounds(bounds, RectangleShape) }, alpha)

    private fun updateCustomRangePath(
        range: CellRange,
        firstCellLeft: Float, lastCellRight: Float,
        startLeft: Float, startTop: Float,
        endRight: Float, endTop: Float,
        options: SelectionRenderOptions,
        preferWithoutPath: Boolean
    ): Boolean {
        val (start, end) = range

        val roundRadius = options.roundRadius
        val cellSize = options.cellSize

        if (start.sameY(end)) {
            if (preferWithoutPath) {
                return true
            } else {
                val bottom = startTop + cellSize

                pathBounds = PackedRectF(startLeft, startTop, endRight, bottom)

                getEmptyPath().addRoundRectCompat(
                    startLeft,
                    startTop,
                    endRight,
                    bottom,
                    roundRadius
                )
            }
        } else {
            val path = getEmptyPath()

            val startBottom = startTop + cellSize
            val endBottom = endTop + cellSize

            val gridYDiff = end.gridY - start.gridY

            pathBounds = PackedRectF(startLeft, startTop, endRight, endBottom)

            Radii.withRadius(roundRadius) {
                lt()
                rt()
                lb(start.gridX != 0)
                rb(gridYDiff == 1 && end.gridX != 6)

                path.addRoundRectCompat(
                    startLeft, startTop, lastCellRight, startBottom, radii()
                )
            }

            Radii.withRadius(roundRadius) {
                rb()
                lb()
                rt(end.gridX != 6)
                lt(gridYDiff == 1 && start.gridX != 0)

                path.addRoundRectCompat(
                    firstCellLeft,
                    if (gridYDiff == 1) startBottom else endTop,
                    endRight,
                    endBottom,
                    radii()
                )
            }

            if (gridYDiff > 1) {
                Radii.withRadius(roundRadius) {
                    lt(start.gridX != 0)
                    rb(end.gridX != 6)

                    path.addRoundRectCompat(
                        firstCellLeft,
                        startBottom,
                        lastCellRight,
                        endTop,
                        radii()
                    )
                }
            }
        }

        return false
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

    private fun getCircleRadiusForCellMonthAnimation(
        state: DefaultSelectionState.CustomRangeStateBase,
        x: Float, y: Float,
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions
    ): Float {
        // Find min radius for circle (with center at (x; y)) to fully fit in month selection.

        val cellSize = options.cellSize

        val distToLeftCorner = x - state.firstCellOnRowLeft
        val distToRightCorner = state.lastCellOnRowRight - x
        val distToTopCorner = y - measureManager.getCellLeft(0)
        val distToBottomCorner = measureManager.getCellTop(41) + cellSize - y

        val startLeft = state.startLeft
        val startTop = state.startTop

        val endRight = state.endRight
        val endTop = state.endTop

        val distToStartCellSq = distanceSquare(startLeft, startTop, x, y)
        val distToEndCellSq = distanceSquare(endRight, endTop + options.cellSize, x, y)

        var maxDist = max(distToLeftCorner, distToRightCorner)
        maxDist = max(maxDist, distToTopCorner)
        maxDist = max(maxDist, distToBottomCorner)

        // Save on expensive sqrt() call: max(sqrt(a), sqrt(b)) => sqrt(max(a, b))
        maxDist = max(maxDist, sqrt(max(distToStartCellSq, distToEndCellSq)))

        return maxDist
    }

    companion object {
        private val tempPoint = PointF()

        private fun throwTransitionUnsupported(
            prevState: SelectionState, currentState: SelectionState
        ): Nothing {
            throw IllegalStateException("${prevState.type} can't be transitioned to ${currentState.type} by this manager")
        }

        private fun lerpRangeToRange(
            startRange: CellRange,
            endRange: CellRange,
            fraction: Float
        ): CellRange {
            val interpolatedStart = lerp(
                startRange.start.index.toFloat(),
                endRange.start.index.toFloat(),
                fraction
            ).toInt()
            val interpolatedEnd = ceil(
                lerp(
                    startRange.end.index.toFloat(),
                    endRange.end.index.toFloat(),
                    fraction
                )
            ).toInt()

            return CellRange(interpolatedStart, interpolatedEnd)
        }
    }
}