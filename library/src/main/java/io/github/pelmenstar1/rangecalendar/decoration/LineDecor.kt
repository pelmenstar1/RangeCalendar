package io.github.pelmenstar1.rangecalendar.decoration

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.annotation.ColorInt
import android.graphics.RectF
import io.github.pelmenstar1.rangecalendar.PackedIntRange
import io.github.pelmenstar1.rangecalendar.PackedRectF
import io.github.pelmenstar1.rangecalendar.PackedRectFArray
import io.github.pelmenstar1.rangecalendar.R
import io.github.pelmenstar1.rangecalendar.utils.addRoundRectCompat
import io.github.pelmenstar1.rangecalendar.utils.drawRoundRectCompat
import io.github.pelmenstar1.rangecalendar.utils.lerp
import kotlin.math.max

class LineDecor(val style: Style) : CellDecor() {
    override fun stateHandler(): StateHandler = LineStateHandler
    override fun renderer(): Renderer = LineRenderer

    private object LineStateHandler : StateHandler {
        override fun createState(
            context: Context,
            decorations: Array<out CellDecor>,
            start: Int,
            endInclusive: Int,
            info: CellInfo
        ): VisualState {
            return LineVisualState(context, decorations, PackedIntRange(start, endInclusive), info)
        }
    }

    private class LineVisualState(
        context: Context,
        decorations: Array<out CellDecor>,
        range: PackedIntRange,
        info: CellInfo
    ): VisualState {
        val initialLinesBounds: PackedRectFArray
        val animatedLinesBounds: PackedRectFArray

        val styles: Array<Style>

        val size = info.size
        val defaultLineHeight: Float

        init {
            val res = context.resources
            val lineMarginTop = res.getDimension(R.dimen.rangeCalendar_stripeMarginTop)
            val lineHorizontalMargin = res.getDimension(R.dimen.rangeCalendar_stripeHorizontalMargin)

            val start = range.start
            val endInclusive = range.endInclusive

            val length = endInclusive - start + 1

            initialLinesBounds = PackedRectFArray(length)
            animatedLinesBounds = PackedRectFArray(length)

            styles = Array(length) { i ->
                (decorations[start + i] as LineDecor).style
            }

            info.getTextBounds(tempRect)
            val freeAreaHeight = info.size - tempRect.bottom

            // The coefficient is hand-picked for line not to be very thin or thick
            defaultLineHeight = max(1f, freeAreaHeight / (2.5f * length))

            // Find total height of lines
            var totalHeight = 0f

            for(i in start..endInclusive) {
                val line = decorations[i] as LineDecor
                val height = line.style.height

                totalHeight += height.resolveHeight(defaultLineHeight)

                if(i < endInclusive) {
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
                    tempRect.left, tempRect.top, tempRect.right, tempRect.top + resolvedHeight
                )

                initialLinesBounds[i] = bounds
                animatedLinesBounds[i] = bounds

                top += resolvedHeight + lineMarginTop
            }
        }

        override fun handleAnimation(
            start: Int,
            endInclusive: Int,
            animationFraction: Float,
            fractionInterpolator: DecorAnimationFractionInterpolator
        ) {
            val length = endInclusive - start + 1

            for(i in start..endInclusive) {
                val initialBounds = initialLinesBounds[i]
                val style = styles[i]

                val animFraction = fractionInterpolator.getItemFraction(
                    i - start, length, animationFraction
                )

                val left: Float
                val right: Float

                when(style.animationStartPosition) {
                    AnimationStartPosition.Left -> {
                        left = initialBounds.left
                        right = lerp(left, initialBounds.right, animFraction)
                    }
                    AnimationStartPosition.Center -> {
                        val centerX = size * 0.5f
                        val animatedHalfWidth = initialBounds.width * animFraction

                        left = centerX - animatedHalfWidth
                        right = centerX + animatedHalfWidth
                    }
                    AnimationStartPosition.Right -> {
                        left = lerp(initialBounds.right, initialBounds.left, animFraction)
                        right = initialBounds.right
                    }
                }

                animatedLinesBounds[i] = PackedRectF(left, initialBounds.top, right, initialBounds.bottom)
            }
        }

        companion object {
            private val tempRect = RectF()

            private fun Float.resolveHeight(default: Float): Float {
                return if(isNaN()) default else this
            }
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
            val linesBounds = lineState.animatedLinesBounds

            val styles = lineState.styles
            val defaultLineHeight = lineState.defaultLineHeight

            for(i in 0 until linesBounds.size) {
                val bounds = linesBounds[i]
                val style = styles[i]

                paint.color = style.color

                if(style.isRoundRadiiMode) {
                    var path = cachedPath
                    if(path == null) {
                        path = Path()
                        cachedPath = path
                    } else {
                        path.rewind()
                    }

                    val radii = style.roundRadii!!
                    for(j in radii.indices) {
                        radii[j] = radii[j].resolveRoundRadius(defaultLineHeight)
                    }

                    path.addRoundRectCompat(bounds, radii)

                    canvas.drawPath(path, paint)
                } else {
                    val roundRadius = style.roundRadius.resolveRoundRadius(defaultLineHeight)

                    canvas.drawRoundRectCompat(bounds, roundRadius, paint)
                }
            }
        }

        private fun Float.resolveRoundRadius(height: Float): Float {
            val half = height * 0.5f

            return if(this > half) half else this
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

                if(radii == null) {
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
}