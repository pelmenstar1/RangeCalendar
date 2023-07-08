package com.github.pelmenstar1.rangecalendar.gesture

import androidx.collection.ArraySet

/**
 * Represents a set of [RangeCalendarGestureType] elements.
 * Equality of gesture types is based on [RangeCalendarGestureType.ordinal] property.
 * Reference equality is not used. It's expected that all items have different ordinal numbers.
 * For the performance reasons it's better when ordinal number lies between 0 and 63 (inclusive).
 */
sealed class RangeCalendarGestureTypeSet {
    /**
     * Gets size of the set.
     */
    abstract val size: Int

    /**
     * Determines whether the set contains specified [type].
     */
    abstract fun contains(type: RangeCalendarGestureType): Boolean

    /**
     * Optimized implementation of the set that uses bits to store info whether the element is in the set or not.
     * A bit position in [bits] represents ordinal number.
     */
    internal class BitsImpl(
        @JvmField val bits: Long,
        @JvmField val elements: Array<RangeCalendarGestureType>
    ) : RangeCalendarGestureTypeSet() {
        override val size: Int
            get() = bits.countOneBits()

        override fun contains(type: RangeCalendarGestureType): Boolean {
            val ordinal = type.ordinal
            if (ordinal !in 0 until 64) {
                return false
            }

            return (bits and (1L shl ordinal)) != 0L
        }

        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other == null || javaClass != other.javaClass) return false

            other as BitsImpl

            return other.bits == bits
        }

        override fun hashCode(): Int {
            return (bits xor (bits ushr 32)).toInt()
        }

        override fun toString(): String {
            // TODO: Fix when elements are repeated.
            return "RangeCalendarGestureTypeSet(elements=${elements.contentToString()})"
        }
    }

    /**
     * Fallback implementation based on [ArraySet] in case types have ordinal numbers out of 0..63
     */
    internal class ArraySetBasedImpl(
        @JvmField val set: ArraySet<RangeCalendarGestureType>
    ) : RangeCalendarGestureTypeSet() {
        override val size: Int
            get() = set.size

        override fun contains(type: RangeCalendarGestureType): Boolean {
            return set.contains(type)
        }

        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other == null || javaClass != other.javaClass) return false

            other as ArraySetBasedImpl

            return set == other.set
        }

        override fun hashCode() = set.hashCode()

        override fun toString(): String {
            return buildString {
                append("RangeCalendarGestureTypeSet(elements=[")
                
                for ((index, element) in set.withIndex())  {
                    append(element)

                    if (index < set.size - 1) {
                        append(", ")
                    }
                }

                append("])")
            }
        }
    }

    companion object {
        /**
         * Creates the set using specified gesture types.
         *
         * [types] array is expected to have elements with unique [RangeCalendarGestureType.ordinal] numbers.
         * Otherwise, repeated elements are discarded. This behaviour can be observed in the [RangeCalendarGestureTypeSet.toString] method.
         * Order of the elements may be different that can also be observed in the [RangeCalendarGestureTypeSet.toString] method.
         */
        @JvmStatic
        fun create(types: Array<RangeCalendarGestureType>): RangeCalendarGestureTypeSet {
            var index = 0

            return createInline(
                types.size,
                hasNextType = { index < types.size },
                getNextType = { types[index++] },
                getArray = { types },
                toArraySet = { arrayToArraySet(types) },
            )
        }

        /**
         * Creates the set using specified gesture types.
         *
         * [types] iterable is expected to have elements with unique [RangeCalendarGestureType.ordinal] numbers.
         * Otherwise, repeated elements are discarded. This behaviour can be observed in the [RangeCalendarGestureTypeSet.toString] method.
         * The order of the elements may be different that can also be observed in the [RangeCalendarGestureTypeSet.toString] method.
         *
         * The [types] iterable may be iterated more than one time.
         */
        @JvmStatic
        fun create(types: Iterable<RangeCalendarGestureType>): RangeCalendarGestureTypeSet {
            // If types is collection, the size is known.
            val size = if (types is Collection<*>) types.size else -1
            if (size == 0) {
                return BitsImpl(0, emptyArray())
            }

            val iterator = types.iterator()

            return createInline(
                size,
                hasNextType = iterator::hasNext,
                getNextType = iterator::next,
                getArray = { iterableToArray(types) },
                toArraySet = { iterableToArraySet(types) }
            )
        }

        private fun iterableToArray(types: Iterable<RangeCalendarGestureType>): Array<RangeCalendarGestureType> {
            val collection = if (types is Collection<RangeCalendarGestureType>) {
                types
            } else {
                types.toMutableList()
            }

            return collection.toTypedArray()
        }

        private fun iterableToArraySet(types: Iterable<RangeCalendarGestureType>): ArraySet<RangeCalendarGestureType> {
            val set = if (types is Collection<*>) {
                ArraySet<RangeCalendarGestureType>(types.size)
            } else {
                ArraySet<RangeCalendarGestureType>()
            }

            types.forEach(set::add)

            return set
        }

        private fun arrayToArraySet(types: Array<RangeCalendarGestureType>): ArraySet<RangeCalendarGestureType> {
            return ArraySet<RangeCalendarGestureType>(types.size).apply {
                types.forEach { add(it) }
            }
        }

        private inline fun createInline(
            size: Int,
            hasNextType: () -> Boolean,
            getNextType: () -> RangeCalendarGestureType,
            getArray: () -> Array<RangeCalendarGestureType>,
            toArraySet: () -> ArraySet<RangeCalendarGestureType>
        ): RangeCalendarGestureTypeSet {
            tryCreateBitsVariant(size, hasNextType, getNextType, getArray)?.let { return it }

            val set = toArraySet()
            return ArraySetBasedImpl(set)
        }

        private inline fun tryCreateBitsVariant(
            size: Int,
            hasNextType: () -> Boolean,
            getNextType: () -> RangeCalendarGestureType,
            getArray: () -> Array<RangeCalendarGestureType>
        ): BitsImpl? {
            if (size > 63) {
                return null
            }

            var bits = 0L

            while (hasNextType()) {
                val ordinal = getNextType().ordinal

                if (ordinal !in 0 until 64) {
                    return null
                }

                bits = bits or (1L shl ordinal)
            }

            return BitsImpl(bits, getArray())
        }
    }
}