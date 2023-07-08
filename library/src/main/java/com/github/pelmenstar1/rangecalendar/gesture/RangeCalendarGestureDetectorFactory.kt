package com.github.pelmenstar1.rangecalendar.gesture

/**
 * Responsible for creating instances of particular implementation of [RangeCalendarGestureDetector].
 */
interface RangeCalendarGestureDetectorFactory<T : RangeCalendarGestureDetector> {
    /**
     * Gets class of the gesture detector that the factory creates.
     */
    val detectorClass: Class<T>

    /**
     * Creates a **new** instance of the implementation of [RangeCalendarGestureDetector]
     */
    fun create(): T
}