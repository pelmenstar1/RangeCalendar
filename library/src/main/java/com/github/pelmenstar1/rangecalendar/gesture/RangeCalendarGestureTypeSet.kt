package com.github.pelmenstar1.rangecalendar.gesture

import androidx.collection.ArraySet

sealed class RangeCalendarGestureTypeSet {
    abstract val size: Int

    abstract fun contains(type: RangeCalendarGestureType): Boolean

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
            return "RangeCalendarGestureTypeSet(elements=${elements.contentToString()})"
        }
    }

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

        @JvmStatic
        fun create(types: Iterable<RangeCalendarGestureType>): RangeCalendarGestureTypeSet {
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