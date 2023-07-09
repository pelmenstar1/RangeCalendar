package com.github.pelmenstar1.rangecalendar.gesture

/**
 * A wrapper for [Map] with [RangeCalendarGestureType] as a key and [Any] as a value.
 * The class provides support for accessing the values of heterogeneous map.
 */
open class RangeCalendarGestureTypeMap(
    private val map: Map<RangeCalendarGestureType<*>, Any>
) {
    /**
     * Returns the value corresponding the given [key]. Throws if there is no value associated with specified [key].
     */
    @Suppress("UNCHECKED_CAST")
    operator fun<TOptions : Any> get(key: RangeCalendarGestureType<TOptions>): TOptions {
        return map[key] as TOptions
    }

    /**
     * Returns the wrapped instance of map.
     */
    fun immutableMap(): Map<RangeCalendarGestureType<*>, Any> = map

    companion object {
        private val EMPTY = RangeCalendarGestureTypeMap(emptyMap())

        /**
         * Returns empty instance of [RangeCalendarGestureTypeMap].
         */
        @JvmStatic
        fun empty() = EMPTY
    }
}

/**
 * A wrapper for [MutableMap] with [RangeCalendarGestureType] as a key and [Any] as a value.
 * The class provides support for accessing the values of heterogeneous map.
 */
class RangeCalendarGestureTypeMutableMap(
    private val map: MutableMap<RangeCalendarGestureType<*>, Any>
): RangeCalendarGestureTypeMap(map) {
    /**
     * Associates the specified [value] with the specified [key] in the map.
     */
    fun <TOptions : Any> put(key: RangeCalendarGestureType<TOptions>, value: TOptions) {
        map[key] = value
    }

    /**
     * Returns the wrapped instance of a map as [MutableMap].
     */
    fun mutableMap(): MutableMap<RangeCalendarGestureType<*>, Any> = map
}