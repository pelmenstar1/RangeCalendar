package com.github.pelmenstar1.rangecalendar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.annotation.RestrictTo
import androidx.core.graphics.alpha
import com.github.pelmenstar1.rangecalendar.utils.lerp
import com.github.pelmenstar1.rangecalendar.utils.withCombinedAlpha

/**
 * A [Drawable] object that draws an arrow. The arrow can also be transitioned to a cross.
 *
 * The class is not expected to be used outside the library.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
class MoveButtonDrawable(
    context: Context,
    private val colorList: ColorStateList,
    private val direction: Int,
    private val animationType: Int
) : Drawable() {
    internal val arrowPaint: Paint

    private var arrowColor: Int
    private var arrowColorAlpha = 1f

    private var arrowSize = 0f
    private var arrowStrokeWidth: Float

    internal var arrowUsePath = false
    internal val arrowPath = Path()
    internal val arrowLinePoints = FloatArray(4)

    private var arrowAnimFraction = 0f
    private val colorAnimator = MoveButtonDrawableColorAnimator(
        colorCallback = ::setPaintColor
    )

    init {
        val res = context.resources

        arrowStrokeWidth = res.getDimension(R.dimen.rangeCalendar_arrowStrokeWidth)
        arrowSize = res.getDimension(R.dimen.rangeCalendar_arrowSize)

        arrowColor = colorList.getColorForState(ENABLED_STATE, colorList.defaultColor)

        arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = arrowColor
            strokeWidth = arrowStrokeWidth

            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    }

    fun setAnimationFraction(fraction: Float) {
        arrowAnimFraction = fraction

        computeLinePoints()
        invalidateSelf()
    }

    fun setStateChangeDuration(millis: Long) {
        colorAnimator.duration = millis
    }

    fun setArrowSize(value: Float) {
        arrowSize = value

        computeLinePoints()
        invalidateSelf()
    }

    fun setArrowStrokeWidth(value: Float) {
        arrowStrokeWidth = value
        arrowPaint.strokeWidth = value

        computeLinePoints()
        invalidateSelf()
    }

    private fun setPaintColor(color: Int) {
        val newColor = color.withCombinedAlpha(arrowColorAlpha)

        if (arrowPaint.color != newColor) {
            arrowPaint.color = newColor

            invalidateSelf()
        }
    }

    private fun computeLinePoints() {
        val bounds = bounds
        if (bounds.isEmpty) {
            return
        }

        val fraction = arrowAnimFraction
        val path = arrowPath
        val linePoints = arrowLinePoints

        val adjOffset = arrowStrokeWidth * 0.5f
        val halfArrowSize = arrowSize * 0.5f
        val adjHalfArrowSize = halfArrowSize - adjOffset

        val midX = (bounds.left + bounds.right) * 0.5f
        val midY = (bounds.top + bounds.bottom) * 0.5f

        val actualLeft = midX - adjHalfArrowSize
        val actualTop = midY - adjHalfArrowSize
        val actualRight = midX + adjHalfArrowSize
        val actualBottom = midY + adjHalfArrowSize

        val anchorX: Float
        val invAnchorX: Float

        if (direction == DIRECTION_LEFT) {
            anchorX = actualRight
            invAnchorX = actualLeft
        } else {
            anchorX = actualLeft
            invAnchorX = actualRight
        }

        // The drawable is animated from the arrow to the cross.
        if (animationType == ANIM_TYPE_ARROW_TO_CROSS) {
            arrowUsePath = true

            // If fraction is 0, it means that we're drawing simple arrow. We should render the arrow as
            // a polygonal chain. Rendering it as two discrete lines is not perfect as there will be an artefact
            // where the lines connect.
            if (fraction == 0f) {
                path.apply {
                    rewind()

                    moveTo(anchorX, actualTop)
                    lineTo(midX, midY)
                    lineTo(anchorX, actualBottom)
                }
            } else {
                // Gradually animate the arrow to the cross.
                val delta = adjHalfArrowSize * fraction

                val line1EndY = midY + delta
                val line2EndY = midY - delta

                val lineEndX = lerp(midX, invAnchorX, fraction)

                path.apply {
                    rewind()

                    moveTo(anchorX, actualTop)
                    lineTo(lineEndX, line1EndY)

                    moveTo(anchorX, actualBottom)
                    lineTo(lineEndX, line2EndY)
                }
            }
        } else {
            // The drawable is animated from void (nothing) to the arrow.
            if (fraction <= 0.5f) {
                // If fraction <= 0.5, it means that we have a line that starts from the bottom (left/right depending on direction)
                // and the end of the line is animated to the center.

                // When fraction = 0.5, the end of the line should be at the center.
                // So we need to scale the fraction from [0; 0.5] to [0; 1]
                val scaledFraction = fraction * 2f

                val lineEndX = lerp(anchorX, midX, scaledFraction)
                val lineEndY = lerp(actualBottom, midY, scaledFraction)

                // We can simply draw a line instead of using Path.
                arrowUsePath = false

                linePoints[0] = anchorX
                linePoints[1] = actualBottom
                linePoints[2] = lineEndX
                linePoints[3] = lineEndY
            } else {
                // Now we have a line that starts from the bottom (left/right depending on the direction) and ends in the center
                // and a line that starts from the center and the end of that line is animated to the top (left/right depending on the direction)

                // Scale the fraction from [0.5; 1] to [0; 1]
                val scaledFraction = fraction * 2f - 1f

                val lineEndX = lerp(midX, anchorX, scaledFraction)
                val lineEndY = lerp(midY, actualTop, scaledFraction)

                arrowUsePath = true

                path.apply {
                    rewind()

                    moveTo(anchorX, actualBottom)
                    lineTo(midX, midY)
                    lineTo(lineEndX, lineEndY)
                }
            }
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        computeLinePoints()
    }

    override fun onStateChange(state: IntArray): Boolean {
        val oldColor = arrowColor
        val newColor = colorList.getColorForState(state, colorList.defaultColor)

        return if (oldColor != newColor) {
            arrowColor = newColor
            colorAnimator.start(oldColor, newColor)

            true
        } else false
    }

    override fun isStateful() = true

    override fun draw(c: Canvas) {
        val paint = arrowPaint

        if (arrowUsePath) {
            c.drawPath(arrowPath, paint)
        } else {
            val points = arrowLinePoints

            c.drawLine(points[0], points[1], points[2], points[3], paint)
        }

        if (RENDER_BOUNDS) {
            // It's only used for debugging purposes. Allocating is OK.
            val infoPaint = Paint().apply {
                style = Paint.Style.STROKE
                color = Color.RED
                strokeWidth = 1f
            }

            val bounds = bounds

            val cx = (bounds.left + bounds.right) * 0.5f
            val cy = (bounds.top + bounds.bottom) * 0.5f

            val halfSize = arrowSize * 0.5f

            val left = cx - halfSize
            val top = cy - halfSize
            val right = cx + halfSize
            val bottom = cy + halfSize

            c.drawRect(left, top, right, bottom, infoPaint)
            c.drawLine(left, top, right, bottom, infoPaint)
            c.drawLine(right, top, left, bottom, infoPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        setAlpha(alpha / 255f)
    }

    fun setAlpha(alpha: Float) {
        if (arrowColorAlpha != alpha) {
            arrowColorAlpha = alpha

            colorAnimator.alpha = alpha

            // arrowColorAlpha is changed, paint color should be updated too.
            setPaintColor(arrowColor)
        }
    }

    override fun getColorFilter(): ColorFilter? = arrowPaint.colorFilter

    override fun setColorFilter(colorFilter: ColorFilter?) {
        arrowPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int {
        return when (arrowColor.alpha) {
            0 -> PixelFormat.TRANSPARENT
            255 -> PixelFormat.OPAQUE
            else -> PixelFormat.TRANSLUCENT
        }
    }

    companion object {
        private const val RENDER_BOUNDS = false

        const val DIRECTION_LEFT = 0
        const val DIRECTION_RIGHT = 1

        const val ANIM_TYPE_ARROW_TO_CROSS = 0
        const val ANIM_TYPE_VOID_TO_ARROW = 1

        private val ENABLED_STATE = intArrayOf(android.R.attr.state_enabled)
    }
}