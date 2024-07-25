package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.component3
import androidx.core.graphics.component4
import androidx.core.graphics.withClip
import androidx.core.graphics.withSave
import com.github.pelmenstar1.rangecalendar.Border
import com.github.pelmenstar1.rangecalendar.Fill
import com.github.pelmenstar1.rangecalendar.RoundRectVisualInfo
import com.github.pelmenstar1.rangecalendar.SelectionFillGradientBoundsType
import com.github.pelmenstar1.rangecalendar.utils.getLazyValue
import com.github.pelmenstar1.rangecalendar.utils.toIntAlpha
import com.github.pelmenstar1.rangecalendar.utils.withClipOut

internal class DefaultSelectionRenderer : SelectionRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val primaryShape = SelectionShape()
    private var inMonthShape: SelectionShape? = null

    private val tempRect = RectF()

    private var roundRectPathInfo: RoundRectVisualInfo? = null

    private fun getRoundRectPathInfo(): RoundRectVisualInfo =
        getLazyValue(roundRectPathInfo, ::RoundRectVisualInfo) { roundRectPathInfo = it }

    private fun getInMonthShape(): SelectionShape =
        getLazyValue(inMonthShape, ::SelectionShape) { inMonthShape = it }

    override fun draw(canvas: Canvas, state: SelectionState, options: SelectionRenderOptions) {
        state as DefaultSelectionState

        drawFragments(canvas, state.fragments, options)
    }

    override fun drawTransitionStage(
        canvas: Canvas,
        stage: SelectionTransitionStage,
        options: SelectionRenderOptions
    ) {
        when (stage) {
            is DefaultSelectionTransitionStage.AppearAlpha -> {
                drawRange(canvas, stage.shapeInfo, options, stage.alpha)
            }
            is DefaultSelectionTransitionStage.CellAppearBubble -> {
                val shapeInfo = stage.shapeInfo

                drawOpaqueRect(
                    canvas,
                    stage.bounds,
                    options,
                    shapeInfo.useInMonthShape, shapeInfo.inMonthShapeInfo
                )
            }
            is DefaultSelectionTransitionStage.MoveCellToCell -> {
                val shapeInfo = stage.shapeInfo
                val width = shapeInfo.cellWidth
                val height = shapeInfo.cellHeight

                drawRect(
                    canvas,
                    shapeInfo.startLeft, shapeInfo.startTop, width, height,
                    options,
                    alpha = 1f,
                    shapeInfo.useInMonthShape, shapeInfo.inMonthShapeInfo
                )
            }
            is DefaultSelectionTransitionStage.TransformFragmentToFragment -> {
                drawGeneralRange(canvas, stage.shapeInfo, options, alpha = 1f)
            }

            is DefaultSelectionTransitionStage.NoOp -> {
                for (shapeInfo in stage.shapeInfoArray) {
                    drawGeneralRange(canvas, shapeInfo, options, alpha = 1f)
                }
            }
        }
    }

    private fun drawRange(
        canvas: Canvas,
        shapeInfo: SelectionShapeInfo,
        options: SelectionRenderOptions,
        alpha: Float
    ) {
        val rangeStart = shapeInfo.rangeStart
        val rangeEnd = shapeInfo.rangeEnd

        // If start and end of the range are on the same row, there could be applied some optimizations
        // that allow drawing the range without using Path.
        if (Cell.gridY(rangeStart) == Cell.gridY(rangeEnd)) {
            val left = shapeInfo.startLeft
            val top = shapeInfo.startTop
            val width = shapeInfo.endRight - left
            val height = shapeInfo.cellHeight

            drawRect(
                canvas,
                left, top, width, height,
                options,
                alpha,
                shapeInfo.useInMonthShape, shapeInfo.inMonthShapeInfo
            )
        } else {
            drawGeneralRange(canvas, shapeInfo, options, alpha)
        }
    }

    private fun drawFragments(
        canvas: Canvas,
        fragments: List<SelectionFragmentState>,
        options: SelectionRenderOptions,
    ) {
        for (fragment in fragments) {
            fragment as DefaultSelectionFragmentState

            drawRange(canvas, fragment.shapeInfo, options, alpha = 1f)
        }
    }

    private fun drawOpaqueRect(
        canvas: Canvas,
        bounds: RectF,
        options: SelectionRenderOptions,
        useInMonthShape: Boolean,
        inMonthShapeInfo: SelectionShapeInfo?
    ) {
        drawRect(
            canvas,
            bounds.left, bounds.top, bounds.width(), bounds.height(),
            options,
            alpha = 1f,
            useInMonthShape, inMonthShapeInfo
        )
    }

    private fun drawRect(
        canvas: Canvas,
        left: Float, top: Float,
        width: Float, height: Float,
        options: SelectionRenderOptions,
        alpha: Float,
        useInMonthShape: Boolean,
        inMonthShapeInfo: SelectionShapeInfo?
    ) {
        val fill = options.fill
        val fillState = options.fillState
        val border = options.border

        val rr = options.roundRadius
        val shapeBounds = tempRect
        val outMonthAlpha = options.outMonthAlpha

        val origin: Int
        var count = -1

        val useTranslationToBounds = useTranslationToBounds(fill, options.fillGradientBoundsType)

        if (useTranslationToBounds) {
            fillState.setSize(width, height)

            count = canvas.save()
            canvas.translate(left, top)

            shapeBounds.set(0f, 0f, width, height)

            origin = SelectionShape.ORIGIN_BOUNDS
        } else {
            shapeBounds.set(left, top, left + width, top + height)

            origin = SelectionShape.ORIGIN_LOCAL
        }

        var inMonthShape: SelectionShape? = null

        if (useInMonthShape && outMonthAlpha < 1f) {
            inMonthShape = getInMonthShape()
            inMonthShape.update(inMonthShapeInfo!!, origin, forcePath = true)
        }

        try {
            val drawable = fill.drawable

            if (drawable != null) {
                val info = getRoundRectPathInfo()

                info.setBounds(0f, 0f, width, height)
                info.setRoundedCorners(rr)

                canvas.withSave {
                    info.clip(canvas)
                    drawDrawableWithAlpha(canvas, drawable, alpha)
                }

                if (border != null) {
                    border.applyToPaint(paint, alpha)

                    info.draw(canvas, paint)
                }
            } else {
                drawObjectInMonthAware(canvas, inMonthShape, alpha, outMonthAlpha) { a ->
                    drawRoundRectWithFill(canvas, shapeBounds, rr, a, fillState, border)
                }
            }
        } finally {
            if (useTranslationToBounds) {
                canvas.restoreToCount(count)
            }
        }
    }

    private fun drawGeneralRange(
        canvas: Canvas,
        shapeInfo: SelectionShapeInfo,
        options: SelectionRenderOptions,
        alpha: Float
    ) {
        val shape = primaryShape
        val fill = options.fill
        val fillState = options.fillState
        val border = options.border

        //Log.i("DefaultSelectionRenderer", "shapeInfo: ${shapeInfo}")

        val outMonthAlpha = options.outMonthAlpha

        val forcePath = fill.isDrawableType
        val useTranslationToBounds = useTranslationToBounds(fill, options.fillGradientBoundsType)
        val origin = if (useTranslationToBounds) SelectionShape.ORIGIN_BOUNDS else SelectionShape.ORIGIN_LOCAL

        shape.update(shapeInfo, origin, forcePath)

        var inMonthShape: SelectionShape? = null

        if (shapeInfo.useInMonthShape && outMonthAlpha < 1f) {
            inMonthShape = getInMonthShape()
            inMonthShape.update(shapeInfo.inMonthShapeInfo!!, origin, forcePath = true)
        }

        val bounds = shape.bounds
        val translatedBounds: RectF

        var count = -1

        if (useTranslationToBounds) {
            val (left, top, right, bottom) = bounds
            val width = right - left
            val height = bottom - top

            fillState.setSize(width, height)

            count = canvas.save()
            canvas.translate(left, top)

            translatedBounds = tempRect.apply { set(0f, 0f, width, height) }
        } else {
            translatedBounds = bounds
        }

        try {
            val drawable = fill.drawable

            // We use a different approach of drawing if fill is drawable-type.
            // In that case, we draw a drawable with clipping over the shape.
            if (drawable != null) {
                val path = shape.path!!

                canvas.withClip(path) {
                    drawDrawableWithAlpha(canvas, drawable, alpha)
                }

                if (border != null) {
                    border.applyToPaint(paint, alpha)

                    shape.draw(canvas, paint)
                }
            } else {
                drawObjectInMonthAware(canvas, inMonthShape, alpha, outMonthAlpha) { a ->
                    drawShapeWithFill(canvas, translatedBounds, shape, fillState, border, a)
                }
            }
        } finally {
            if (useTranslationToBounds) {
                canvas.restoreToCount(count)
            }
        }
    }

    private inline fun drawObjectInMonthAware(
        canvas: Canvas,
        inMonthShape: SelectionShape?,
        alpha: Float,
        outMonthAlpha: Float,
        drawObject: Canvas.(alpha: Float) -> Unit
    ) {
        if (inMonthShape == null) {
            canvas.drawObject(alpha)
        } else {
            val inMonthPath = inMonthShape.path!!

            canvas.withClip(inMonthPath) { drawObject(alpha) }
            canvas.withClipOut(inMonthPath) { drawObject(outMonthAlpha * alpha) }
        }
    }

    private fun drawShapeWithFill(
        canvas: Canvas,
        bounds: RectF,
        shape: SelectionShape,
        fillState: Fill.State,
        border: Border?,
        alpha: Float,
    ) {
        drawObjectWithFillAndStroke(canvas, bounds, alpha, fillState, border) {
            shape.draw(canvas, paint)
        }
    }

    private fun drawRoundRectWithFill(
        canvas: Canvas,
        bounds: RectF,
        rr: Float,
        alpha: Float,
        fillState: Fill.State,
        border: Border?
    ) {
        drawObjectWithFillAndStroke(canvas, bounds, alpha, fillState, border) {
            drawRoundRect(bounds, rr, rr, paint)
        }
    }

    private inline fun drawObjectWithFillAndStroke(
        canvas: Canvas,
        bounds: RectF,
        alpha: Float,
        fillState: Fill.State,
        border: Border?,
        drawObject: Canvas.() -> Unit
    ) {
        fillState.drawWith(canvas, bounds, paint, alpha, drawObject)

        if (border != null) {
            border.applyToPaint(paint, alpha)

            canvas.drawObject()
        }
    }

    private fun drawDrawableWithAlpha(canvas: Canvas, drawable: Drawable, alpha: Float) {
        drawable.alpha = alpha.toIntAlpha()
        drawable.draw(canvas)
    }

    private fun useTranslationToBounds(fill: Fill, boundsType: SelectionFillGradientBoundsType): Boolean {
        // In case of a shader-like fill, we need to use additional translation
        // only if it's wanted to be so -- shader should be applied relative to the shape's local coordinates
        // We also need a translation if fill has TYPE_DRAWABLE type.
        // It's not yet customizable to use whole grid bounds as with shader-like fill.
        return (fill.isShaderLike && boundsType == SelectionFillGradientBoundsType.SHAPE) || fill.isDrawableType
    }
}