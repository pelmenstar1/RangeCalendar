package com.github.pelmenstar1.rangecalendar.gesture

object RangeCalendarDefaultGestureTypes {
    @JvmField
    val doubleTapWeek = RangeCalendarGestureType(ordinal = 0, displayName = "doubleTapWeek")

    @JvmField
    val longPressRange = RangeCalendarGestureType(ordinal = 1, displayName = "longPressRange")

    @JvmField
    val horizontalSwipeWeek = RangeCalendarGestureType(ordinal = 2, displayName = "horizontalSwipeWeek")

    @JvmField
    val diagonalSwipeMonth = RangeCalendarGestureType(ordinal = 3, displayName = "diagonalSwipeMonth")

    internal const val typeCount = 4
    internal val allTypes = arrayOf(doubleTapWeek, longPressRange, horizontalSwipeWeek, diagonalSwipeMonth)

    internal val allEnabledSet = RangeCalendarGestureTypeSet.create(allTypes)
}

class RangeCalendarDefaultGestureTypeSetBuilder {
    private val types = arrayOfNulls<RangeCalendarGestureType>(RangeCalendarDefaultGestureTypes.typeCount)
    private var bits = 0L

    fun doubleTapWeek() = addType { doubleTapWeek }
    fun longPressRange() = addType { longPressRange }
    fun horizontalSwipeWeek() = addType { horizontalSwipeWeek }
    fun diagonalSwipeMonth() = addType { diagonalSwipeMonth }

    private inline fun addType(getType: RangeCalendarDefaultGestureTypes.() -> RangeCalendarGestureType) {
        addType(RangeCalendarDefaultGestureTypes.getType())
    }

    private fun addType(type: RangeCalendarGestureType) {
        val ordinal = type.ordinal

        types[type.ordinal] = type
        bits = bits or (1L shl ordinal)
    }

    fun toSet(): RangeCalendarGestureTypeSet {
        val elements = createElements()

        return RangeCalendarGestureTypeSet.BitsImpl(bits, elements)
    }

    @Suppress("UNCHECKED_CAST")
    private fun createElements(): Array<RangeCalendarGestureType> {
        val bitCount = bits.countOneBits()
        if (bitCount == RangeCalendarDefaultGestureTypes.typeCount) {
            return types as Array<RangeCalendarGestureType>
        }

        val elements = arrayOfNulls<RangeCalendarGestureType>(bitCount)
        var eIndex = 0

        for (type in types) {
            if (type != null) {
                elements[eIndex++] = type
            }
        }

        return elements as Array<RangeCalendarGestureType>
    }
}

internal inline fun RangeCalendarGestureTypeSet.contains(
    getType: RangeCalendarDefaultGestureTypes.() -> RangeCalendarGestureType
): Boolean {
    return contains(RangeCalendarDefaultGestureTypes.getType())
}