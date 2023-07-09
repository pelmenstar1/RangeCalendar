package com.github.pelmenstar1.rangecalendar.gesture

import com.github.pelmenstar1.rangecalendar.Distance

/**
 * A configuration for pitching gesture.
 */
data class PinchConfiguration(
    /**
     * The pitching gesture can be detected when two pointers make a line with specific angle.
     * For example, some pitching gesture is detected when two pointers make a line with angle 0 (horizontal line).
     * In practice, it's nearly impossible to do this. In the best case, you touch the screen and make a line with 1 degree angle.
     * This property specifies the maximum deviation of the angle of the line, so that the gesture is still detected.
     */
    val angleDeviation: Float,

    /**
     * The minimum distance that each pointer should wander to get the gesture detected.
     */
    val minDistance: Distance
)