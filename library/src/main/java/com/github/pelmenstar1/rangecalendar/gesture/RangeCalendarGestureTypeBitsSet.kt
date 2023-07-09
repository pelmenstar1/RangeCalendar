package com.github.pelmenstar1.rangecalendar.gesture

internal class RangeCalendarGestureTypeBitsSet(
    // If bit 'n' is set, it means that the gesture with ordinal number 'n' is in set.
    @JvmField val bits: Long,

    // Expected that elements array doesn't have items with the same ordinal
    @JvmField val elements: Array<RangeCalendarGestureType<*>>
) : Set<RangeCalendarGestureType<*>> {
    override val size: Int
        get() = bits.countOneBits()

    override fun isEmpty(): Boolean {
        return bits == 0L
    }

    override fun contains(element: RangeCalendarGestureType<*>): Boolean {
        val ordinal = element.ordinal
        if (ordinal !in 0 until 64) {
            return false
        }

        return (bits and (1L shl ordinal)) != 0L
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

    override fun hashCode(): Int {
        return (bits xor (bits ushr 32)).toInt()
    }

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