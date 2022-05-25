package io.github.pelmenstar1.rangecalendar.decoration

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.annotation.ColorInt
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

    private object LineVisual: Visual {
        override fun stateHandler(): VisualStateHandler = LineStateHandler
        override fun renderer(): Renderer = LineRenderer
    }

    private object LineStateHandler : VisualStateHandler {
        private val tempRect = RectF()
        private val emptyLineState = LineVisualState(PackedRectFArray(0), emptyArray())

        override fun emptyState(): VisualState = emptyLineState

        override fun createState(
            context: Context,
            decorations: Array<out CellDecor>,
            start: Int,
            endInclusive: Int,
            info: CellInfo
        ): VisualState {
            val res = context.resources
            val lineMarginTop = res.getDimension(R.dimen.rangeCalendar_stripeMarginTop)
            val lineHorizontalMargin =
                res.getDimension(R.dimen.rangeCalendar_stripeHorizontalMargin)

            val length = endInclusive - start + 1

            val linesBounds = PackedRectFArray(length)

            val styles = Array(length) { i ->
                (decorations[start + i] as LineDecor).style
            }

            info.getTextBounds(tempRect)
            val freeAreaHeight = info.size - tempRect.bottom

            // The coefficient is hand-picked for line not to be very thin or thick
            val defaultLineHeight = max(1f, freeAreaHeight / (2.5f * length))

            // Find total height of lines
            var totalHeight = 0f

            for (i in start..endInclusive) {
                val line = decorations[i] as LineDecor
                val height = line.style.height

                totalHeight += height.resolveHeight(defaultLineHeight)

                if (i < endInclusive) {
                    totalHeight += lineMarginTop
                }
            }

            // Find y of position where to start drawing lines
            var top = 0.5f * (tempRect.bottom + info.size - totalHeight)

            for (i in 0 until length) {
                val decor = decorations[start + i] as LineDecor

                tempRect.set(
                    lineHorizontalMargin,
                    top,
                    info.size - lineHorizontalMargin,
                    top + defaultLineHeight
                )

                // If cell is round, then it should be narrowed to fit the cell shape
                info.narrowRectOnBottom(tempRect)

                val height = decor.style.height
                val resolvedHeight = height.resolveHeight(defaultLineHeight)

                val bounds = PackedRectF(
                    tempRect.left, top, tempRect.right, top + resolvedHeight
                )

                linesBounds[i] = bounds

                top += resolvedHeight + lineMarginTop
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

                paint.color = style.color

                if (style.isRoundRadiiMode) {
                    var path = cachedPath
                    if (path == null) {
                        path = Path()
                        cachedPath = path
                    } else {
                        path.rewind()
                    }

                    val radii = style.roundRadii!!
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

    class Style constructor(@ColorInt color: Int) {
        var color: Int = color
            private set

        var roundRadius: Float = 0f
            private set

        var roundRadii: FloatArray? = null
            private set

        var height: Float = Float.NaN
            private set

        var animationStartPosition: AnimationStartPosition = AnimationStartPosition.Center
            private set

        internal var isRoundRadiiMode = false

        fun withHeight(height: Float): Style = apply {
            this.height = height
        }

        fun withAnimationStartPosition(pos: AnimationStartPosition) = apply {
            animationStartPosition = pos
        }

        @JvmOverloads
        fun withRoundedCorners(radius: Float = Float.POSITIVE_INFINITY): Style {
            return apply {
                roundRadius = radius
                isRoundRadiiMode = false
            }
        }

        fun withRoundedCorners(
            leftTop: Float = Float.POSITIVE_INFINITY,
            rightTop: Float = Float.POSITIVE_INFINITY,
            rightBottom: Float = Float.POSITIVE_INFINITY,
            leftBottom: Float = Float.POSITIVE_INFINITY
        ): Style {
            return apply {
                var radii = roundRadii

                if (radii == null) {
                    radii = FloatArray(8)
                    roundRadii = radii
                }

                isRoundRadiiMode = true

                radii[0] = leftTop
                radii[1] = leftTop
                radii[2] = rightTop
                radii[3] = rightTop
                radii[4] = rightBottom
                radii[5] = rightBottom
                radii[6] = leftBottom
                radii[7] = leftBottom
            }
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