package com.github.pelmenstar1.rangecalendar.decoration

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.annotation.ColorInt
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.component3
import androidx.core.graphics.component4
import androidx.core.graphics.withTranslation
import com.github.pelmenstar1.rangecalendar.Border
import com.github.pelmenstar1.rangecalendar.BorderAnimationType
import com.github.pelmenstar1.rangecalendar.Fill
import com.github.pelmenstar1.rangecalendar.HorizontalAlignment
import com.github.pelmenstar1.rangecalendar.Padding
import com.github.pelmenstar1.rangecalendar.R
import com.github.pelmenstar1.rangecalendar.Shape
import com.github.pelmenstar1.rangecalendar.VerticalAlignment
import com.github.pelmenstar1.rangecalendar.utils.RECT_ARRAY_BOTTOM
import com.github.pelmenstar1.rangecalendar.utils.RECT_ARRAY_LEFT
import com.github.pelmenstar1.rangecalendar.utils.RECT_ARRAY_RIGHT
import com.github.pelmenstar1.rangecalendar.utils.RECT_ARRAY_TOP
import com.github.pelmenstar1.rangecalendar.utils.arrayRectToObject
import com.github.pelmenstar1.rangecalendar.utils.lerpFloatArray
import com.github.pelmenstar1.rangecalendar.utils.setRectFromValues

/**
 * Represents a shape decoration.
 * All the shapes in the cell will be positioned horizontally.
 */
class ShapeDecor(val style: Style) : CellDecor() {
    private object ShapeVisual : Visual {
        override fun stateHandler(): VisualStateHandler = ShapeStateHandler
        override fun renderer(): Renderer = ShapeRenderer
    }

    private class TransitiveAdditionShapeVisualState(
        override val start: ShapeVisualState,
        override val end: ShapeVisualState,
        private val affectedRangeStart: Int,
        private val affectedRangeEnd: Int
    ) : ShapeVisualState(
        end.inCellLeft, end.inCellTop, end.inCellRight, end.inCellBottom,
        FloatArray(end.boundsArray.size),
        end.styles
    ), VisualState.Transitive {
        override var animationFraction: Float = 0f

        override fun handleAnimation(
            animationFraction: Float,
            fractionInterpolator: DecorAnimationFractionInterpolator
        ) {
            this.animationFraction = animationFraction

            val startBoundsArray = start.boundsArray
            val endBoundsArray = end.boundsArray
            val thisBoundsArray = boundsArray

            val rangeStart = affectedRangeStart
            val rangeEnd = affectedRangeEnd
            val rangeLength = rangeEnd - rangeStart + 1

            lerpFloatArray(
                startBoundsArray, endBoundsArray, thisBoundsArray,
                startIndex = 0, endIndexExclusive = rangeStart * 4,
                animationFraction
            )

            for (i in rangeStart..rangeEnd) {
                val animFraction = fractionInterpolator.getItemFraction(
                    i - rangeStart, rangeLength, animationFraction
                )

                lerpRectFromCenter(
                    sourceArray = endBoundsArray,
                    destArray = thisBoundsArray,
                    index = i * 4,
                    animFraction
                )
            }

            lerpFloatArray(
                startBoundsArray, endBoundsArray, thisBoundsArray,
                startIndex = (rangeEnd + 1) * 4, endIndexExclusive = thisBoundsArray.size,
                animationFraction,
                startOffset = rangeLength * 4
            )
        }
    }

    private class TransitiveRemovalShapeVisualState(
        override val start: ShapeVisualState,
        override val end: ShapeVisualState,
        private val affectedRangeStart: Int,
        private val affectedRangeEnd: Int
    ) : ShapeVisualState(
        start.inCellLeft, start.inCellTop, start.inCellRight, start.inCellBottom,
        FloatArray(start.boundsArray.size),
        start.styles
    ), VisualState.Transitive {
        override var animationFraction: Float = 0f

        override fun handleAnimation(
            animationFraction: Float,
            fractionInterpolator: DecorAnimationFractionInterpolator
        ) {
            val revAnimationFraction = 1f - animationFraction

            this.animationFraction = revAnimationFraction

            val startBoundsArray = start.boundsArray
            val endBoundsArray = end.boundsArray
            val thisBoundsArray = boundsArray

            val rangeStart = affectedRangeStart
            val rangeEnd = affectedRangeEnd
            val rangeLength = rangeEnd - rangeStart + 1

            lerpFloatArray(
                startBoundsArray, endBoundsArray, thisBoundsArray,
                startIndex = 0, endIndexExclusive = rangeStart * 4,
                animationFraction
            )

            for (i in rangeStart..rangeEnd) {
                val itemFraction = fractionInterpolator.getItemFraction(
                    i - rangeStart, rangeLength, revAnimationFraction
                )

                lerpRectFromCenter(
                    sourceArray = startBoundsArray,
                    destArray = thisBoundsArray,
                    index = i * 4,
                    itemFraction
                )
            }

            lerpFloatArray(
                startBoundsArray, endBoundsArray, thisBoundsArray,
                startIndex = (rangeEnd + 1) * 4, endIndexExclusive = thisBoundsArray.size,
                animationFraction,
                endOffset = rangeLength * 4
            )
        }
    }

