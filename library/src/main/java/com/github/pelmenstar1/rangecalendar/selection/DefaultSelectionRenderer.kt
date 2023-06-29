package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.component3
import androidx.core.graphics.component4
import com.github.pelmenstar1.rangecalendar.SelectionFillGradientBoundsType
import com.github.pelmenstar1.rangecalendar.utils.drawRoundRectCompat
import com.github.pelmenstar1.rangecalendar.utils.getLazyValue

private typealias CanvasWithBoundsLambda = Canvas.(left: Float, top: Float, right: Float, bottom: Float) -> Unit

internal class DefaultSelectionRenderer : SelectionRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val primaryShape = SelectionShape()
    private val secondaryShape = SelectionShape()

    private var primaryCellNode: CellRenderNode? = null
    private var secondaryCellNode: CellRenderNode? = null

    private val tempRect = RectF()

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
        useSelectionFill(canvas, options, left, top, width, height, alpha) { l, t, r, b ->
            canvas.drawRoundRectCompat(l, t, r, b, options.roundRadius, paint)
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

        val boundsType = options.fillGradientBoundsType

        val origin = if (options.fillGradientBoundsType == SelectionFillGradientBoundsType.GRID) {
            SelectionShape.ORIGIN_LOCAL
        } else {
            SelectionShape.ORIGIN_BOUNDS
        }

        shape.update(shapeInfo, origin)

        val fill = options.fill

        val bounds = shape.bounds
        val actualBounds: RectF

        var count = -1

        if (boundsType == SelectionFillGradientBoundsType.SHAPE) {
            val (left, top, right, bottom) = bounds
            val width = right - left
            val height = bottom - top

            fill.setSize(width, height)

            count = canvas.save()
            canvas.translate(left, top)

            actualBounds = tempRect.apply {
                set(0f, 0f, width, height)
            }
        } else {
            actualBounds = bounds
        }

        try {
            fill.drawWith(canvas, actualBounds, paint, alpha) {
                shape.draw(canvas, paint)
            }
        } finally {
            if (count >= 0) {
                canvas.restoreToCount(count)
            }
        }
    }

    private inline fun useSelectionFill(
        canvas: Canvas,
        options: SelectionRenderOptions,
        left: Float, top: Float, width: Float, height: Float,
        alpha: Float,
        crossinline block: CanvasWithBoundsLambda
    ) {
        val fill = options.fill
        val boundsType = options.fillGradientBoundsType
        val rect = tempRect

        val right = left + width
        val bottom = top + height

        var count = -1

        if (boundsType == SelectionFillGradientBoundsType.SHAPE) {
            fill.setSize(width, height)

            count = canvas.save()
            canvas.translate(left, top)

            rect.set(0f, 0f, width, height)
        } else {
            rect.set(left, top, right, bottom)
        }

        try {
            fill.drawWith(canvas, rect, paint, alpha) {
                val l: Float
                val t: Float
                val r: Float
                val b: Float

                if (boundsType == SelectionFillGradientBoundsType.SHAPE) {
                    l = 0f
                    t = 0f
                    r = width
                    b = height
                } else {
                    l = left
                    t = top
                    r = right
                    b = bottom
                }

                block(this, l, t, r, b)
            }
        } finally {
            if (count >= 0) {
                canvas.restoreToCount(count)
            }
        }
    }
}