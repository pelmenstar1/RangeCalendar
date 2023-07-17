package com.github.pelmenstar1.rangecalendar.gesture

import com.github.pelmenstar1.rangecalendar.utils.iterateSetBits

internal typealias GetDefaultGestureType<T> = RangeCalendarDefaultGestureTypes.() -> RangeCalendarGestureType<T>

/**
 * Defines default gesture types.
 */
object RangeCalendarDefaultGestureTypes {
    /**
     * Single tap to select a cell.
     */
    @JvmField
    val singleTapCell = RangeCalendarGestureType<Nothing>(ordinal = 0, displayName = "singleTapCell")

    /**
     * Double tap to select week range.
     */
    @JvmField
    val doubleTapWeek = RangeCalendarGestureType<Nothing>(ordinal = 1, displayName = "doubleTapWeek")

    /**
     * Long press to start selecting custom range.
     */
    @JvmField
    val longPressRange = RangeCalendarGestureType<Nothing>(ordinal = 2, displayName = "longPressRange")

    /**
     * Horizontal pinch to select week range.
     */
    @JvmField
    val horizontalPinchWeek =
        RangeCalendarGestureType<PinchConfiguration>(ordinal = 3, displayName = "horizontalSwipeWeek")

    /**
     * Diagonal pinch to select month range. The diagonal in this gesture is a one that runs from left bottom corner to right top corner.
     */
    @JvmField
    val diagonalPinchMonth =
        RangeCalendarGestureType<PinchConfiguration>(ordinal = 4, displayName = "diagonalSwipeMonth")

    internal const val typeCount = 5
    internal val allTypes: Array<RangeCalendarGestureType<*>> =
        arrayOf(singleTapCell, doubleTapWeek, longPressRange, horizontalPinchWeek, diagonalPinchMonth)

    // bits is a number where lowest 'typeCount' bits are set.
    internal val allEnabledSet = RangeCalendarGestureTypeBitsSet(bits = (1 shl typeCount) - 1, allTypes)
}

/**
 * Builder for [RangeCalendarGestureTypeBitsSet] using default gestures.
 */
class RangeCalendarDefaultGestureTypeSetBuilder {
    private var bits = 0

    /**
     * Adds [RangeCalendarDefaultGestureTypes.singleTapCell] to the set.
     */
    fun singleTapCell() = addType { singleTapCell }

    /**
     * Adds [RangeCalendarDefaultGestureTypes.doubleTapWeek] to the set.
     */
    fun doubleTapWeek() = addType { doubleTapWeek }

    /**
     * Adds [RangeCalendarDefaultGestureTypes.longPressRange] to the set.
     */
    fun longPressRange() = addType { longPressRange }

    /**
     * Adds [RangeCalendarDefaultGestureTypes.horizontalPinchWeek] to the set.
     */
    fun horizontalPinchWeek() = addType { horizontalPinchWeek }

    /**
     * Adds [RangeCalendarDefaultGestureTypes.diagonalPinchMonth] to the set.
     */
    fun diagonalPinchMonth() = addType { diagonalPinchMonth }

    private inline fun addType(getType: RangeCalendarDefaultGestureTypes.() -> RangeCalendarGestureType<*>) {
        addType(RangeCalendarDefaultGestureTypes.getType())
    }

    private fun addType(type: RangeCalendarGestureType<*>) {
        bits = bits or (1 shl type.ordinal)
    }

    /**
     * Creates the set.
     */
    fun build(): Set<RangeCalendarGestureType<*>> {
        val elements = createElements()

        return RangeCalendarGestureTypeBitsSet(bits, elements)
    }

    @Suppress("UNCHECKED_CAST")
    private fun createElements(): Array<RangeCalendarGestureType<*>> {
        val elementCount = bits.countOneBits()
        val allTypes = RangeCalendarDefaultGestureTypes.allTypes

        if (elementCount == RangeCalendarDefaultGestureTypes.typeCount) {
            return allTypes
        }

        val elements = arrayOfNulls<RangeCalendarGestureType<*>>(elementCount)
        var eIndex = 0

        bits.iterateSetBits { ordinal ->
            elements[eIndex++] = allTypes[ordinal]
        }

        return elements as Array<RangeCalendarGestureType<*>>
    }
}

internal inline fun Set<RangeCalendarGestureType<*>>.contains(getType: GetDefaultGestureType<*>): Boolean {
    return contains(RangeCalendarDefaultGestureTypes.getType())
}

internal inline fun<T : Any> RangeCalendarGestureTypeMutableMap.put(getType: GetDefaultGestureType<T>, value: T) {
    put(RangeCalendarDefaultGestureTypes.getType(), value)
}

internal inline fun <T : Any> RangeCalendarGestureTypeMap.get(getType: GetDefaultGestureType<T>): T? {
    return get(RangeCalendarDefaultGestureTypes.getType())
}