    private class TransitiveCellInfoShapeVisualState(
        override val start: ShapeVisualState,
        override val end: ShapeVisualState,
        private val affectedRangeStart: Int,
        private val affectedRangeEnd: Int
    ) : ShapeVisualState(
        start.inCellLeft, start.inCellTop, start.inCellRight, start.inCellBottom,
        FloatArray(start.boundsArray.size),
        start.styles
    ), VisualState.Transitive {
        override var animationFraction: Float = 0f

        override fun handleAnimation(
            animationFraction: Float,
            fractionInterpolator: DecorAnimationFractionInterpolator
        ) {
            this.animationFraction = animationFraction

            lerpFloatArray(
                start.boundsArray, end.boundsArray, boundsArray,
                affectedRangeStart * 4, (affectedRangeEnd + 1) * 4,
                animationFraction
            )
        }
    }

    private open class ShapeVisualState(
        val inCellLeft: Float,
        val inCellTop: Float,
        val inCellRight: Float,
        val inCellBottom: Float,
        val boundsArray: FloatArray,
        val styles: Array<Style>
    ) : VisualState {
        override val isEmpty: Boolean
            get() = boundsArray.isEmpty()

        override fun visual(): Visual = ShapeVisual
    }

    private object ShapeStateHandler : VisualStateHandler {
        private val emptyShapeState = ShapeVisualState(
            0f, 0f, 0f, 0f,
            FloatArray(0),
            emptyArray()
        )

        private val rect = RectF()

        private var defaultDecorBlockPadding: Padding? = null
        private var defaultDecorPadding: Padding? = null

        private fun getDecorBlockPadding(context: Context, padding: Padding?): Padding {
            if (padding == null) {
                var defaultPadding = defaultDecorBlockPadding

                if (defaultPadding == null) {
                    val hMargin =
                        context.resources.getDimension(R.dimen.rangeCalendar_shapeHorizontalMargin)

                    defaultPadding = Padding(hMargin, 0f, hMargin, 0f)

                    defaultDecorBlockPadding = defaultPadding
                }

                return defaultPadding
            }

            return padding
        }

        private fun getDecorPadding(context: Context, padding: Padding?): Padding {
            if (padding == null) {
                var defaultPadding = defaultDecorPadding

                if (defaultPadding == null) {
                    val topMargin =
                        context.resources.getDimension(R.dimen.rangeCalendar_shapeMarginTop)

                    defaultPadding = Padding(0f, topMargin, 0f, 0f)

                    defaultDecorPadding = defaultPadding
                }

                return defaultPadding
            }

            return padding
        }

        override fun emptyState(): VisualState = emptyShapeState

