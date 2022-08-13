package com.github.pelmenstar1.rangecalendar

import android.os.Build

/**
 * Defines all supported formats of weekday. The format is very dependent on locale.
 * For example, English:
 * - [WeekdayType.SHORT] - weekdays look like Mon, Tue, Wed...
 * - [WeekdayType.NARROW] - weekdays look like M, T, W...
 */
enum class WeekdayType {
    /**
     * Short-weekday.
     *
     * The format is very dependent on locale.
     * For example, when locale is English, weekdays look like Mon, Tue, Wed...
     */
    SHORT,

    /**
     * Narrow-weekday.
     *
     * The format is very dependent on locale.
     * For example, when locale is English, weekdays look like M, T, W...
     *
     * Although it's supported if API level is 24 and higher, it can be used on lower API levels:
     * it will be replaced with [SHORT]
     */
    NARROW;

    /**
     * If API level is lower than 23, returns [SHORT], otherwise it returns this reference.
     */
    fun resolved(): WeekdayType {
        return if (Build.VERSION.SDK_INT < 24) {
            SHORT
        } else {
            this
        }
    }

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