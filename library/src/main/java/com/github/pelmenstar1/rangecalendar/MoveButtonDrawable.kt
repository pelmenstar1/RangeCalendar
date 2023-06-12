package com.github.pelmenstar1.rangecalendar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.Choreographer
import android.view.Choreographer.FrameCallback
import androidx.annotation.RestrictTo
import androidx.core.graphics.alpha
import com.github.pelmenstar1.rangecalendar.utils.colorLerp
import com.github.pelmenstar1.rangecalendar.utils.lerp
import com.github.pelmenstar1.rangecalendar.utils.withAlpha

/**
 * A [Drawable] object that draws an arrow. The arrow can also be transitioned to the 'close' icon.
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
    private val arrowPaint: Paint
    private var arrowColor: Int
    private var arrowSize = 0f
    private val arrowStrokeWidth: Float

    private var arrowUsePath = false
    private val arrowPath = Path()
    private val arrowLinePoints = FloatArray(4)

    private var arrowAnimFraction = 0f

    private var colorAnimDurationNanos = 0L
    private var colorAnimStartTime = 0L

    private var colorAnimStartColor = 0
    private var colorAnimEndColor = 0
    private var colorAnimIsRunning = false
    private var colorAnimIsInterrupted = false

    private val colorAnimTickCallback = FrameCallback { nanos -> onStateChangeAnimTick(nanos) }
    private val startColorAnimCallback = FrameCallback { time ->
        colorAnimStartTime = time

        setPaintColor(colorAnimStartColor)
        choreographer.postFrameCallback(colorAnimTickCallback)
    }

    init {
        val res = context.resources

        arrowStrokeWidth = res.getDimension(R.dimen.rangeCalendar_arrowStrokeWidth)
        arrowSize = res.getDimension(R.dimen.rangeCalendar_arrowSize)

        arrowColor = colorList.getColorForState(ENABLED_STATE, 0)

        arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = arrowColor
            strokeWidth = arrowStrokeWidth
        }
    }

    fun setAnimationFraction(fraction: Float) {
        arrowAnimFraction = fraction

        computeLinePoints()
        invalidateSelf()
    }

    fun setStateChangeDuration(millis: Long) {
        colorAnimDurationNanos = millis * 1_000_000
    }

    fun setArrowSize(value: Float) {
        arrowSize = value

        computeLinePoints()
        invalidateSelf()
    }

    private fun onStateChangeAnimTick(nanos: Long) {
        if (colorAnimIsInterrupted) {
            colorAnimIsInterrupted = false
            colorAnimStartTime = nanos
        }

        val fraction = (nanos - colorAnimStartTime).toFloat() / colorAnimDurationNanos

        if (fraction >= 1f) {
            colorAnimIsRunning = false

            onStateChangeAnimTickFraction(1f)
        } else {
            onStateChangeAnimTickFraction(fraction)

            choreographer.postFrameCallback(colorAnimTickCallback)
        }
    }

    private fun onStateChangeAnimTickFraction(fraction: Float) {
        val c = colorLerp(colorAnimStartColor, colorAnimEndColor, fraction)

        setPaintColor(c)
    }

    private fun startStateChangeAnimation(startColor: Int, endColor: Int) {
        colorAnimStartColor = startColor
        colorAnimEndColor = endColor

        if (colorAnimIsRunning) {
            colorAnimIsInterrupted = true
        } else {
            colorAnimIsRunning = true
            choreographer.postFrameCallback(startColorAnimCallback)
        }
    }

    private fun setPaintColor(color: Int) {
        arrowPaint.color = color

        invalidateSelf()
    }

    private fun computeLinePoints() {
        val bounds = bounds
        val fraction = arrowAnimFraction
        val path = arrowPath
        val linePoints = arrowLinePoints

        val halfArrowSize = arrowSize * 0.5f

        val midX = (bounds.left + bounds.right) * 0.5f
        val midY = (bounds.top + bounds.bottom) * 0.5f

        val actualLeft = midX - halfArrowSize
        val actualTop = midY - halfArrowSize
        val actualRight = midX + halfArrowSize
        val actualBottom = midY + halfArrowSize

        val anchorX: Float
        val invAnchorX: Float

        if (direction == DIRECTION_LEFT)  {
            anchorX = actualRight
            invAnchorX = actualLeft
        }  else {
            anchorX = actualLeft
            invAnchorX = actualRight
        }

        if (animationType == ANIM_TYPE_ARROW_TO_CLOSE) {
            if (fraction == 0f) {
                arrowUsePath = true
                path.apply {
                    rewind()

                    moveTo(anchorX, actualTop)
                    lineTo(midX, midY)
                    lineTo(anchorX, actualBottom)
                }
            } else {
                val delta = halfArrowSize * fraction

                val line1EndY = midY + delta
                val line2EndY = midY - delta

                val lineEndX = lerp(midX, invAnchorX, fraction)

                arrowUsePath = true

                path.apply {
                    rewind()

                    moveTo(anchorX, actualTop)
                    lineTo(lineEndX, line1EndY)

                    moveTo(anchorX, actualBottom)
                    lineTo(lineEndX, line2EndY)
                }
            }
        } else {
            if (fraction <= 0.5f) {
                val scaledFraction = fraction * 2f

                val lineEndX = lerp(anchorX, midX, scaledFraction)
                val lineEndY = lerp(actualBottom, midY, scaledFraction)

                arrowUsePath = false

                linePoints[0] = anchorX
                linePoints[1] = actualBottom
                linePoints[2] = lineEndX
                linePoints[3] = lineEndY
            } else {
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
            startStateChangeAnimation(oldColor, newColor)

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
    }

    override fun setAlpha(alpha: Int) {
        setPaintColor(arrowColor.withAlpha(alpha))
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
        const val DIRECTION_LEFT = 0
        const val DIRECTION_RIGHT = 1

        const val ANIM_TYPE_ARROW_TO_CLOSE = 0
        const val ANIM_TYPE_VOID_TO_ARROW = 1

        private const val MIN_VISIBLE_ALPHA = 40

        private val ENABLED_STATE = intArrayOf(android.R.attr.state_enabled)
        private val choreographer = Choreographer.getInstance()
    }
}