        override fun createState(
            context: Context,
            decorations: Array<out CellDecor>,
            start: Int,
            endInclusive: Int,
            info: CellInfo
        ): VisualState {
            val rect = rect
            val decorCount = endInclusive - start + 1

            val layoutOptions = info.layoutOptions
            val blockPadding = getDecorBlockPadding(context, layoutOptions?.padding)

            var totalWidth = blockPadding.left + blockPadding.right
            var maxHeight = Float.MIN_VALUE

            for (i in start..endInclusive) {
                val decor = decorations[i] as ShapeDecor

                val style = decor.style
                val padding = getDecorPadding(context, style.padding)

                val size = style.size
                if (size > maxHeight) {
                    maxHeight = size
                }

                totalWidth += size

                if (i > start) {
                    totalWidth += padding.left
                }

                if (i < endInclusive) {
                    totalWidth += padding.right
                }
            }

            val top = info.findTopWithAlignment(
                maxHeight, blockPadding,
                layoutOptions?.verticalAlignment ?: VerticalAlignment.CENTER
            )

            rect.set(0f, top, info.width, top + maxHeight)

            info.narrowRectOnBottom(rect)
            val (inCellLeft, inCellTop, inCellRight, inCellBottom) = rect

            var left = when (
                info.layoutOptions?.horizontalAlignment ?: HorizontalAlignment.CENTER
            ) {
                HorizontalAlignment.LEFT -> rect.left + blockPadding.left
                HorizontalAlignment.CENTER -> (rect.left + rect.right - totalWidth) * 0.5f
                HorizontalAlignment.RIGHT -> rect.right - totalWidth - blockPadding.right
            }

            val styles = Array(decorCount) { (decorations[start + it] as ShapeDecor).style }
            val boundsArray = FloatArray(decorCount * 4)

            val halfMaxHeight = maxHeight * 0.5f

            for (i in 0 until decorCount) {
                val decor = decorations[start + i] as ShapeDecor
                val style = decor.style
                val padding = getDecorPadding(context, style.padding)
                val size = style.size

                if (i > 0) {
                    left += padding.left
                }

                val decorTop = when (style.contentAlignment) {
                    VerticalAlignment.TOP -> top + padding.top
                    VerticalAlignment.CENTER -> top + halfMaxHeight - size * 0.5f
                    VerticalAlignment.BOTTOM -> top + maxHeight - size
                }

                val right = left + size
                val bottom = decorTop + size

                setRectFromValues(
                    boundsArray, i * 4,
                    left, decorTop, right, bottom
                )

                style.fill.setSize(size, size)

                left += size
                if (i < decorCount - 1) {
                    left += padding.right
                }
            }

            return ShapeVisualState(
                inCellLeft, inCellTop, inCellRight, inCellBottom,
                boundsArray,
                styles
            )
        }

        override fun createTransitiveState(
            start: VisualState,
            end: VisualState,
            affectedRangeStart: Int,
            affectedRangeEnd: Int,
            change: VisualStateChange
        ): VisualState.Transitive {
            start as ShapeVisualState
            end as ShapeVisualState

            return when (change) {
                VisualStateChange.ADD -> {
                    TransitiveAdditionShapeVisualState(
                        start, end,
                        affectedRangeStart, affectedRangeEnd
                    )
                }

                VisualStateChange.REMOVE -> {
                    TransitiveRemovalShapeVisualState(
                        start, end,
                        affectedRangeStart, affectedRangeEnd
                    )
                }

                VisualStateChange.CELL_INFO -> {
                    TransitiveCellInfoShapeVisualState(
                        start, end,
                        affectedRangeStart, affectedRangeEnd
                    )
                }
            }
        }
    }

    private object ShapeRenderer : Renderer {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var tempPath: Path? = null

        private val rect = RectF()

        override fun renderState(canvas: Canvas, state: VisualState) {
            val shapeState = state as ShapeVisualState
            val rect = rect
            val paint = paint
            val boundsArray = shapeState.boundsArray
            val styles = shapeState.styles
            val decorCount = styles.size

            canvas.save()
            canvas.clipRect(
                shapeState.inCellLeft,
                shapeState.inCellTop,
                shapeState.inCellRight,
                shapeState.inCellBottom
            )

            for (i in 0 until decorCount) {
                val style = styles[i]
                val absIndex = i * 4
                val shape = style.shape

                arrayRectToObject(boundsArray, absIndex, rect)
                drawFilledShape(canvas, shape, style.fill)

                val border = style.border
                if (border != null) {
                    val endBoundsArray = when (state) {
                        is TransitiveAdditionShapeVisualState -> state.end.boundsArray
                        is TransitiveCellInfoShapeVisualState -> state.end.boundsArray
                        is TransitiveRemovalShapeVisualState -> state.start.boundsArray

                        else -> boundsArray
                    }

                    // Need to call it again as drawFilledShape mutates rect.
                    arrayRectToObject(boundsArray, absIndex, rect)

                    border.doDrawPreparation(
                        animatedBoundsArray = boundsArray,
                        endBoundsArray,
                        absIndex,
                        state.animationFraction, style.borderAnimationType,
                        paint, rect
                    )

                    drawShape(canvas, shape)
                }
            }

            canvas.restore()
        }

