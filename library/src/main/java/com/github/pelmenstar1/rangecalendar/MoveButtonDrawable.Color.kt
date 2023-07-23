package com.github.pelmenstar1.rangecalendar

import android.view.Choreographer
import androidx.annotation.ColorInt
import com.github.pelmenstar1.rangecalendar.utils.colorLerp
import com.github.pelmenstar1.rangecalendar.utils.getLazyValue

/**
 * Responsible for managing animation logic for the arrow color in MoveButtonDrawable.
 */
internal class MoveButtonDrawableColorAnimator(
    private val colorCallback: ColorCallback
) {
    fun interface ColorCallback {
        fun onTick(color: Int)
    }

    // Stores the duration of ongoing animation
    // We can't use duration property as it can change during the animation and that can cause jumps in colors.
    private var currentAnimationDuration = 0L
    private var startTimeNanos = 0L

    private var startColor = 0
    private var endColor = 0
    private var currentColor = 0

    private var isRunning = false

    // Stores whether there was another animation request during current one.
    private var isInterrupted = false

    private var choreographer: Choreographer? = null

    private val tickCallback = Choreographer.FrameCallback(::onTick)
    private val startCallback = Choreographer.FrameCallback { time ->
        startTimeNanos = time

        onColor(startColor)
        postTick()
    }

    var duration = 0L

    fun start(@ColorInt startColor: Int, @ColorInt endColor: Int) {
        // If there's already ongoing animation, we should ignore startColor parameter and
        // use current color of the animation. Doing that, we can synchronize the end of current animation
        // and the start of new animation between currentColor and endColor. It will make the transition seamless.
        val animStartColor = if (isRunning) {
            isInterrupted = true

            currentColor
        } else {
            startColor
        }

        this.startColor = animStartColor
        this.endColor = endColor
        currentColor = animStartColor

        isRunning = true
        currentAnimationDuration = duration

        postFrameCallback(startCallback)
    }

    private fun onTick(nanos: Long) {
        if (isInterrupted) {
            isInterrupted = false

            // If the animation was interrupted with another request, act like it's a new animation,
            // thus start time should be current time.
            startTimeNanos = nanos
        }

        val elapsedMillis = (nanos - startTimeNanos) / 1_000_00
        val fraction = elapsedMillis.toFloat() / currentAnimationDuration

        if (fraction >= 1f) {
            onEnd()
        } else {
            val c = colorLerp(startColor, endColor, fraction)

            onColor(c)
            postTick()
        }
    }

    private fun onEnd() {
        isRunning = false

        onColor(endColor)
    }

    private fun postTick() {
        postFrameCallback(tickCallback)
    }

    private fun postFrameCallback(callback: Choreographer.FrameCallback) {
        val c = getLazyValue(choreographer, Choreographer::getInstance) { choreographer = it }

        c.postFrameCallback(callback)
    }

    private fun onColor(@ColorInt color: Int) {
        currentColor = color
        colorCallback.onTick(color)
    }
}