package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.component3
import androidx.core.graphics.component4
import com.github.pelmenstar1.rangecalendar.Fill
import com.github.pelmenstar1.rangecalendar.SelectionFillGradientBoundsType
import com.github.pelmenstar1.rangecalendar.utils.drawRoundRectCompat
import com.github.pelmenstar1.rangecalendar.utils.getLazyValue

internal class DefaultSelectionRenderer : SelectionRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val primaryShape = SelectionShape()
    private val secondaryShape = SelectionShape()

    private var primaryCellNode: CellRenderNode? = null
    private var secondaryCellNode: CellRenderNode? = null

    private fun getOrCreatePrimaryCellNode() =
        getLazyValue(primaryCellNode, ::CellRenderNode) { primaryCellNode = it }

    private fun getOrCreateSecondaryCellNode() =
        getLazyValue(secondaryCellNode, ::CellRenderNode) { secondaryCellNode = it }

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
                drawRect(canvas, state.bounds, options)
            }

            is DefaultSelectionState.CellDualBubble -> {
                drawRect(canvas, state.startBounds, options)
                drawRect(canvas, state.endBounds, options)
            }

            is DefaultSelectionState.CellMoveToCell -> {
                val left = state.left
                val top = state.top

                val shapeInfo = state.start.shapeInfo
                val width = shapeInfo.cellWidth
                val height = shapeInfo.cellHeight

                drawCell(canvas, left, top, width, height, options, alpha = 1f, isPrimary = true)
            }

            is DefaultSelectionState.RangeToRange -> {
                drawGeneralRange(canvas, state.shapeInfo, options, alpha = 1f, isPrimary = true)
            }
        }
    }

    private fun drawRange(
        canvas: Canvas,
        shapeInfo: SelectionShapeInfo,
        options: SelectionRenderOptions,
        alpha: Float,
        isPrimary: Boolean
    ) {
        val (rangeStart, rangeEnd) = shapeInfo.range

        // If start and end of the range are on the same row, there could be applied some optimizations
        // that allow drawing the range without using Path.
        if (rangeStart.sameY(rangeEnd)) {
            val left = shapeInfo.startLeft
            val top = shapeInfo.startTop
            val width = shapeInfo.endRight - left
            val height = shapeInfo.cellHeight

            if (rangeStart == rangeEnd) {
                drawCell(canvas, left, top, width, height, options, alpha, isPrimary)
            } else {
                drawRect(canvas, left, top, width, height, options, alpha)
            }
        } else {
            drawGeneralRange(canvas, shapeInfo, options, alpha, isPrimary)
        }
    }

    private fun drawRange(
        canvas: Canvas,
        state: DefaultSelectionState,
        options: SelectionRenderOptions,
        alpha: Float,
        isPrimary: Boolean
    ) {
        drawRange(canvas, state.shapeInfo, options, alpha, isPrimary)
    }

    private fun drawCell(
        canvas: Canvas,
        left: Float, top: Float,
        width: Float, height: Float,
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

            node.setSize(width, height)
            node.setRenderOptions(options)

            node.draw(canvas, left, top, alpha)
        } else {
            drawRect(canvas, left, top, width, height, options, alpha)
        }
    }

    private fun drawRect(
        canvas: Canvas,
        bounds: RectF,
        options: SelectionRenderOptions,
        alpha: Float = 1f
    ) {
        val (left, top, right, bottom) = bounds

        drawRect(canvas, left, top, right, bottom, options, alpha)
    }

    private fun drawRect(
        canvas: Canvas,
        left: Float, top: Float,
        width: Float, height: Float,
        options: SelectionRenderOptions,
        alpha: Float = 1f
    ) {
        useSelectionFill(canvas, options, left, top, width, height, alpha) {
            canvas.drawRoundRectCompat(
                0f, 0f, width, height,
                options.roundRadius, paint
            )
        }
    }

    private fun drawGeneralRange(
        canvas: Canvas,
        shapeInfo: SelectionShapeInfo,
        options: SelectionRenderOptions,
        alpha: Float,
        isPrimary: Boolean
    ) {
        val shape = if (isPrimary) {
            primaryShape
        } else {
            secondaryShape
        }

        shape.updateShapeIfNecessary(shapeInfo)

        useSelectionFill(canvas, options, shape.bounds, alpha) {
            shape.draw(canvas, paint)
        }
    }

    private inline fun useSelectionFill(
        canvas: Canvas,
        options: SelectionRenderOptions,
        left: Float, top: Float,
        width: Float, height: Float,
        alpha: Float,
        block: Canvas.() -> Unit
    ) = useSelectionFill(canvas, options, left, top, { setSize(width, height) }, alpha, block)

    private inline fun useSelectionFill(
        canvas: Canvas,
        options: SelectionRenderOptions,
        bounds: RectF,
        alpha: Float,
        block: Canvas.() -> Unit
    ) = useSelectionFill(
        canvas,
        options,
        bounds.left, bounds.top,
        { setSize(bounds.width(), bounds.height()) },
        alpha,
        block
    )

    private inline fun useSelectionFill(
        canvas: Canvas,
        options: SelectionRenderOptions,
        left: Float, top: Float,
        setSize: Fill.() -> Unit,
        alpha: Float,
        block: Canvas.() -> Unit
    ) {
        val fill = options.fill

        if (options.fillGradientBoundsType == SelectionFillGradientBoundsType.SHAPE) {
            fill.setSize()
        }

        fill.drawWith(canvas, left, top, paint, alpha, block)
    }
}