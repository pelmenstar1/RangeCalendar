package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.component3
import androidx.core.graphics.component4
import com.github.pelmenstar1.rangecalendar.Fill
import com.github.pelmenstar1.rangecalendar.RoundRectVisualInfo
import com.github.pelmenstar1.rangecalendar.SelectionFillGradientBoundsType
import com.github.pelmenstar1.rangecalendar.utils.getLazyValue

internal class DefaultSelectionRenderer : SelectionRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val primaryShape = SelectionShape()
    private val secondaryShape = SelectionShape()

    private var primaryCellNode: CellRenderNode? = null
    private var secondaryCellNode: CellRenderNode? = null

    private val tempRect = RectF()

    private var roundRectPathInfo: RoundRectVisualInfo? = null

    private fun getOrCreatePrimaryCellNode() =
        getLazyValue(primaryCellNode, ::CellRenderNode) { primaryCellNode = it }

    private fun getOrCreateSecondaryCellNode() =
        getLazyValue(secondaryCellNode, ::CellRenderNode) { secondaryCellNode = it }

    private fun getRoundRectPathInfo(): RoundRectVisualInfo =
        getLazyValue(roundRectPathInfo, ::RoundRectVisualInfo) { roundRectPathInfo = it }

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
        val fill = options.fill
        val rr = options.roundRadius
        val shapeBounds = tempRect

        var count = -1

        if (useTranslationToBounds(fill, options.fillGradientBoundsType)) {
            fill.setSize(width, height)

            count = canvas.save()
            canvas.translate(left, top)

            shapeBounds.set(0f, 0f, width, height)
        } else {
            shapeBounds.set(left, top, left + width, top + height)
        }

        try {
            val drawable = fill.drawable

            if (drawable != null) {
                val info = getRoundRectPathInfo()

                info.setBounds(0f, 0f, width, height)
                info.setRoundedCorners(rr)

                info.getPath()?.also {
                    // Clip path if info.getPath() is not null. It returns null if round radius is 0.
                    //
                    // We can clip without an additional save because we already have a save for translation that is always used
                    // for drawable fills
                    canvas.clipPath(it)
                }

                setDrawableAlpha(drawable, alpha)
                drawable.draw(canvas)
            } else {
                fill.drawWith(canvas, shapeBounds, paint, alpha) {
                    drawRoundRect(shapeBounds, rr, rr, paint)
                }
            }
        } finally {
            if (count >= 0) {
                canvas.restoreToCount(count)
            }
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

        val fill = options.fill

        var forcePath = false
        val origin: Int

        val useTranslationToBounds = useTranslationToBounds(fill, options.fillGradientBoundsType)

        if (useTranslationToBounds) {
            origin = SelectionShape.ORIGIN_BOUNDS
            forcePath = fill.isDrawableType
        } else {
            origin = SelectionShape.ORIGIN_LOCAL
        }

        shape.update(shapeInfo, origin, forcePath)

        val bounds = shape.bounds
        val translatedBounds: RectF

        var count = -1

        if (useTranslationToBounds) {
            val (left, top, right, bottom) = bounds
            val width = right - left
            val height = bottom - top

            fill.setSize(width, height)

            count = canvas.save()
            canvas.translate(left, top)

            translatedBounds = tempRect.apply {
                set(0f, 0f, width, height)
            }
        } else {
            translatedBounds = bounds
        }

        try {
            val drawable = fill.drawable

            // We use a different approach of drawing if fill is drawable-type.
            // In that case, we draw a drawable with clipping over the shape.
            if (drawable != null) {
                // As isDrawableFill is true, it means that SelectionShape.update() was called with forcePath=true,
                // that makes the logic to create a path.
                canvas.clipPath(shape.path!!)

                setDrawableAlpha(drawable, alpha)
                drawable.draw(canvas)
            } else {
                fill.drawWith(canvas, translatedBounds, paint, alpha) { shape.draw(canvas, paint) }
            }
        } finally {
            if (count >= 0) {
                canvas.restoreToCount(count)
            }
        }
    }

    private fun useTranslationToBounds(fill: Fill, boundsType: SelectionFillGradientBoundsType): Boolean {
        // In case of a shader-like fill, we need to use additional translation
        // only if it's wanted to be so -- shader should be applied relative to the shape's local coordinates
        // We also need a translation if fill has TYPE_DRAWABLE type.
        // It's not yet customizable to use whole grid bounds as with shader-like fill.
        return (fill.isShaderLike && boundsType == SelectionFillGradientBoundsType.SHAPE) || fill.isDrawableType
    }

    private fun setDrawableAlpha(drawable: Drawable, alpha: Float) {
        drawable.alpha = (alpha * 255f + 0.5f).toInt()
    }
}