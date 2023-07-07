package com.github.pelmenstar1.rangecalendar.gesture

interface RangeCalendarGestureDetectorFactory<T : RangeCalendarGestureDetector> {
    val detectorClass: Class<T>
    
    fun create(): T
}