package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.component3
import androidx.core.graphics.component4
import com.github.pelmenstar1.rangecalendar.Fill
import com.github.pelmenstar1.rangecalendar.SelectionFillGradientBoundsType
import com.github.pelmenstar1.rangecalendar.utils.addRoundRectCompat
import com.github.pelmenstar1.rangecalendar.utils.drawRoundRectCompat
import com.github.pelmenstar1.rangecalendar.utils.getLazyValue

internal class DefaultSelectionRenderer : SelectionRenderer {
    private object Radii {
        private val value = FloatArray(8)
        private var currentRadius: Float = 0f

        private var flags = 0

        private const val LT_SHIFT = 0
        private const val RT_SHIFT = 1
        private const val RB_SHIFT = 2
        private const val LB_SHIFT = 3

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
            flags = 0
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

    private var primaryPath: Path? = null
    private var secondaryPath: Path? = null

    private var primaryCellNode: CellRenderNode? = null
    private var secondaryCellNode: CellRenderNode? = null

    private fun getOrCreatePrimaryCellNode() =
        getLazyValue(primaryCellNode, ::CellRenderNode) { primaryCellNode = it }

    private fun getOrCreateSecondaryCellNode() =
        getLazyValue(secondaryCellNode, ::CellRenderNode) { secondaryCellNode = it }

    private fun getOrCreatePrimaryPath() =
        getLazyValue(primaryPath, ::Path) { primaryPath = it }

    private fun getOrCreateSecondaryPath() =
        getLazyValue(secondaryPath, ::Path) { secondaryPath = it }

    override fun draw(canvas: Canvas, state: SelectionState, options: SelectionRenderOptions) {
        state as DefaultSelectionState

        drawRange(canvas, state, options, alpha = 1f, isPrimary = true)
    }

    override fun drawTransition(
        canvas: Canvas,
        state: SelectionState.Transitive,
        options: SelectionRenderOptions
    ) {
        when (state) {
            is DefaultSelectionState.AppearAlpha -> {
                drawRange(canvas, state.baseState, options, state.alpha, isPrimary = true)
            }

            is DefaultSelectionState.DualAlpha -> {
                drawRange(canvas, state.start, options, state.startAlpha, isPrimary = true)
                drawRange(canvas, state.end, options, state.endAlpha, isPrimary = false)
            }

            is DefaultSelectionState.CellAppearBubble -> {
                drawRectOnRow(canvas, state.bounds, options)
            }

            is DefaultSelectionState.CellDualBubble -> {
                drawRectOnRow(canvas, state.startBounds, options)
                drawRectOnRow(canvas, state.endBounds, options)
            }

            is DefaultSelectionState.CellMoveToCell -> {
                val (left, top, right, bottom) = state.bounds

                drawCell(canvas, left, top, right, bottom, options, alpha = 1f, isPrimary = true)
            }

            is DefaultSelectionState.RangeToRange -> {
                drawGeneralRange(canvas, state, options, alpha = 1f, isPrimary = true)
            }
        }
    }

    private fun drawCell(
        canvas: Canvas,
        left: Float, top: Float, right: Float, bottom: Float,
        options: SelectionRenderOptions,
        alpha: Float,
        isPrimary: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= 29 && canvas.isHardwareAccelerated) {
            val node = if (isPrimary) {
                getOrCreatePrimaryCellNode()
            } else {
                getOrCreateSecondaryCellNode()
            }

            val width = right - left
            val height = bottom - top

            node.setSize(width, height)
            node.setRenderOptions(options)

            node.draw(canvas, left, top, alpha)
        } else {
            drawRectOnRow(canvas, left, top, right, bottom, options, alpha)
        }
    }

    private fun drawRange(
        canvas: Canvas,
        state: DefaultSelectionState,
        options: SelectionRenderOptions,
        alpha: Float = 1f,
        isPrimary: Boolean = true
    ) {
        val (rangeStart, rangeEnd) = state.range

        // If start and end of the range are on the same row, there could be applied some optimizations
        // that allow drawing the range with using Path.
        if (rangeStart.sameY(rangeEnd)) {
            val left = state.startLeft
            val top = state.startTop
            val right = state.endRight
            val bottom = top + state.cellHeight

            if (rangeStart == rangeEnd) {
                drawCell(canvas, left, top, right, bottom, options, alpha, isPrimary)
            } else {
                drawRectOnRow(canvas, left, top, right, bottom, options, alpha)
            }
        } else {
            drawGeneralRange(canvas, state, options, alpha, isPrimary)
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

    private fun drawGeneralRange(
        canvas: Canvas,
        rangeInfo: DefaultSelectionStateRangeInfo,
        options: SelectionRenderOptions,
        alpha: Float,
        isPrimary: Boolean
    ) {
        val (start, end) = rangeInfo.range

        val startLeft = rangeInfo.startLeft
        val startTop = rangeInfo.startTop

        val endRight = rangeInfo.endRight
        val endTop = rangeInfo.endTop

        val firstCellLeft = rangeInfo.firstCellOnRowLeft
        val lastCellRight = rangeInfo.lastCellOnRowRight

        val cellHeight = rangeInfo.cellHeight
        val rr = options.roundRadius

        val startBottom = startTop + cellHeight
        val gridYDiff = end.gridY - start.gridY

        if (gridYDiff == 0) {
            useSelectionFill(canvas, options, startLeft, startTop, endRight, startBottom, alpha) {
                drawRoundRectCompat(startLeft, startTop, endRight, startBottom, rr, paint)
            }
        } else {
            val path = if (isPrimary) {
                getOrCreatePrimaryPath()
            } else {
                getOrCreateSecondaryPath()
            }

            path.rewind()

            val endBottom = endTop + cellHeight

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

            useSelectionFill(canvas, options, firstCellLeft, startTop, lastCellRight, endBottom, alpha) {
                drawPath(path, paint)
            }
        }
    }
}