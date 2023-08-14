package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.component3
import androidx.core.graphics.component4
import androidx.core.graphics.withClip
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
    private var secondaryShape: SelectionShape? = null
    private var inMonthShape: SelectionShape? = null

    private val tempRect = RectF()

    private var roundRectPathInfo: RoundRectVisualInfo? = null

    private fun getRoundRectPathInfo(): RoundRectVisualInfo =
        getLazyValue(roundRectPathInfo, ::RoundRectVisualInfo) { roundRectPathInfo = it }

    private fun getSecondaryShape(): SelectionShape =
        getLazyValue(secondaryShape, ::SelectionShape) { secondaryShape = it }

    private fun getInMonthShape(): SelectionShape =
        getLazyValue(inMonthShape, ::SelectionShape) { inMonthShape = it }

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
                val shapeInfo = state.baseState.shapeInfo

                drawOpaqueRect(
                    canvas,
                    state.bounds,
                    options,
                    shapeInfo.useInMonthShape, shapeInfo.inMonthShapeInfo
                )
            }

            is DefaultSelectionState.CellDualBubble -> {
                val startShapeInfo = state.start.shapeInfo
                val endShapeInfo = state.end.shapeInfo

                drawOpaqueRect(
                    canvas,
                    state.startBounds,
                    options,
                    startShapeInfo.useInMonthShape, startShapeInfo.inMonthShapeInfo
                )

                drawOpaqueRect(
                    canvas,
                    state.endBounds,
                    options,
                    endShapeInfo.useInMonthShape, endShapeInfo.inMonthShapeInfo
                )
            }

            is DefaultSelectionState.CellMoveToCell -> {
                val shapeInfo = state.shapeInfo
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

            drawRect(
                canvas,
                left, top, width, height,
                options,
                alpha,
                shapeInfo.useInMonthShape, shapeInfo.inMonthShapeInfo
            )
        } else {
            drawGeneralRange(canvas, shapeInfo, options, alpha, isPrimary)
        }
    }

    private fun drawRange(
        canvas: Canvas,
        state: SelectionShapeBasedState,
        options: SelectionRenderOptions,
        alpha: Float,
        isPrimary: Boolean
    ) {
        drawRange(canvas, state.shapeInfo, options, alpha, isPrimary)
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

                info.getPath()?.also {
                    // Clip path if info.getPath() is not null. It returns null if round radius is 0.
                    //
                    // We can clip without an additional save because we already have a save for translation that is always used
                    // for drawable fills
                    canvas.clipPath(it)
                }

                drawDrawableWithAlpha(canvas, drawable, alpha)
            } else {
                drawObjectInMonthAware(canvas, inMonthShape, alpha, outMonthAlpha) { a ->
                    drawRoundRectWithFill(canvas, shapeBounds, rr, a, fillState)
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
        alpha: Float,
        isPrimary: Boolean
    ) {
        val shape = if (isPrimary) primaryShape else getSecondaryShape()
        val fill = options.fill
        val fillState = options.fillState

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
                // As isDrawableFill is true, it means that SelectionShape.update() was called with forcePath=true,
                // that makes the logic to create a path.
                canvas.clipPath(shape.path!!)

                drawDrawableWithAlpha(canvas, drawable, alpha)
            } else {
                drawObjectInMonthAware(canvas, inMonthShape, alpha, outMonthAlpha) { a ->
                    drawShapeWithFill(canvas, translatedBounds, shape, fillState, a)
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

            canvas.withClip(inMonthPath) {
                drawObject(alpha)
            }

            canvas.withClipOut(inMonthPath) {
                drawObject(outMonthAlpha * alpha)
            }
        }
    }

    private fun drawShapeWithFill(
        canvas: Canvas,
        bounds: RectF,
        shape: SelectionShape,
        fillState: Fill.State,
        alpha: Float,
    ) {
        fillState.drawWith(canvas, bounds, paint, alpha) { shape.draw(canvas, paint) }
    }

    private fun drawRoundRectWithFill(
        canvas: Canvas,
        bounds: RectF,
        rr: Float,
        alpha: Float,
        fillState: Fill.State
    ) {
        fillState.drawWith(canvas, bounds, paint, alpha) { drawRoundRect(bounds, rr, rr, paint) }
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