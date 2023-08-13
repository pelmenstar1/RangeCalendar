package com.github.pelmenstar1.rangecalendar

/**
 * Defines all supported formats of weekday. The format is very dependent on locale.
 * For example, English:
 * - [WeekdayType.SHORT] - weekdays look like Mo, Tu, We...
 * - [WeekdayType.NARROW] - weekdays look like M, T, W...
 */
enum class WeekdayType {
    /**
     * Short-weekday.
     *
     * The format is very dependent on locale.
     * For example, when locale is English, weekdays look like Mo, Tu, We...
     */
    SHORT,

    /**
     * Narrow-weekday.
     *
     * The format is very dependent on locale.
     * For example, when locale is English, weekdays look like M, T, W...
     */
    NARROW;

    companion object {
        /**
         * Returns [WeekdayType] which has specified ordinal.
         *
         * @param index ordinal of [WeekdayType]
         * @throws IllegalArgumentException if index is negative or greater than enum value with largest ordinal
         */
        fun ofOrdinal(index: Int) = when (index) {
            0 -> SHORT
            1 -> NARROW
            else -> throw IllegalArgumentException("index is out of range")
        }
    }
}