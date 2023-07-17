package com.github.pelmenstar1.rangecalendar.gesture

internal class RangeCalendarGestureTypeBitsSet(
    // If bit 'n' is set, it means that the gesture with ordinal number 'n' is in set.
    private val bits: Int,

    // Expected that the array doesn't have items with the same ordinal
    private val elements: Array<RangeCalendarGestureType<*>>
) : Set<RangeCalendarGestureType<*>> {
    override val size: Int
        get() = elements.size

    override fun isEmpty(): Boolean = bits == 0

    override fun contains(element: RangeCalendarGestureType<*>): Boolean {
        val ordinal = element.ordinal
        if (ordinal !in 0 until 32) {
            return false
        }

        return (bits and (1 shl ordinal)) != 0
    }

    override fun containsAll(elements: Collection<RangeCalendarGestureType<*>>): Boolean {
        return elements.all(::contains)
    }

    override fun iterator(): Iterator<RangeCalendarGestureType<*>> {
        return elements.iterator()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null || javaClass != other.javaClass) return false

        other as RangeCalendarGestureTypeBitsSet

        return other.bits == bits
    }

    override fun hashCode(): Int = bits

    override fun toString(): String {
        return buildString(64) {
            append("RangeCalendarGestureTypeSet(elements=[")

            for ((index, element) in elements.withIndex()) {
                append(element.toString())

                if (index < elements.size - 1) {
                    append(", ")
                }
            }

            append("])")
        }
    }
}