package io.github.pelmenstar1.rangecalendar.decoration

import android.content.Context
import android.graphics.*
import androidx.annotation.ColorInt
import androidx.annotation.ColorLong
import androidx.annotation.RequiresApi
import io.github.pelmenstar1.rangecalendar.*
import io.github.pelmenstar1.rangecalendar.PackedRectF
import io.github.pelmenstar1.rangecalendar.PackedRectFArray
import io.github.pelmenstar1.rangecalendar.utils.getTextBounds
import io.github.pelmenstar1.rangecalendar.utils.lerp
import kotlin.math.max

/**
 * Represents a line decoration.
 * All the lines in the cell will be positioned vertically.
 */
class LineDecor(val style: Style) : CellDecor() {
    override fun visual(): Visual = LineVisual

    private open class LineVisualState(
        val linesBoundsArray: PackedRectFArray,
        val textBoundsArray: PackedRectFArray,
        val styles: Array<Style>
    ) : VisualState {
        override val isEmpty: Boolean
            get() = linesBoundsArray.isEmpty

        override fun visual(): Visual = LineVisual
    }

    private class TransitiveAdditionLineVisualState(
        override val start: LineVisualState,
        override val end: LineVisualState,
        private val affectedRangeStart: Int,
        private val affectedRangeEnd: Int
    ): LineVisualState(PackedRectFArray(end.linesBoundsArray.size), end.textBoundsArray, end.styles), VisualState.Transitive {
        override var animationFraction: Float = 0f

        override fun handleAnimation(
            animationFraction: Float,
            fractionInterpolator: DecorAnimationFractionInterpolator
        ) {
            this.animationFraction = animationFraction

            val startBoundsArray = start.linesBoundsArray
            val endBoundsArray = end.linesBoundsArray
            val affectedRangeLength = affectedRangeEnd - affectedRangeStart + 1

            lerpRectArray(
                startBoundsArray, endBoundsArray, linesBoundsArray,
                0, affectedRangeStart - 1,
                animationFraction
            )

            for (i in affectedRangeStart..affectedRangeEnd) {
                val animFraction = fractionInterpolator.getItemFraction(
                    i - affectedRangeStart, affectedRangeLength, animationFraction
                )

                val bounds = endBoundsArray[i]
                val style = end.styles[i]

                linesBoundsArray[i] = lerpRectHorizontal(
                    bounds, animFraction,
                    style.animationStartPosition
                )
            }

            lerpRectArray(
                startBoundsArray, endBoundsArray, linesBoundsArray,
                affectedRangeEnd + 1, endBoundsArray.size - 1,
                animationFraction,
                startOffset = affectedRangeLength
            )
        }
    }

    private class TransitiveRemovalLineVisualState(
        override val start: LineVisualState,
        override val end: LineVisualState,
        private val affectedRangeStart: Int,
        private val affectedRangeEnd: Int
    ): LineVisualState(PackedRectFArray(start.linesBoundsArray.size), start.textBoundsArray, start.styles), VisualState.Transitive {
        override var animationFraction: Float = 0f

        override fun handleAnimation(
            animationFraction: Float,
            fractionInterpolator: DecorAnimationFractionInterpolator
        ) {
            val revAnimationFraction = 1f - animationFraction

            this.animationFraction = revAnimationFraction

            val startBoundsArray = start.linesBoundsArray
            val endBoundsArray = end.linesBoundsArray

            lerpRectArray(
                startBoundsArray, endBoundsArray, linesBoundsArray,
                0, affectedRangeStart - 1,
                animationFraction
            )

            val affectedRangeLength = affectedRangeEnd - affectedRangeStart + 1

            for (i in affectedRangeStart..affectedRangeEnd) {
                val itemFraction = fractionInterpolator.getItemFraction(
                    i - affectedRangeStart, affectedRangeLength, revAnimationFraction
                )

                val bounds = startBoundsArray[i]
                val style = styles[i]

                linesBoundsArray[i] = lerpRectHorizontal(
                    bounds, itemFraction,
                    style.animationStartPosition
                )
            }

            lerpRectArray(
                startBoundsArray, endBoundsArray, linesBoundsArray,
                affectedRangeEnd + 1, startBoundsArray.size - 1,
                animationFraction,
                endOffset = affectedRangeLength
            )
        }
    }

