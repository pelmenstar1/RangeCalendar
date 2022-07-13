package io.github.pelmenstar1.rangecalendar.selection

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.core.graphics.withSave
import io.github.pelmenstar1.rangecalendar.PackedRectF
import io.github.pelmenstar1.rangecalendar.RectangleShape
import io.github.pelmenstar1.rangecalendar.SelectionFillGradientBoundsType
import io.github.pelmenstar1.rangecalendar.SelectionType
import io.github.pelmenstar1.rangecalendar.utils.addRoundRectCompat
import io.github.pelmenstar1.rangecalendar.utils.distanceSquare
import io.github.pelmenstar1.rangecalendar.utils.drawRoundRectCompat
import io.github.pelmenstar1.rangecalendar.utils.lerp
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
            // Set bit at 'shift' position.
            flags = flags or (1 shl shift)
        }

        private fun setFlag(shift: Int, condition: Boolean) {
            // Set bit at 'shift' position if 'condition' is true, otherwise do nothing.
            flags = flags or ((if (condition) 1 else 0) shl shift)
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
            // Creates conditional select mask.
            // If bit at 'shift' position is set, all bits set in mask, otherwise it's 0
            // Steps:
            // 1. Right-shift flags by 'shift' value to have the bit at LSB position.
            // 2. Then when we have 0 or 1 value, multiply it by -0x1 to get all bits set if the bit is set
            // and 0 if isn't.
            // (-0x1 in Kotlin because Int is signed and 0xFFFFFFFF fits on in Long,
            // so -0x1 is the way to get all bits set in 32-bit signed int).

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
            // createMask(shift) creates mask which is all bits set mask if bit at 'shift' position is set,
            // and 0 if isn't.
            // So, we need to binary AND bits of radius float to get the same value if the bit is set,
            // and 0 if isn't.
            //
            // Then we convert it back to float and init 'value' array.
            //
            // (0.0f in bit representation is 0, so Float.intBitsToFloat(0) = 0.0f).
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

                DefaultSelectionState.MonthState(
                    range,
                    startLeft, startTop,
                    endRight, endTop,
                    measureManager.firstCellLeft, measureManager.lastCellRight
                )
            }
            SelectionType.NONE -> throw IllegalArgumentException("Type can't be NONE")
        }

        setStateInternal(state)
    }

    private fun setStateInternal(state: DefaultSelectionState) {
        _prevState = _currentState
        _currentState = state
    }

    override fun hasTransition() = when (_prevState.type) {
        SelectionType.NONE -> _currentState.type != SelectionType.NONE && _currentState.type != SelectionType.CUSTOM
        SelectionType.CELL -> _currentState.type != SelectionType.CUSTOM
        SelectionType.WEEK -> _currentState.type != SelectionType.CUSTOM && _currentState.type != SelectionType.MONTH
        SelectionType.MONTH -> _currentState.type == SelectionType.NONE || _currentState.type == SelectionType.CELL

        else -> false
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
                drawCustomRangePath(canvas, state, options, alpha)
            }

            else -> {}
        }
    }

    override fun drawTransition(
        canvas: Canvas,
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions,
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

            else -> throwTransitionUnsupported(_prevState, _currentState)
        }
    }

    private fun drawFromNoneTransition(
        canvas: Canvas,
        fraction: Float,
        options: SelectionRenderOptions
    ) {
        when (val curState = _currentState) {
            is DefaultSelectionState.CellState -> {
                drawCell(canvas, curState, options, alpha = fraction)
            }
            is DefaultSelectionState.WeekState -> {
                drawWeekSelectionFromCenter(canvas, curState, options, fraction)
            }
            is DefaultSelectionState.MonthState -> {
                drawCustomRangePath(canvas, curState, options, alpha = fraction)
            }
            else -> throwTransitionUnsupported(_prevState, curState)
        }
    }

    private fun drawFromCellTransition(
        canvas: Canvas,
        fraction: Float,
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions
    ) {
        val prevState = _prevState as DefaultSelectionState.CellState

        when (val curState = _currentState) {
            is DefaultSelectionState.None -> {
                drawCell(canvas, prevState, options, alpha = 1f - fraction)
            }

            is DefaultSelectionState.CellState -> {
                when {
                    prevState.cell.sameY(curState.cell) -> {
                        drawCellToCell(
                            canvas,
                            prevState, curState,
                            xFraction = fraction, yFraction = 1f,
                            options
                        )
                    }
                    prevState.cell.sameX(curState.cell) -> {
                        drawCellToCell(
                            canvas,
                            prevState, curState,
                            xFraction = 1f, yFraction = fraction,
                            options
                        )
                    }
                    else -> {
                        if (prevState.cell == curState.cell) {
                            drawCellToCell(
                                canvas,
                                prevState,
                                curState,
                                xFraction = fraction,
                                yFraction = fraction,
                                options
                            )
                        } else {
                            drawCell(canvas, prevState, options, alpha = 1f - fraction)
                            drawCell(canvas, curState, options, alpha = fraction)
                        }
                    }
                }
            }
            is DefaultSelectionState.WeekState -> {
                if (prevState.cell.sameY(curState.startCell)) {
                    drawCellToWeekSelectionOnRow(
                        canvas,
                        prevState,
                        curState,
                        measureManager,
                        options,
                        fraction
                    )
                } else {
                    drawCellToWeekSelection(canvas, prevState, curState, options, fraction)
                }
            }
            is DefaultSelectionState.MonthState -> {
                drawCellToMonthSelection(
                    canvas,
                    prevState.cell,
                    curState,
                    measureManager,
                    options,
                    fraction
                )
            }
            else -> throwTransitionUnsupported(prevState, curState)
        }
    }

    private fun drawFromWeekTransition(
        canvas: Canvas,
        fraction: Float,
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions
    ) {
        val prevState = _prevState as DefaultSelectionState.WeekState

        when (val curState = _currentState) {
            is DefaultSelectionState.None -> {
                drawWeekSelectionFromCenter(canvas, prevState, options, 1f - fraction)
            }
            is DefaultSelectionState.CellState -> {
                if (prevState.startCell.sameY(curState.cell)) {
                    drawCellToWeekSelectionOnRow(
                        canvas,
                        curState,
                        prevState,
                        measureManager,
                        options,
                        1f - fraction
                    )
                } else {
                    drawCellToWeekSelection(canvas, curState, prevState, options, 1f - fraction)
                }
            }
            is DefaultSelectionState.WeekState -> {
                drawWeekSelectionFromCenter(canvas, prevState, options, 1f - fraction)
                drawWeekSelectionFromCenter(canvas, curState, options, fraction)
            }
            else -> throwTransitionUnsupported(prevState, curState)
        }
    }

    private fun drawFromMonthTransition(
        canvas: Canvas,
        fraction: Float,
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions
    ) {
        val prevState = _prevState as DefaultSelectionState.MonthState

        when (val curState = _currentState) {
            is DefaultSelectionState.None -> {
                drawCustomRangePath(canvas, prevState, options, alpha = 1f - fraction)
            }
            is DefaultSelectionState.CellState -> {
                drawCellToMonthSelection(
                    canvas,
                    curState.cell,
                    prevState,
                    measureManager,
                    options,
                    1f - fraction
                )
            }
            else -> throwTransitionUnsupported(prevState, curState)
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
        val right = left + cellSize
        val bottom = top + cellSize

        options.prepareSelectionFill(left, top, right, bottom, alpha)
        canvas.drawRoundRectCompat(
            left, top, right, bottom,
            options.roundRadius, paint
        )
    }

    private fun drawCellToCell(
        canvas: Canvas,
        start: DefaultSelectionState.CellState,
        end: DefaultSelectionState.CellState,
        xFraction: Float, yFraction: Float,
        options: SelectionRenderOptions,
        alpha: Float = 1f
    ) {
        val cellSize = options.cellSize

        val left = lerp(start.left, end.left, xFraction)
        val top = lerp(start.top, end.top, yFraction)

        val right = left + cellSize
        val bottom = top + cellSize

        options.prepareSelectionFill(left, top, right, bottom, alpha)

        canvas.drawRoundRectCompat(
            left, top, right, bottom,
            options.roundRadius, paint
        )
    }

    private fun drawCellToWeekSelection(
        c: Canvas,
        start: DefaultSelectionState.CellState,
        end: DefaultSelectionState.WeekState,
        options: SelectionRenderOptions,
        fraction: Float
    ) {
        drawCell(c, start, options, alpha = 1f - fraction)

        drawWeekSelectionFromCenter(c, end, options, fraction)
    }

    private fun drawCellToWeekSelectionOnRow(
        canvas: Canvas,
        start: DefaultSelectionState.CellState,
        end: DefaultSelectionState.WeekState,
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions,
        fraction: Float,
        alpha: Float = 1f
    ) {
        val cell = start.cell
        val leftAnchor = measureManager.getCellLeft(cell)
        val rightAnchor = leftAnchor + options.cellSize

        drawWeekSelectionAnchor(canvas, end, leftAnchor, rightAnchor, options, fraction, alpha)
    }

    private fun drawWeekSelectionAnchor(
        canvas: Canvas,
        state: DefaultSelectionState.WeekState,
        leftAnchor: Float, rightAnchor: Float,
        options: SelectionRenderOptions,
        fraction: Float,
        alpha: Float = 1f
    ) {
        val top = state.top
        val bottom = top + options.cellSize

        val left = lerp(leftAnchor, state.startLeft, fraction)
        val right = lerp(rightAnchor, state.endRight, fraction)

        options.prepareSelectionFill(left, top, right, bottom, alpha)

        canvas.drawRoundRectCompat(
            left, top, right, bottom,
            options.roundRadius, paint
        )
    }

    private fun drawWeekSelectionFromCenter(
        canvas: Canvas,
        state: DefaultSelectionState.WeekState,
        options: SelectionRenderOptions,
        fraction: Float,
        alpha: Float = 1f
    ) {
        val anchor = (state.startLeft + state.endRight) * 0.5f

        drawWeekSelectionAnchor(canvas, state, anchor, anchor, options, fraction, alpha)
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
        val bottom = top + options.cellSize

        options.prepareSelectionFill(left, top, right, bottom, alpha)
        canvas.drawRoundRectCompat(
            left, top, right, bottom,
            options.roundRadius, paint
        )
    }

    private fun drawCustomRangePath(
        canvas: Canvas,
        state: DefaultSelectionState.CustomRangeStateBase,
        options: SelectionRenderOptions,
        alpha: Float = 1f
    ) {
        updateCustomRangePath(state, options)

        path?.let {
            options.prepareSelectionFill(pathBounds, alpha)
            canvas.drawPath(it, paint)
        }
    }

    private fun drawCellToMonthSelection(
        canvas: Canvas,
        cell: Cell,
        state: DefaultSelectionState.CustomRangeStateBase,
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions,
        fraction: Float
    ) {
        updateCustomRangePath(state, options)

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

    private fun SelectionRenderOptions.prepareSelectionFill(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        alpha: Float
    ) {
        fill.run {
            if (fillGradientBoundsType == SelectionFillGradientBoundsType.SHAPE) {
                setBounds(left, top, right, bottom)
            }

            applyToPaint(paint, alpha)
        }
    }

    private fun SelectionRenderOptions.prepareSelectionFill(bounds: PackedRectF, alpha: Float) {
        fill.run {
            if (fillGradientBoundsType == SelectionFillGradientBoundsType.SHAPE) {
                setBounds(bounds, RectangleShape)
            }

            applyToPaint(paint, alpha)
        }
    }

    private fun updateCustomRangePath(
        state: DefaultSelectionState.CustomRangeStateBase,
        options: SelectionRenderOptions
    ) {
        val cellSize = options.cellSize
        val roundRadius = options.roundRadius
        val range = state.range

        // Check if sth is updated
        if (pathRange == range && pathCellSize == cellSize && pathRoundRadius == roundRadius) {
            return
        }

        pathRange = range
        pathCellSize = cellSize
        pathRoundRadius = roundRadius

        var path = path

        if (path == null) {
            path = Path()
            this.path = path
        } else {
            path.rewind()
        }

        val (start, end) = range

        if (start.sameY(end)) {
            val left = state.startLeft
            val top = state.startTop

            val right = state.endRight
            val bottom = top + cellSize

            pathBounds = PackedRectF(left, top, right, bottom)

            path.addRoundRectCompat(left, top, right, bottom, roundRadius)
        } else {
            val firstCellOnRowLeft = state.firstCellLeft
            val lastCellOnRowRight = state.lastCellRight

            val startLeft = state.startLeft
            val startTop = state.startTop
            val startBottom = startTop + cellSize

            val endRight = state.endRight
            val endTop = state.endTop
            val endBottom = endTop + cellSize

            val gridYDiff = end.gridY - start.gridY

            pathBounds = PackedRectF(startLeft, startTop, endRight, endBottom)

            Radii.withRadius(roundRadius) {
                lt()
                rt()
                lb(start.gridX != 0)
                rb(gridYDiff == 1 && end.gridX != 6)

                path.addRoundRectCompat(
                    startLeft, startTop, lastCellOnRowRight, startBottom, radii()
                )
            }

            Radii.withRadius(roundRadius) {
                rb()
                lb()
                rt(end.gridX != 6)
                lt(gridYDiff == 1 && start.gridX != 0)

                path.addRoundRectCompat(
                    firstCellOnRowLeft,
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
                        firstCellOnRowLeft,
                        startBottom,
                        lastCellOnRowRight,
                        endTop,
                        radii()
                    )
                }
            }
        }
    }

    private fun getCircleRadiusForCellMonthAnimation(
        state: DefaultSelectionState.CustomRangeStateBase,
        x: Float, y: Float,
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions
    ): Float {
        // Find max radius for circle (with center at center of cell) to fully fit in month selection.
        // Try distance to left, top, right, bottom corners.
        // Then try distance to start and end cells.

        val distToLeftCorner = x - state.firstCellLeft
        val distToRightCorner = state.lastCellRight - x
        val distToTopCorner = y - measureManager.firstCellTop
        val distToBottomCorner = measureManager.lastCellBottom - y

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
        private fun throwTransitionUnsupported(
            prevState: SelectionState,
            curState: SelectionState
        ): Nothing {
            throw IllegalStateException("${prevState.type} can't be transitioned to ${curState.type} by this renderer")
        }
    }
}