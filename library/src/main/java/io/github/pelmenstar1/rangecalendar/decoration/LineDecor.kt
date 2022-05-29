package io.github.pelmenstar1.rangecalendar.decoration

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import io.github.pelmenstar1.rangecalendar.*
import io.github.pelmenstar1.rangecalendar.PackedRectF
import io.github.pelmenstar1.rangecalendar.PackedRectFArray
import io.github.pelmenstar1.rangecalendar.utils.addRoundRectCompat
import io.github.pelmenstar1.rangecalendar.utils.drawRoundRectCompat
import io.github.pelmenstar1.rangecalendar.utils.lerp
import kotlin.math.max

class LineDecor(val style: Style) : CellDecor() {
    override fun visual(): Visual = LineVisual

    private open class LineVisualState(
        val linesBoundsArray: PackedRectFArray,
        val styles: Array<Style>
    ) : VisualState {
        override val isEmpty: Boolean
            get() = linesBoundsArray.isEmpty

        override fun visual(): Visual = LineVisual
    }

    private class TransitiveAdditionLineVisualState(
        private val start: LineVisualState,
        override val end: LineVisualState,
        private val affectedRangeStart: Int,
        private val affectedRangeEnd: Int
    ): LineVisualState(PackedRectFArray(end.linesBoundsArray.size), end.styles), VisualState.Transitive {
        override fun handleAnimation(
            animationFraction: Float,
            fractionInterpolator: DecorAnimationFractionInterpolator
        ) {
            val startBoundsArray = start.linesBoundsArray
            val endBoundsArray = end.linesBoundsArray
            val affectedRangeLength = affectedRangeEnd - affectedRangeStart + 1

            lerpRectArray(
                startBoundsArray, endBoundsArray, linesBoundsArray,
                0, affectedRangeStart - 1,
                animationFraction
            )

            for(i in affectedRangeStart..affectedRangeEnd) {
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
        private val start: LineVisualState,
        override val end: LineVisualState,
        private val affectedRangeStart: Int,
        private val affectedRangeEnd: Int
    ): LineVisualState(PackedRectFArray(start.linesBoundsArray.size), start.styles), VisualState.Transitive {
        override fun handleAnimation(
            animationFraction: Float,
            fractionInterpolator: DecorAnimationFractionInterpolator
        ) {
            val startBoundsArray = start.linesBoundsArray
            val endBoundsArray = end.linesBoundsArray

            lerpRectArray(
                startBoundsArray, endBoundsArray, linesBoundsArray,
                0, affectedRangeStart - 1,
                animationFraction
            )

            val affectedRangeLength = affectedRangeEnd - affectedRangeStart + 1

            for(i in affectedRangeStart..affectedRangeEnd) {
                val itemFraction = fractionInterpolator.getItemFraction(
                    i - affectedRangeStart, affectedRangeLength, 1f - animationFraction
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
        private val start: LineVisualState,
        override val end: LineVisualState,
        private val affectedRangeStart: Int,
        private val affectedRangeEnd: Int
    ): LineVisualState(PackedRectFArray(start.linesBoundsArray.size), start.styles), VisualState.Transitive {
        override fun handleAnimation(
            animationFraction: Float,
            fractionInterpolator: DecorAnimationFractionInterpolator
        ) {
            lerpRectArray(
                start.linesBoundsArray, end.linesBoundsArray, linesBoundsArray,
                affectedRangeStart, affectedRangeEnd,
                animationFraction
            )
        }
    }

    private object LineVisual: Visual {
        override fun stateHandler(): VisualStateHandler = LineStateHandler
        override fun renderer(): Renderer = LineRenderer
    }

    private object LineStateHandler : VisualStateHandler {
        private val emptyLineState = LineVisualState(PackedRectFArray(0), emptyArray())

        private var defaultDecorBlockPadding: Padding? = null
        private var defaultDecorPadding: Padding? = null

        private fun getDecorBlockPadding(context: Context, padding: Padding?): Padding {
            if(padding == null) {
                var defaultPadding = defaultDecorBlockPadding

                if(defaultPadding == null) {
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
            if(padding == null) {
                var defaultPadding = defaultDecorPadding

                if(defaultPadding == null) {
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

            val length = endInclusive - start + 1

            val linesBounds = PackedRectFArray(length)

            val styles = Array(length) { i ->
                (decorations[start + i] as LineDecor).style
            }

            val textBounds = info.textBounds
            val textBoundsBottom = textBounds.bottom
            val freeAreaHeight = info.size - textBoundsBottom

            // The coefficient is hand-picked for line not to be very thin or thick
            val defaultLineHeight = max(1f, freeAreaHeight / (2.5f * length))

            // Find total height of lines
            var totalHeight = 0f

            for (i in start..endInclusive) {
                val line = decorations[i] as LineDecor
                val style = line.style

                val height = style.height
                val padding = getDecorPadding(context, style.padding)

                totalHeight += height.resolveHeight(defaultLineHeight) + padding.top + padding.bottom
            }

            // Find y of position where to start drawing lines
            var top = when(layoutOptions?.verticalAlignment ?: VerticalAlignment.CENTER) {
                VerticalAlignment.TOP -> textBounds.top + decorBlockPadding.top
                VerticalAlignment.CENTER -> 0.5f * (textBoundsBottom + info.size - totalHeight)
                VerticalAlignment.BOTTOM -> textBoundsBottom - totalHeight - decorBlockPadding.bottom
            }

            for (i in 0 until length) {
                val decor = decorations[start + i] as LineDecor

                val style = decor.style
                val decorPadding = getDecorPadding(context, style.padding)

                top += decorPadding.top

                val height = style.height
                val resolvedHeight = height.resolveHeight(defaultLineHeight)

                // If cell is round, then it should be narrowed to fit the cell shape
                val initialBounds = info.narrowRectOnBottom(
                    PackedRectF(0f, top, info.size, top + resolvedHeight)
                )

                var left = initialBounds.left + decorPadding.left + decorBlockPadding.left
                var right = initialBounds.right - (decorPadding.right + decorBlockPadding.right)

                val width = style.width

                val maxWidth = right - left

                val resolvedWidth = if(width.isNaN() || width >= maxWidth) {
                    maxWidth
                } else {
                    width
                }

                if(resolvedWidth != width) {
                    when(style.horizontalAlignment) {
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

                val bounds = initialBounds.withLeftAndRight(left, right)

                linesBounds[i] = bounds
                style.fill.setBounds(bounds)

                top += resolvedHeight + decorPadding.bottom
            }

            return LineVisualState(linesBounds, styles)
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

            return when(change) {
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

        override fun renderState(
            canvas: Canvas,
            state: VisualState
        ) {
            val lineState = state as LineVisualState
            val linesBounds = lineState.linesBoundsArray

            val styles = lineState.styles

            for (i in 0 until linesBounds.size) {
                val bounds = linesBounds[i]
                val style = styles[i]

                val lineHeight = bounds.height

                style.fill.applyToPaint(paint)

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
                        radii[j] = radii[j].resolveRoundRadius(lineHeight)
                    }

                    path.addRoundRectCompat(bounds, radii)

                    canvas.drawPath(path, paint)
                } else {
                    val roundRadius = style.roundRadius.resolveRoundRadius(lineHeight)

                    canvas.drawRoundRectCompat(bounds, roundRadius, paint)
                }
            }
        }

        private fun Float.resolveRoundRadius(alternative: Float): Float {
            val half = alternative * 0.5f

            return if (this > half) half else this
        }
    }

    enum class AnimationStartPosition {
        Left,
        Center,
        Right
    }

    class Style private constructor(
        val fill: Fill,
        val roundRadius: Float,
        val roundRadii: FloatArray?,
        val width: Float,
        val height: Float,
        val horizontalAlignment: HorizontalAlignment,
        val animationStartPosition: AnimationStartPosition,
        val padding: Padding?
    ) {
        class Builder(private var fill: Fill) {
            private var roundRadius: Float = 0f
            private var roundRadii: FloatArray? = null
            private var horizontalAlignment = HorizontalAlignment.CENTER
            private var width: Float = Float.NaN
            private var height: Float = Float.NaN
            private var animationStartPosition = AnimationStartPosition.Center
            private var padding: Padding? = null

            fun fill(value: Fill) = apply {
                fill = value
            }

            fun animationStartPosition(value: AnimationStartPosition) = apply {
                animationStartPosition = value
            }

            fun roundedCorners(radius: Float = Float.POSITIVE_INFINITY) = apply {
                require(radius >= 0f) { "Round radius can't be negative" }

                roundRadius = radius
                roundRadii = null
            }

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

            fun horizontalAlignment(value: HorizontalAlignment) = apply {
                horizontalAlignment = value
            }

            fun width(value: Float) = apply {
                require(value > 0f) { "Width can't be negative or zero" }

                width = value
            }

            fun height(value: Float) = apply {
                require(value > 0f) { "Height can't be negative or zero" }

                height = value
            }

            fun padding(value: Padding?) = apply {
                padding = value
            }

            fun padding(
                left: Float = 0f,
                top: Float = 0f,
                right: Float = 0f,
                bottom: Float = 0f
            ) = padding(Padding(left, top, right, bottom))

            fun build() = Style(
                fill,
                roundRadius, roundRadii,
                width, height,
                horizontalAlignment,
                animationStartPosition,
                padding
            )
        }
    }

    companion object {
        private fun lerpRectArray(
            start: PackedRectFArray, end: PackedRectFArray, outArray: PackedRectFArray,
            startIndex: Int, endIndexInclusive: Int,
            fraction: Float,
            startOffset: Int = 0, endOffset: Int = 0
        ) {
            for(i in startIndex..endIndexInclusive) {
                outArray[i] = lerp(
                    start[i - startOffset],
                    end[i - endOffset],
                    fraction
                )
            }
        }

        private fun lerpRectHorizontal(
            bounds: PackedRectF,
            fraction: Float,
            startPosition: AnimationStartPosition
        ): PackedRectF {
            val boundsLeft = bounds.left
            val boundsRight = bounds.right

            val left: Float
            val right: Float

            when(startPosition) {
                AnimationStartPosition.Left -> {
                    left = boundsLeft
                    right = lerp(left, boundsRight, fraction)
                }
                AnimationStartPosition.Center -> {
                    val animatedHalfWidth = (boundsRight - boundsLeft) * fraction
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