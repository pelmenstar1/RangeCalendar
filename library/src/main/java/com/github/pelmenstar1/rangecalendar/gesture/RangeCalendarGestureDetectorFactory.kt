package com.github.pelmenstar1.rangecalendar.gesture

/**
 * Responsible for creating instances of particular implementation of [RangeCalendarGestureDetector].
 */
fun interface RangeCalendarGestureDetectorFactory {
    /**
     * Creates a **new** instance of the implementation of [RangeCalendarGestureDetector]
     */
    fun create(): RangeCalendarGestureDetector
}