    private class TransitiveCellInfoLineVisualState(
        override val start: LineVisualState,
        override val end: LineVisualState,
        private val affectedRangeStart: Int,
        private val affectedRangeEnd: Int
    ): LineVisualState(PackedRectFArray(start.linesBoundsArray.size), start.textBoundsArray, start.styles), VisualState.Transitive {
        override var animationFraction: Float = 0f

        override fun handleAnimation(
            animationFraction: Float,
            fractionInterpolator: DecorAnimationFractionInterpolator
        ) {
            this.animationFraction = animationFraction

            lerpRectArray(
                start.linesBoundsArray, end.linesBoundsArray, linesBoundsArray,
                affectedRangeStart, affectedRangeEnd,
                animationFraction
            )
        }
    }

    private object LineVisual : Visual {
        override fun stateHandler(): VisualStateHandler = LineStateHandler
        override fun renderer(): Renderer = LineRenderer
    }

    private object LineStateHandler : VisualStateHandler {
        private val emptyLineState = LineVisualState(
            PackedRectFArray(0),
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
                        context.resources.getDimension(R.dimen.rangeCalendar_lineHorizontalMargin)

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
                        context.resources.getDimension(R.dimen.rangeCalendar_lineMarginTop)

                    defaultPadding = Padding(0f, topMargin, 0f, 0f)

                    defaultDecorPadding = defaultPadding
                }

                return defaultPadding
            }

