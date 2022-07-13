package io.github.pelmenstar1.rangecalendar.selection

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.withSave
import io.github.pelmenstar1.rangecalendar.PackedRectF
import io.github.pelmenstar1.rangecalendar.RectangleShape
import io.github.pelmenstar1.rangecalendar.SelectionFillGradientBoundsType
import io.github.pelmenstar1.rangecalendar.SelectionType
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

                val firstCellLeft = measureManager.firstCellLeft
                val lastCellRight = measureManager.lastCellRight

                if (type == SelectionType.MONTH) {
                    DefaultSelectionState.MonthState(
                        range,
                        startLeft, startTop,
                        endRight, endTop,
                        firstCellLeft, lastCellRight
                    )
                } else {
                    DefaultSelectionState.CustomRangeState(
                        range,
                        startLeft, startTop,
                        endRight, endTop,
                        firstCellLeft, lastCellRight
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
                drawCell(canvas, prevState, options, alpha = 1f - fraction)
            }

            is DefaultSelectionState.CellState -> {
                when {
                    prevState.cell.sameY(currentState.cell) -> {
                        drawCellToCell(
                            canvas,
                            prevState, currentState,
                            xFraction = fraction, yFraction = 1f,
                            options
                        )
                    }
                    prevState.cell.sameX(currentState.cell) -> {
                        drawCellToCell(
                            canvas,
                            prevState, currentState,
                            xFraction = 1f, yFraction = fraction,
                            options
                        )
                    }
                    else -> {
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
                            drawCell(canvas, prevState, options, alpha = 1f - fraction)
                            drawCell(canvas, currentState, options, alpha = fraction)
                        }
                    }
                }
            }
            is DefaultSelectionState.WeekState -> {
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
                drawWeekSelectionFromCenter(canvas, prevState, options, 1f - fraction)
            }
            is DefaultSelectionState.CellState -> {
                if (prevState.startCell.sameY(currentState.cell)) {
                    drawRangeToRangeTransition(
                        canvas,
                        prevState.range, currentState.range,
                        measureManager, options,
                        fraction
                    )
                } else {
                    drawCellToWeekSelection(canvas, currentState, prevState, options, 1f - fraction)
                }
            }
            is DefaultSelectionState.WeekState -> {
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
                drawCustomRange(canvas, prevState, options, alpha = 1f - fraction)
            }
            is DefaultSelectionState.CellState -> {
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

        if (rangeStart.sameY(rangeEnd)) {
            drawRectOnRow(canvas, state.startLeft, state.startTop, state.endRight, options, alpha)
        } else {
            // Check if sth is updated
            if (pathRange == range && pathCellSize == cellSize && pathRoundRadius == roundRadius) {
                return
            }

            pathRange = range
            pathCellSize = cellSize
            pathRoundRadius = roundRadius

            updateCustomRangePath(
                range,
                state.firstCellLeft, state.lastCellRight,
                state.startLeft, state.startTop,
                state.endRight, state.endTop,
                options,
                preferWithoutPath = false
            )

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
            state.firstCellLeft, state.lastCellRight,
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

        if (endRange.contains(cell)) {
            drawRangeToRangeTransition(
                canvas,
                startState.range, endRange,
                measureManager, options,
                fraction
            )
        } else {
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
        measureManager.lerpCellLeft(
            startRange.start.index,
            endRange.start.index,
            fraction,
            tempPoint
        )

        val (startLeft, startTop) = tempPoint

        measureManager.lerpCellRight(startRange.end.index, endRange.end.index, fraction, tempPoint)

        val (endRight, endTop) = tempPoint

        val canDrawWithoutPath = updateCustomRangePath(
            lerpRangeToRange(startRange, endRange, fraction),
            measureManager.firstCellLeft, measureManager.lastCellRight,
            startLeft, startTop,
            endRight, endTop,
            options,
            preferWithoutPath = true
        )

        if (canDrawWithoutPath) {
            drawRectOnRow(canvas, startLeft, startTop, endRight, options)
        } else {
            options.prepareSelectionFill(pathBounds, alpha = 1f)

            path?.let { canvas.drawPath(it, paint) }
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