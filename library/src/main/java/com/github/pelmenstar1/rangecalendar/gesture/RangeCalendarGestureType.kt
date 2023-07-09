package com.github.pelmenstar1.rangecalendar.gesture


/**
 * A descriptor of a gesture type. The descriptor is associated with the type of options (of any class)
 * that supplies additional information needed in the implementation of [RangeCalendarGestureDetector] to work.
 *
 * Note: [equals], [hashCode], [compareTo] are based on [ordinal] property. [displayName] is only used in [toString].
 */
class RangeCalendarGestureType<TOptions : Any>(val ordinal: Int, private val displayName: String) :
    Comparable<RangeCalendarGestureType<TOptions>> {
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null || javaClass != other.javaClass) return false

        other as RangeCalendarGestureType<*>

        return ordinal == other.ordinal
    }

    override fun hashCode() = ordinal

    override fun compareTo(other: RangeCalendarGestureType<TOptions>): Int {
        return ordinal - other.ordinal
    }

    override fun toString() = displayName
}