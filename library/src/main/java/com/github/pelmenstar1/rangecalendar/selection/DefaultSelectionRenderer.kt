package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.component3
import androidx.core.graphics.component4
import androidx.core.graphics.withSave
import com.github.pelmenstar1.rangecalendar.Fill
import com.github.pelmenstar1.rangecalendar.SelectionFillGradientBoundsType
import com.github.pelmenstar1.rangecalendar.utils.addRoundRectCompat
import com.github.pelmenstar1.rangecalendar.utils.drawRoundRectCompat

internal class DefaultSelectionRenderer : SelectionRenderer {
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

    override fun draw(canvas: Canvas, state: SelectionState, options: SelectionRenderOptions) {
        when (state) {
            is DefaultSelectionState.CellState -> drawCell(canvas, state, options)
            is DefaultSelectionState.WeekState -> drawWeekSelection(canvas, state, options)
            is DefaultSelectionState.CustomRangeStateBase -> drawCustomRange(canvas, state, options)
            else -> {}
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
        val left = state.left
        val top = state.top

        drawRectOnRow(
            canvas,
            left, top, right = left + state.cellWidth, bottom = top + state.cellHeight,
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
                state.endRight, bottom = top + state.cellHeight,
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

        val cellHeight = state.cellHeight
        val rr = options.roundRadius

        val startBottom = startTop + cellHeight
        val gridYDiff = end.gridY - start.gridY

        if (gridYDiff == 0) {
            pathBounds.set(startLeft, startTop, endRight, startBottom)

            path.addRoundRect(pathBounds, rr, rr, Path.Direction.CW)
        } else {
            val endBottom = endTop + cellHeight

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
}