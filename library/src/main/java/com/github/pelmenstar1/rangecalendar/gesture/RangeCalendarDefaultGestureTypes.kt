package com.github.pelmenstar1.rangecalendar.gesture

/**
 * Defines default gesture types.
 */
object RangeCalendarDefaultGestureTypes {
    /**
     * Double tap to select week range.
     */
    @JvmField
    val doubleTapWeek = RangeCalendarGestureType(ordinal = 0, displayName = "doubleTapWeek")

    /**
     * Long press to start selecting custom range.
     */
    @JvmField
    val longPressRange = RangeCalendarGestureType(ordinal = 1, displayName = "longPressRange")

    /**
     * Horizontal pinch to select week range.
     */
    @JvmField
    val horizontalSwipeWeek = RangeCalendarGestureType(ordinal = 2, displayName = "horizontalSwipeWeek")

    /**
     * Diagonal pinch to select month range. The diagonal in this gesture is a one that runs from left bottom corner to right top corner.
     */
    @JvmField
    val diagonalSwipeMonth = RangeCalendarGestureType(ordinal = 3, displayName = "diagonalSwipeMonth")

    internal const val typeCount = 4
    internal val allTypes = arrayOf(doubleTapWeek, longPressRange, horizontalSwipeWeek, diagonalSwipeMonth)

    internal val allEnabledSet = RangeCalendarGestureTypeSet.create(allTypes)
}

/**
 * Builder for [RangeCalendarGestureTypeSet] using default gestures.
 */
class RangeCalendarDefaultGestureTypeSetBuilder {
    private val types = arrayOfNulls<RangeCalendarGestureType>(RangeCalendarDefaultGestureTypes.typeCount)
    private var bits = 0L

    /**
     * Adds [RangeCalendarDefaultGestureTypes.doubleTapWeek] to the set.
     */
    fun doubleTapWeek() = addType { doubleTapWeek }

    /**
     * Adds [RangeCalendarDefaultGestureTypes.longPressRange] to the set.
     */
    fun longPressRange() = addType { longPressRange }

    /**
     * Adds [RangeCalendarDefaultGestureTypes.horizontalSwipeWeek] to the set.
     */
    fun horizontalSwipeWeek() = addType { horizontalSwipeWeek }

    /**
     * Adds [RangeCalendarDefaultGestureTypes.diagonalSwipeMonth] to the set.
     */
    fun diagonalSwipeMonth() = addType { diagonalSwipeMonth }

    private inline fun addType(getType: RangeCalendarDefaultGestureTypes.() -> RangeCalendarGestureType) {
        addType(RangeCalendarDefaultGestureTypes.getType())
    }

    private fun addType(type: RangeCalendarGestureType) {
        val ordinal = type.ordinal

        types[type.ordinal] = type
        bits = bits or (1L shl ordinal)
    }

    /**
     * Creates the set.
     */
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