        private fun drawShape(canvas: Canvas, shape: Shape) {
            val rect = rect

            if (shape.needsPathToDraw) {
                var path = tempPath
                if (path == null) {
                    path = Path()
                    tempPath = path
                }

                shape.draw(canvas, rect, path, paint)

                path.reset()
            } else {
                shape.draw(canvas, rect, null, paint)
            }
        }

        private fun drawFilledShape(canvas: Canvas, shape: Shape, fill: Fill) {
            val (left, top, right, bottom) = rect
            val width = right - left
            val height = bottom - top

            rect.set(0f, 0f, width, height)

            canvas.withTranslation(left, top) {
                fill.drawWith(canvas, rect, paint, alpha = 1f) {
                    drawShape(canvas, shape)
                }
            }
        }
    }

    override fun visual(): Visual = ShapeVisual

    /**
     * Represents style for [ShapeDecor].
     */
    class Style private constructor(
        /**
         * Type of shape.
         */
        val shape: Shape,

        /**
         * Size of shape as width and height are the same.
         */
        val size: Float,

        /**
         * Fill of shape.
         */
        val fill: Fill,

        /**
         * Padding of shape, if it's null, then there's no padding.
         */
        val padding: Padding?,

        /**
         * Border of shape, if it's null, then there's no border.
         */
        val border: Border?,

        /**
         * Represents how to animate the border
         */
        val borderAnimationType: BorderAnimationType,

        /**
         * Vertical alignment of the shape.
         */
        val contentAlignment: VerticalAlignment
    ) {
        class Builder(
            private var shape: Shape,
            private var size: Float,
            private var fill: Fill
        ) {
            private var padding: Padding? = null
            private var border: Border? = null
            private var borderAnimationType = BorderAnimationType.SHAPE_AND_WIDTH
            private var contentAlignment = VerticalAlignment.CENTER

            init {
                require(size >= 0f) { "Size is negative or zero" }
            }

            /**
             * Sets padding of the shape.
             *
             * @param value padding of the line. If it's null, then there's no padding.
             * @return reference to this object
             */
            fun padding(value: Padding?) = apply {
                padding = value
            }

            /**
             * Sets padding of the shape.
             *
             * @return reference to this object
             */
            fun padding(
                left: Float = 0f,
                top: Float = 0f,
                right: Float = 0f,
                bottom: Float = 0f
            ) = padding(Padding(left, top, right, bottom))

            /**
             * Sets border of the shape using color specified by color int and stroke width.
             *
             * @param color color of border
             * @param width stroke width, in pixels
             *
             * @return reference to this object
             */
            fun border(@ColorInt color: Int, width: Float) = border(Border(color, width))

            /**
             * Sets border of the shape.
             *
             * @param value [Border] object that describes border more precisely
             * @return reference to this object
             */
            fun border(value: Border) = apply {
                border = value
            }

            /**
             * Sets border animation type.
             *
             * @return reference to this object
             */
            fun borderAnimationType(value: BorderAnimationType) = apply {
                borderAnimationType = value
            }

            /**
             * Sets vertical alignment of the shape.
             *
             * @return reference to this object
             */
            fun contentAlignment(value: VerticalAlignment) = apply {
                contentAlignment = value
            }

            /**
             * Builds [Style] object.
             */
            fun build() = Style(
                shape,
                size,
                fill,
                padding,
                border, borderAnimationType,
                contentAlignment
            )
        }
    }

    companion object {
        private fun lerpRectFromCenter(
            sourceArray: FloatArray, destArray: FloatArray,
            index: Int,
            fraction: Float
        ) {
            val left = sourceArray[index + RECT_ARRAY_LEFT]
            val top = sourceArray[index + RECT_ARRAY_TOP]
            val right = sourceArray[index + RECT_ARRAY_RIGHT]
            val bottom = sourceArray[index + RECT_ARRAY_BOTTOM]

            val centerX = (left + right) * 0.5f
            val centerY = (top + bottom) * 0.5f

            val t = 0.5f * fraction
            val animatedHalfWidth = (right - left) * t
            val animatedHalfHeight = (bottom - top) * t

            setRectFromValues(
                destArray, index,
                left = centerX - animatedHalfWidth, top = centerY - animatedHalfHeight,
                right = centerX + animatedHalfWidth, bottom = centerY + animatedHalfHeight
            )
        }
    }
}