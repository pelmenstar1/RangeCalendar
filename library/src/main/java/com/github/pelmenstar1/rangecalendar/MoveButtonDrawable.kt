package com.github.pelmenstar1.rangecalendar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.Choreographer
import android.view.Choreographer.FrameCallback
import androidx.annotation.RestrictTo
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
    private var arrowSize: Int = 0
    private val arrowStrokeWidth: Float

    private val linePoints = FloatArray(8)
    private var linePointsLength = 8

    private var arrowAnimFraction = 0f

    private var colorAnimDuration = 0L
    private var colorAnimStartTime = 0L

    private var colorAnimStartColor = 0
    private var colorAnimEndColor = 0
    private var colorAnimIsRunning = false
    private var colorAnimIsInterrupted = false

    private val colorAnimTickCallback = FrameCallback { time -> onStateChangeAnimTick(time) }
    private val startColorAnimCallback = FrameCallback { time ->
        colorAnimStartTime = time

        setPaintColor(colorAnimStartColor)
        choreographer.postFrameCallback(colorAnimTickCallback)
    }

    init {
        val res = context.resources

        arrowStrokeWidth = res.getDimension(R.dimen.rangeCalendar_arrowStrokeWidth)
        arrowSize = res.getDimensionPixelSize(R.dimen.rangeCalendar_arrowSize)

        arrowColor = colorList.getColorForState(ENABLED_STATE, 0)

        arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
        colorAnimDuration = millis
    }

    fun setArrowSize(value: Int) {
        arrowSize = value

        computeLinePoints()
        invalidateSelf()
    }

    private fun onStateChangeAnimTick(time: Long) {
        if (colorAnimIsInterrupted) {
            colorAnimIsInterrupted = false
            colorAnimStartTime = time
        }

        val fraction = (time - colorAnimStartTime).toFloat() / colorAnimDuration

        if (fraction >= 1f) {
            colorAnimIsRunning = false

            onStateChangeAnimTickFraction(1f)
        } else {
            arrowAnimFraction = fraction
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
            choreographer.postFrameCallback(startColorAnimCallback)
        }
    }

    private fun setPaintColor(color: Int) {
        arrowPaint.color = color

        invalidateSelf()
    }

    private fun computeLinePoints() {
        val bounds = bounds

        val boundsLeft = bounds.left
        val boundsTop = bounds.top
        val boundsWidth = bounds.width()
        val boundsHeight = bounds.height()

        val actualLeft = (boundsLeft + (boundsWidth - arrowSize) / 2).toFloat()
        val actualTop = (boundsTop + (boundsHeight - arrowSize) / 2).toFloat()
        val actualRight = actualLeft + arrowSize
        val actualBottom = actualTop + arrowSize

        val midX = (boundsLeft + boundsWidth / 2).toFloat()
        val midY = (boundsTop + boundsHeight / 2).toFloat()

        val fraction = arrowAnimFraction
        val halfStrokeWidth = arrowStrokeWidth * 0.5f

        val points = linePoints
        if (animationType == ANIM_TYPE_ARROW_TO_CLOSE) {
            val line1EndY = lerp(midY - halfStrokeWidth + 0.5f, actualBottom, fraction)
            val line2EndY = lerp(midY - halfStrokeWidth, actualTop, fraction)

            val line1EndX: Float
            val line2EndX: Float
            val anchorX: Float

            if (direction == DIRECTION_RIGHT) {
                line1EndX = lerp(midX - halfStrokeWidth + 1, actualRight, fraction)
                line2EndX = lerp(midX + halfStrokeWidth, actualRight, fraction)
                anchorX = actualLeft
            } else {
                line1EndX = lerp(midX - halfStrokeWidth, actualLeft, fraction)
                line2EndX = lerp(midX - halfStrokeWidth, actualLeft, fraction)
                anchorX = actualRight
            }

            points[0] = anchorX
            points[1] = actualTop
            points[2] = line1EndX
            points[3] = line1EndY
            points[4] = anchorX
            points[5] = actualBottom
            points[6] = line2EndX
            points[7] = line2EndY
        } else {
            val anchorX = if (direction == DIRECTION_LEFT) actualRight else actualLeft

            if (fraction <= 0.5f) {
                val sFr = fraction * 2f

                points[0] = anchorX
                points[1] = actualBottom
                points[2] = lerp(anchorX, midX, sFr)
                points[3] = lerp(actualBottom, midY, sFr)

                linePointsLength = 4
            } else {
                val sFr = fraction * 2f - 1f
                points[0] = anchorX
                points[1] = actualBottom
                points[2] = midX - halfStrokeWidth
                points[3] = midY - halfStrokeWidth
                points[4] = midX + halfStrokeWidth - 2.5f
                points[5] = midY - halfStrokeWidth - 0.5f
                points[6] = lerp(points[4], anchorX, sFr)
                points[7] = lerp(points[5], actualTop, sFr)

                linePointsLength = 8
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
        val points = linePoints

        if (linePointsLength == 4) {
            c.drawLine(points[0], points[1], points[2], points[3], arrowPaint)
        } else {
            c.drawLines(points, 0, linePointsLength, arrowPaint)
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
        return when (Color.alpha(arrowColor)) {
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