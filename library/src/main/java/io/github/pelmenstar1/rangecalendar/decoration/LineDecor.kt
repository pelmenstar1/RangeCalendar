package io.github.pelmenstar1.rangecalendar.decoration

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.annotation.ColorInt
import android.graphics.RectF
import io.github.pelmenstar1.rangecalendar.R
import kotlin.math.max

class LineDecor(val style: Style) : CellDecor<LineDecor>() {
    override fun newRenderer(context: Context): CellDecorRenderer<LineDecor> = Renderer(context)

    private class Renderer(context: Context) : CellDecorRenderer<LineDecor> {
        private val stripeMarginTop: Float
        private val stripeHorizontalMargin: Float
        private val paint: Paint

        init {
            val res = context.resources
            stripeMarginTop = res.getDimension(R.dimen.rangeCalendar_stripeMarginTop)
            stripeHorizontalMargin = res.getDimension(R.dimen.rangeCalendar_stripeHorizontalMargin)

            paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
        }

        override fun decorationClass() = LineDecor::class.java

        private fun Float.resolveRoundRadius(height: Float): Float {
            val half = height * 0.5f

            return if(this > half) half else this
        }

        override fun render(
            canvas: Canvas,
            decorations: Array<out CellDecor<*>>,
            start: Int, endInclusive: Int,
            info: CellInfo,
        ) {
            val length = endInclusive - start + 1

            // The coefficient is hand-picked for line not to be very thin or thick
            val lineHeight = max(1f, info.size * (1f / 12f))

            // Find half of total height of lines
            val halfTotalHeight = (lineHeight + stripeMarginTop) * length * 0.5f

            // Find y of position where to start drawing lines
            info.getTextBounds(tempRect)

            val bottomCenterY = (tempRect.bottom + info.size) * 0.5f
            var top = bottomCenterY - halfTotalHeight

            val centerX = info.size * 0.5f

            for (i in start..endInclusive) {
                val decor = decorations[i] as LineDecor
                val animFraction = decor.animationFraction

                tempRect.set(0f, top, info.size, top + lineHeight)

                // If cell is round, then it should be narrowed to fit the cell shape
                info.narrowRectOnBottom(tempRect)

                val halfWidth = tempRect.width() * 0.5f - stripeHorizontalMargin

                val animatedHalfWidth = halfWidth * animFraction

                // The line is animated starting from its center
                val stripeLeft = centerX - animatedHalfWidth
                val stripeRight = centerX + animatedHalfWidth

                tempRect.set(stripeLeft, top, stripeRight, top + lineHeight)

                val style = decor.style

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
                        radii[j] = radii[j].resolveRoundRadius(lineHeight)
                    }

                    path.addRoundRect(tempRect, radii, Path.Direction.CW)

                    canvas.drawPath(path, paint)
                } else {
                    val roundRadius = style.roundRadius.resolveRoundRadius(lineHeight)

                    canvas.drawRoundRect(tempRect, roundRadius, roundRadius, paint)
                }

                top += lineHeight + stripeMarginTop
            }
        }

        companion object {
            private val tempRect = RectF()

            private var cachedPath: Path? = null
        }
    }

    class Style constructor(@ColorInt color: Int) {
        var color: Int = color
            private set

        var roundRadius: Float = 0f
            private set

        var roundRadii: FloatArray? = null
            private set

        internal var isRoundRadiiMode = false

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