            return padding
        }

        override fun emptyState(): VisualState = emptyLineState

        override fun createState(
            context: Context,
            decorations: Array<out CellDecor>,
            start: Int,
            endInclusive: Int,
            info: CellInfo
        ): VisualState {
            val layoutOptions = info.layoutOptions
            val decorBlockPadding = getDecorBlockPadding(context, layoutOptions?.padding)
            val textHorizontalMargin =
                context.resources.getDimension(R.dimen.rangeCalendar_lineTextHorizontalMargin)

            val length = endInclusive - start + 1

            val linesBounds = PackedRectFArray(length)

            val styles = Array(length) { i ->
                (decorations[start + i] as LineDecor).style
            }

            val textBounds = info.textBounds
            val freeAreaHeight = info.size - textBounds.bottom

            // The coefficient is hand-picked for line not to be very thin or thick
            val defaultLineHeight = max(1f, freeAreaHeight / (2.5f * length))

            var lengthOfDecorsWithText = 0
            var totalHeight = 0f

            for (i in start..endInclusive) {
                val line = decorations[i] as LineDecor
                val style = line.style

                val height = style.height
                val padding = getDecorPadding(context, style.padding)

                totalHeight += height.resolveHeight(defaultLineHeight) + padding.top + padding.bottom

                if (style.text != null) {
                    lengthOfDecorsWithText++
                }
            }

            val textBoundsArray = PackedRectFArray(lengthOfDecorsWithText)
            var textIndex = 0

            // Find y of position where to start drawing lines
            var top = info.findTopWithAlignment(
                totalHeight, decorBlockPadding,
                layoutOptions?.verticalAlignment ?: VerticalAlignment.CENTER
            )

            for (i in 0 until length) {
                val decor = decorations[start + i] as LineDecor

                val style = decor.style
                val decorPadding = getDecorPadding(context, style.padding)

                top += decorPadding.top

                val height = style.height
                val resolvedHeight = height.resolveHeight(defaultLineHeight)
                val bottom = top + resolvedHeight

                rect.set(0f, top, info.size, bottom)

                // If cell is round, then it should be narrowed to fit the cell shape
                info.narrowRectOnBottom(rect)

                var left = rect.left + decorPadding.left + decorBlockPadding.left
                var right = rect.right - (decorPadding.right + decorBlockPadding.right)

                val width = style.width

                val maxWidth = right - left

                val resolvedWidth = if (width.isNaN() || width >= maxWidth) {
                    maxWidth
                } else {
                    width
                }

                if (resolvedWidth != width) {
                    when (style.horizontalAlignment) {
                        HorizontalAlignment.LEFT -> {
                            right = left + resolvedWidth
                        }
                        HorizontalAlignment.CENTER -> {
                            val centerX = (left + right) * 0.5f
                            val halfWidth = resolvedWidth * 0.5f

                            left = centerX - halfWidth
                            right = centerX + halfWidth
                        }
                        HorizontalAlignment.RIGHT -> {
                            left = right - resolvedWidth
                        }
                    }
                }

                rect.left = left
                rect.right = right

                linesBounds[i] = PackedRectF(rect)
                style.fill.setBounds(rect)

                if (style.text != null) {
                    val textSize = getTextBounds(style.text, style.textSize, style.textTypeface)
                    val (textWidth, textHeight) = textSize

                    val textX = when (style.textAlignment) {
                        HorizontalAlignment.LEFT -> {
                            left + textHorizontalMargin
                        }
                        HorizontalAlignment.CENTER -> {
                            (left + right - textWidth) * 0.5f
                        }
                        HorizontalAlignment.RIGHT -> {
                            right - textWidth - textHorizontalMargin
                        }
                    }
                    val textY = (top + bottom - textHeight) * 0.5f

                    textBoundsArray[textIndex++] = PackedRectF(
                        textX, textY, textX + textWidth, textY + textHeight
                    )
                }

                top += resolvedHeight + decorPadding.bottom
            }

            return LineVisualState(linesBounds, textBoundsArray, styles)
        }

        override fun createTransitiveState(
            start: VisualState,
            end: VisualState,
            affectedRangeStart: Int,
            affectedRangeEnd: Int,
            change: VisualStateChange
        ): VisualState.Transitive {
            start as LineVisualState
            end as LineVisualState

            return when (change) {
                VisualStateChange.ADD -> {
                    TransitiveAdditionLineVisualState(
                        start, end,
                        affectedRangeStart, affectedRangeEnd
                    )
                }
                VisualStateChange.REMOVE -> {
                    TransitiveRemovalLineVisualState(
                        start, end,
                        affectedRangeStart, affectedRangeEnd
                    )
                }
                VisualStateChange.CELL_INFO -> {
                    TransitiveCellInfoLineVisualState(
                        start, end,
                        affectedRangeStart, affectedRangeEnd
                    )
                }
            }
        }

        private fun Float.resolveHeight(default: Float): Float {
            return if (isNaN()) default else this
        }
    }

    private object LineRenderer : Renderer {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private var cachedPath: Path? = null
        private var tempRadii = FloatArray(8)
        private val rect = RectF()

        override fun renderState(
            canvas: Canvas,
            state: VisualState
        ) {
            val lineState = state as LineVisualState
            val linesBounds = lineState.linesBoundsArray

            val styles = lineState.styles
            val textBoundsArray = lineState.textBoundsArray
            var textIndex = 0

            for (i in 0 until linesBounds.size) {
                val bounds = linesBounds[i]
                val style = styles[i]

                style.fill.applyToPaint(paint)

                bounds.setTo(rect)

                drawRect(canvas, style)

                val border = style.border

                if (border != null) {
                    val endBounds = when (state) {
                        is TransitiveAdditionLineVisualState -> state.end.linesBoundsArray[i]
                        is TransitiveCellInfoLineVisualState -> state.end.linesBoundsArray[i]
                        is TransitiveRemovalLineVisualState -> state.start.linesBoundsArray[i]

                        else -> bounds
                    }

                    border.doDrawPreparation(
                        bounds, endBounds,
                        state.animationFraction, style.borderAnimationType,
                        paint, rect
                    )

                    drawRect(canvas, style)
                }

                val text = style.text

                if (text != null) {
                    paint.apply {
                        textSize = style.textSize
                        typeface = style.textTypeface
                        color = style.textColor

                        pathEffect = null
                    }

                    val textBounds = textBoundsArray[textIndex++]
                    val (textLeft, textTop, textRight, textBottom) = textBounds

                    if (style.clipTextToBounds) {
                        canvas.save()
                        canvas.clipRect(textLeft, textTop, textRight, textBottom)
                        canvas.drawText(text, textLeft, textBottom, paint)
                        canvas.restore()
                    } else {
                        canvas.drawText(text, textLeft, textBottom, paint)
                    }
                }
            }
        }

        private fun drawRect(canvas: Canvas, style: Style) {
            val lineHeight = rect.height()

            if (style.roundRadii != null) {
                var path = cachedPath
                if (path == null) {
                    path = Path()
                    cachedPath = path
                } else {
                    path.rewind()
                }

                val radii = style.roundRadii
                for (j in radii.indices) {
                    tempRadii[j] = radii[j].resolveRoundRadius(lineHeight)
                }

                path.addRoundRect(rect, radii, Path.Direction.CW)

                canvas.drawPath(path, paint)
            } else {
                val roundRadius = style.roundRadius.resolveRoundRadius(lineHeight)

                canvas.drawRoundRect(rect, roundRadius, roundRadius, paint)
            }
        }

        private fun Float.resolveRoundRadius(alternative: Float): Float {
            val half = alternative * 0.5f

            return if (this > half) half else this
        }
    }

    /**
     * Represents a position from which to start animating (dis-)appearance of the line.
     */
    enum class AnimationStartPosition {
        Left,
        Center,
        Right
    }

    /**
     * Represents style for [LineDecor]
     */
    class Style private constructor(
        /**
         * Fill of the line.
         */
        val fill: Fill,

        /**
         * Round radius of the line. It's only valid if [Style.roundRadii] is null.
         */
        val roundRadius: Float,

        /**
         * Represents round radius's for all 4 corners. The length is always 8.
         */
        val roundRadii: FloatArray?,

        /**
         * Width of the line, [Float.NaN] if it should be chosen automatically.
         */
        val width: Float,

        /**
         * Height of the line, [Float.NaN] if it should be chosen automatically.
         */
        val height: Float,

        /**
         * Horizontal alignment of the line.
         */
        val horizontalAlignment: HorizontalAlignment,

        /**
         * Position from which to start animating (dis-)appearance of the line.
         */
        val animationStartPosition: AnimationStartPosition,

        /**
         * Padding of the line, null if default.
         */
        val padding: Padding?,

        /**
         * Border of the line, null if no border.
         */
        val border: Border?,

        /**
         * Represents how to animate the border.
         */
        val borderAnimationType: BorderAnimationType,

        /**
         * Text inside the line.
         */
        val text: String?,

        /**
         * [Typeface] of the text.
         */
        val textTypeface: Typeface?,

        /**
         * Text size, in pixels, of the text.
         */
        val textSize: Float,

        /**
         * Text color of the text.
         */
        val textColor: Int,

        /**
         * Horizontal alignment of the text inside the line
         */
        val textAlignment: HorizontalAlignment,

        /**
         * Whether to clip text to bounds of the line.
         */
        val clipTextToBounds: Boolean,
    ) {
        /**
         * The builder class of [Style]
         */
        class Builder(private var fill: Fill) {
            private var roundRadius: Float = 0f
            private var roundRadii: FloatArray? = null
            private var horizontalAlignment = HorizontalAlignment.CENTER
            private var width: Float = Float.NaN
            private var height: Float = Float.NaN
            private var animationStartPosition = AnimationStartPosition.Center
            private var padding: Padding? = null

            private var text: String? = null
            private var textTypeface: Typeface? = null
            private var textSize: Float = Float.NaN
            private var textColor = 0
            private var textAlignment = HorizontalAlignment.CENTER
            private var clipTextToBounds = true

            private var border: Border? = null
            private var borderAnimationType = BorderAnimationType.ONLY_SHAPE

            /**
             * Sets [Fill] of the line.
             *
             * @return reference to this object.
             */
            fun fill(value: Fill) = apply {
                fill = value
            }

            /**
             * Sets position from which to start animating (dis-)appearance of the line.
             *
             * @return reference to this object.
             */
            fun animationStartPosition(value: AnimationStartPosition) = apply {
                animationStartPosition = value
            }

            /**
             * Sets round radius of the line for all corners.
             *
             * @param radius round radius (in pixels) of the line for all corners.
             * If it's [Float.POSITIVE_INFINITY], then line will be as round as possible.
             * @return reference to this object.
             *
             * @throws IllegalArgumentException if [radius] is negative
             */
            fun roundedCorners(radius: Float = Float.POSITIVE_INFINITY) = apply {
                require(radius >= 0f) { "Round radius can't be negative" }

                roundRadius = radius
                roundRadii = null
            }

            /**
             * Sets round radius's for all 4 corners of the line.
             * If one of arguments is [Float.POSITIVE_INFINITY],
             * then the associated angle will be as round as possible.
             *
             * @param leftTop round radius (in pixels) for left top corner
             * @param rightTop round radius (in pixels) for right top corner
             * @param rightBottom round radius (in pixels) for right bottom corner
             * @param leftBottom round radius (in pixels) for left bottom corner
             *
             * @return reference to this object.
             *
             * @throws IllegalArgumentException if one of the arguments is negative
             */
            fun roundedCorners(
                leftTop: Float = Float.POSITIVE_INFINITY,
                rightTop: Float = Float.POSITIVE_INFINITY,
                rightBottom: Float = Float.POSITIVE_INFINITY,
                leftBottom: Float = Float.POSITIVE_INFINITY
            ) = apply {
                require(leftTop >= 0f) { "'leftTop' can't be negative" }
                require(rightTop >= 0f) { "'rightTop' can't be negative" }
                require(rightBottom >= 0f) { "'rightBottom' can't be negative" }
                require(leftBottom >= 0f) { "'leftBottom' can't be negative" }

                roundRadii = FloatArray(8).also {
                    it[0] = leftTop
                    it[1] = leftTop
                    it[2] = rightTop
                    it[3] = rightTop
                    it[4] = rightBottom
                    it[5] = rightBottom
                    it[6] = leftBottom
                    it[7] = leftBottom
                }
            }

            /**
             * Sets horizontal alignment of the line.
             *
             * @return reference to this object.
             */
            fun horizontalAlignment(value: HorizontalAlignment) = apply {
                horizontalAlignment = value
            }

            /**
             * Sets width of the line.
             *
             * @param value width of the line. If it's [Float.NaN], then line width will be chosen automatically
             * @return reference to this object
             */
            fun width(value: Float) = apply {
                require(value > 0f || value.isNaN()) { "Width can't be negative or zero" }

                width = value
            }

            /**
             * Sets height of the line.
             *
             * @param value height of the line. If it's [Float.NaN], then line height will be chosen automatically
             * @return reference to this object
             */
            fun height(value: Float) = apply {
                require(value > 0f || value.isNaN()) { "Height can't be negative or zero" }

                height = value
            }

            /**
             * Sets padding of the line.
             *
             * @param value padding of the line. If it's null, then there's no padding.
             * @return reference to this object
             */
            fun padding(value: Padding?) = apply {
                padding = value
            }

            /**
             * Sets padding of the line.
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
             * Sets border of the line using color specified by color int and stroke width.
             *
             * @param color color of border
             * @param width stroke width, in pixels
             *
             * @return reference to this object
             */
            fun border(@ColorInt color: Int, width: Float) = border(Border(color, width))

            /**
             * Sets border of the line using color specified by color long and stroke width.
             *
             * Supported when API level is 26 and higher,
             * but actually color longs are used when API level is 29 or higher because
             * color long support was added to [Paint] in Android 10 (API level 29).
             * So before it, [color] is converted to color int and used as such.
             *
             * @param color color of border
             * @param width stroke width, in pixels
             *
             * @return reference to this object
             */
            @RequiresApi(26)
            fun border(@ColorLong color: Long, width: Float) = border(Border(color, width))

            /**
             * Sets border of the line.
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
             * Sets text inside the line.
             *
             * @param text text to be set
             * @param textSize size (in pixels) of the text
             * @param textColor color of the text
             *
             * @return reference to this object
             */
            fun text(
                text: String,
                textSize: Float,
                @ColorInt textColor: Int
            ) = apply {
                this.text = text
                this.textSize = textSize
                this.textColor = textColor
            }

            /**
             * Sets [Typeface] of the text inside the line.
             *
             * @param value [Typeface] to be set
             *
             * @return reference to this object
             */
            fun textTypeface(value: Typeface?) = apply {
                textTypeface = value
            }

            /**
             * Sets horizontal alignment of the text inside the line.
             *
             * @return reference to this object
             */
            fun textAlignment(value: HorizontalAlignment) = apply {
                textAlignment = value
            }

            /**
             * Sets whether to clip the text to bounds of the line.
             *
             * @return reference to this object
             */
            fun clipTextToBounds(value: Boolean) = apply {
                clipTextToBounds = value
            }

            /**
             * Builds [Style] object.
             */
            fun build() = Style(
                fill,
                roundRadius, roundRadii,
                width, height,
                horizontalAlignment,
                animationStartPosition,
                padding,
                border, borderAnimationType,
                text, textTypeface, textSize, textColor,
                textAlignment,
                clipTextToBounds
            )
        }
    }

    companion object {
        /**
         * Linearly interpolates rectangle from one with zero width to specified bounds
         */
        private fun lerpRectHorizontal(
            bounds: PackedRectF,
            fraction: Float,
            startPosition: AnimationStartPosition
        ): PackedRectF {
            val boundsLeft = bounds.left
            val boundsRight = bounds.right

            val left: Float
            val right: Float

            when (startPosition) {
                AnimationStartPosition.Left -> {
                    left = boundsLeft
                    right = lerp(left, boundsRight, fraction)
                }
                AnimationStartPosition.Center -> {
                    val animatedHalfWidth = (boundsRight - boundsLeft) * fraction * 0.5f
                    val centerX = (boundsLeft + boundsRight) * 0.5f

                    left = centerX - animatedHalfWidth
                    right = centerX + animatedHalfWidth
                }
                AnimationStartPosition.Right -> {
                    left = lerp(boundsRight, boundsLeft, fraction)
                    right = boundsRight
                }
            }

            return bounds.withLeftAndRight(left, right)
        }
    }
}