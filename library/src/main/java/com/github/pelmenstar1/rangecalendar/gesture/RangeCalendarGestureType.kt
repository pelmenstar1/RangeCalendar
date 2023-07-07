package com.github.pelmenstar1.rangecalendar.gesture

class RangeCalendarGestureType(val ordinal: Int, val displayName: String) : Comparable<RangeCalendarGestureType> {
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null || javaClass != other.javaClass) return false

        other as RangeCalendarGestureType

        return ordinal == other.ordinal
    }

    override fun hashCode() = ordinal

    override fun compareTo(other: RangeCalendarGestureType): Int {
        return ordinal - other.ordinal
    }

    override fun toString() = displayName
}