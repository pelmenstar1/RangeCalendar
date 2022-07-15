package io.github.pelmenstar1.rangecalendar.decoration

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.annotation.ColorInt
import androidx.annotation.ColorLong
import androidx.annotation.RequiresApi
import io.github.pelmenstar1.rangecalendar.*

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
    ) : ShapeVisualState(end.boundsInCell, PackedRectFArray(end.boundsArray.size), end.styles),
        VisualState.Transitive {
        override var animationFraction: Float = 0f

        override fun handleAnimation(
            animationFraction: Float,
            fractionInterpolator: DecorAnimationFractionInterpolator
        ) {
            this.animationFraction = animationFraction

            val startBoundsArray = start.boundsArray
            val endBoundsArray = end.boundsArray
            val affectedRangeLength = affectedRangeEnd - affectedRangeStart + 1

            lerpRectArray(
                startBoundsArray, endBoundsArray, boundsArray,
                0, affectedRangeStart - 1,
                animationFraction
            )

            for (i in affectedRangeStart..affectedRangeEnd) {
                val animFraction = fractionInterpolator.getItemFraction(
                    i - affectedRangeStart, affectedRangeLength, animationFraction
                )

                val bounds = endBoundsArray[i]

                boundsArray[i] = lerpRectFromCenter(bounds, animFraction)
            }

            lerpRectArray(
                startBoundsArray, endBoundsArray, boundsArray,
                affectedRangeEnd + 1, endBoundsArray.size - 1,
                animationFraction,
                startOffset = affectedRangeLength
            )
        }
    }

    private class TransitiveRemovalShapeVisualState(
        override val start: ShapeVisualState,
        override val end: ShapeVisualState,
        private val affectedRangeStart: Int,
        private val affectedRangeEnd: Int
    ): ShapeVisualState(start.boundsInCell, PackedRectFArray(start.boundsArray.size), start.styles), VisualState.Transitive {
        override var animationFraction: Float = 0f

        override fun handleAnimation(
            animationFraction: Float,
            fractionInterpolator: DecorAnimationFractionInterpolator
        ) {
            val revAnimationFraction = 1f - animationFraction

            this.animationFraction = revAnimationFraction

            val startBoundsArray = start.boundsArray
            val endBoundsArray = end.boundsArray

            lerpRectArray(
                startBoundsArray, endBoundsArray, boundsArray,
                0, affectedRangeStart - 1,
                animationFraction
            )

            val affectedRangeLength = affectedRangeEnd - affectedRangeStart + 1

            for (i in affectedRangeStart..affectedRangeEnd) {
                val itemFraction = fractionInterpolator.getItemFraction(
                    i - affectedRangeStart, affectedRangeLength, revAnimationFraction
                )

                val bounds = startBoundsArray[i]

                boundsArray[i] = lerpRectFromCenter(bounds, itemFraction)
            }

            lerpRectArray(
                startBoundsArray, endBoundsArray, boundsArray,
                affectedRangeEnd + 1, startBoundsArray.size - 1,
                animationFraction,
                endOffset = affectedRangeLength
            )
        }
    }

    private class TransitiveCellInfoShapeVisualState(
        override val start: ShapeVisualState,
        override val end: ShapeVisualState,
        private val affectedRangeStart: Int,
        private val affectedRangeEnd: Int
    ): ShapeVisualState(start.boundsInCell, PackedRectFArray(start.boundsArray.size), start.styles), VisualState.Transitive {
        override var animationFraction: Float = 0f

        override fun handleAnimation(
            animationFraction: Float,
            fractionInterpolator: DecorAnimationFractionInterpolator
        ) {
            this.animationFraction = animationFraction

            lerpRectArray(
                start.boundsArray, end.boundsArray, boundsArray,
                affectedRangeStart, affectedRangeEnd,
                animationFraction
            )
        }
    }

    private open class ShapeVisualState(
        val boundsInCell: PackedRectF,
        val boundsArray: PackedRectFArray,
        val styles: Array<Style>
    ) : VisualState {
        override val isEmpty: Boolean
            get() = boundsArray.isEmpty

        override fun visual(): Visual = ShapeVisual
    }

    private object ShapeStateHandler : VisualStateHandler {
        private val emptyShapeState = ShapeVisualState(
            PackedRectF(0),
            PackedRectFArray(0),
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
            val length = endInclusive - start + 1

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

            rect.set(0f, top, info.size, top + maxHeight)

            info.narrowRectOnBottom(rect)
            val narrowArea = PackedRectF(rect)

            var left = when (
                info.layoutOptions?.horizontalAlignment ?: HorizontalAlignment.CENTER
            ) {
                HorizontalAlignment.LEFT -> {
                    rect.left + blockPadding.left
                }
                HorizontalAlignment.CENTER -> {
                    (rect.left + rect.right - totalWidth) * 0.5f
                }
                HorizontalAlignment.RIGHT -> {
                    rect.right - totalWidth - blockPadding.right
                }
            }

            val styles = Array(length) { (decorations[start + it] as ShapeDecor).style }
            val boundsArray = PackedRectFArray(length)

            val halfMaxHeight = maxHeight * 0.5f

            for (i in 0 until length) {
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
                val packedRect = PackedRectF(left, decorTop, right, bottom)

                boundsArray[i] = packedRect
                style.fill.setBounds(left, decorTop, right, bottom, style.shape)

                left += size
                if (i < length - 1) {
                    left += padding.right
                }
            }

            return ShapeVisualState(narrowArea, boundsArray, styles)
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

            canvas.save()
            shapeState.boundsInCell.setTo(rect)
            canvas.clipRect(rect)

            val boundsArray = shapeState.boundsArray
            val styles = shapeState.styles

            for (i in 0 until boundsArray.size) {
                val bounds = boundsArray[i]
                val style = styles[i]

                style.fill.applyToPaint(paint)

                bounds.setTo(rect)

                val shapeType = style.shape

                drawShape(canvas, shapeType)

                val border = style.border

                if (border != null) {
                    val endBounds = when (state) {
                        is TransitiveAdditionShapeVisualState -> state.end.boundsArray[i]
                        is TransitiveCellInfoShapeVisualState -> state.end.boundsArray[i]
                        is TransitiveRemovalShapeVisualState -> state.start.boundsArray[i]

                        else -> bounds
                    }

                    border.doDrawPreparation(
                        bounds, endBounds,
                        state.animationFraction, style.borderAnimationType,
                        paint, rect
                    )

                    drawShape(canvas, shapeType)
                }
            }

            canvas.restore()
        }

        private fun drawShape(canvas: Canvas, shape: Shape) {
            if(shape.needsPathToDraw) {
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
        private fun lerpRectFromCenter(rect: PackedRectF, fraction: Float): PackedRectF {
            val (left, top, right, bottom) = rect

            val centerX = (left + right) * 0.5f
            val centerY = (top + bottom) * 0.5f

            val t = 0.5f * fraction

            val animatedHalfWidth = (right - left) * t
            val animatedHalfHeight = (bottom - top) * t

            return PackedRectF(
                centerX - animatedHalfWidth, centerY - animatedHalfHeight,
                centerX + animatedHalfWidth, centerY + animatedHalfHeight
            )
        }
    }
}