package com.github.pelmenstar1.rangecalendar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import android.view.Choreographer
import android.view.Choreographer.FrameCallback
import com.github.pelmenstar1.rangecalendar.utils.colorLerp
import com.github.pelmenstar1.rangecalendar.utils.lerp
import com.github.pelmenstar1.rangecalendar.utils.withAlpha

internal class MoveButtonDrawable(
    context: Context,
    private val colorList: ColorStateList,
    private val direction: Int,
    private val animationType: Int
) : Drawable() {
    private class AnimationState {
        var startColor = 0
        var endColor = 0
        var isRunning = false
        var isCancelledBecauseOfQueue = false

        fun set(other: AnimationState) {
            set(other.startColor, other.endColor)
        }

        fun set(startColor: Int, endColor: Int) {
            this.startColor = startColor
            this.endColor = endColor
        }
    }

    private val paint: Paint
    private var color: Int
    private val size: Int
    private val strokeWidth: Float

    private var animationFraction = 0f

    private val linePoints = FloatArray(8)
    private var linePointsLength = 8

    private var stateChangeDuration = 0L
    private var stateChangeStartTime = 0L

    private var currentAnimationState: AnimationState? = null

    private val stateChangeAnimTickCb = FrameCallback { time -> onStateChangeAnimTick(time) }
    private val startStateChangeAnimCb = FrameCallback { time ->
        stateChangeStartTime = time

        setPaintColor(currentAnimationState!!.startColor)
        choreographer.postFrameCallback(stateChangeAnimTickCb)
    }

    init {
        val res = context.resources

        strokeWidth = res.getDimension(R.dimen.rangeCalendar_arrowStrokeWidth)
        size = res.getDimensionPixelSize(R.dimen.rangeCalendar_arrowSize)

        color = colorList.getColorForState(ENABLED_STATE, 0)

        paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        paint.strokeWidth = strokeWidth
    }

    fun setAnimationFraction(fraction: Float) {
        animationFraction = fraction

        computeLinePoints()
        invalidateSelf()
    }

    fun setStateChangeDuration(millis: Long) {
        stateChangeDuration = millis
    }

    private fun onStateChangeAnimTick(time: Long) {
        val animState = currentAnimationState!!

        // currentAnimationState can't be null, because the animation has started
        if (!animState.isRunning && animState.isCancelledBecauseOfQueue) {
            animState.isRunning = true
            animState.isCancelledBecauseOfQueue = false

            stateChangeStartTime = time
            setPaintColor(animState.startColor)
            choreographer.postFrameCallback(stateChangeAnimTickCb)
        } else {
            val fraction = (time - stateChangeStartTime).toFloat() / stateChangeDuration

            if (fraction >= 1f) {
                onStateChangeAnimTickFraction(1f)
                animState.isRunning = false
            } else {
                onStateChangeAnimTickFraction(fraction)
                choreographer.postFrameCallback(stateChangeAnimTickCb)
            }
        }
    }

    private fun startStateChangeAnimation(startColor: Int, endColor: Int) {
        var animState = currentAnimationState

        if (animState != null && animState.isRunning) {
            animState.isRunning = false
            animState.isCancelledBecauseOfQueue = true

            animState.set(startColor, endColor)
        } else {
            if (animState == null) {
                animState = AnimationState()
                currentAnimationState = animState
            }

            animState.set(startColor, endColor)
            choreographer.postFrameCallback(startStateChangeAnimCb)
        }
    }

    private fun onStateChangeAnimTickFraction(fraction: Float) {
        val animState = currentAnimationState!!
        val c = colorLerp(animState.startColor, animState.endColor, fraction)

        setPaintColor(c)
    }

    private fun setPaintColor(color: Int) {
        paint.color = color

        invalidateSelf()
    }

    private fun computeLinePoints() {
        val bounds = bounds

        val boundsLeft = bounds.left
        val boundsTop = bounds.top
        val boundsWidth = bounds.width()
        val boundsHeight = bounds.height()

        val actualLeft = (boundsLeft + (boundsWidth - size) / 2).toFloat()
        val actualTop = (boundsTop + (boundsHeight - size) / 2).toFloat()
        val actualRight = actualLeft + size
        val actualBottom = actualTop + size

        val midX = (boundsLeft + boundsWidth / 2).toFloat()
        val midY = (boundsTop + boundsHeight / 2).toFloat()

        val fraction = animationFraction
        val halfStrokeWidth = strokeWidth * 0.5f

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
        val oldColor = color
        val newColor = colorList.getColorForState(state, colorList.defaultColor)

        return if (oldColor != newColor) {
            color = newColor
            startStateChangeAnimation(oldColor, newColor)

            true
        } else false
    }

    override fun isStateful() = true

    override fun draw(c: Canvas) {
        val points = linePoints

        if (linePointsLength == 4) {
            c.drawLine(points[0], points[1], points[2], points[3], paint)
        } else {
            c.drawLines(points, 0, linePointsLength, paint)
        }
    }

    override fun setAlpha(alpha: Int) {
        setPaintColor(color.withAlpha(alpha))
    }

    override fun getColorFilter(): ColorFilter? = paint.colorFilter

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int {
        return when (Color.alpha(color)) {
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