package com.github.pelmenstar1.rangecalendar.gesture

import androidx.collection.ArrayMap
import com.github.pelmenstar1.rangecalendar.Distance
import kotlin.math.PI

/**
 * Configuration of various touch gestures.
 */
data class RangeCalendarGestureConfiguration(
    /**
     * A set of enabled (allowed) gesture types.
     */
    val enabledGestureTypes: Set<RangeCalendarGestureType<*>>,

    /**
     * A map that contains options for specific gesture types.
     */
    val gestureTypeOptions: RangeCalendarGestureTypeMap
) {
    companion object {
        private val DEFAULT = RangeCalendarGestureConfiguration {
            enabledGestureTypes = RangeCalendarDefaultGestureTypes.allEnabledSet

            gestureTypeOptions {
                // 17.5 degrees
                val angleDeviation = 17.5f * (PI.toFloat() / 180)

                val horizontalWeekMinDist = Distance.Relative(fraction = 0.1f, anchor = Distance.RelativeAnchor.WIDTH)
                val diagonalMonthMinDist =
                    Distance.Relative(fraction = 0.15f, anchor = Distance.RelativeAnchor.DIAGONAL)

                put({ horizontalPinchWeek }, PinchConfiguration(angleDeviation, horizontalWeekMinDist))
                put({ diagonalPinchMonth }, PinchConfiguration(angleDeviation, diagonalMonthMinDist))
            }
        }

        /**
         * Returns the default configuration.
         */
        fun default() = DEFAULT
    }
}

class RangeCalendarGestureConfigurationBuilder {
    private var _enabledGestureTypes: Set<RangeCalendarGestureType<*>>? = null

    /**
     * Gets or sets a set of enabled (allowed) gesture types.
     *
     * The getter throws [IllegalStateException] if the setter is not called at least once.
     */
    var enabledGestureTypes: Set<RangeCalendarGestureType<*>>
        get() = _enabledGestureTypes ?: throw IllegalStateException("No value set")
        set(value) {
            _enabledGestureTypes = value
        }

    private var _gestureTypeOptions: RangeCalendarGestureTypeMap? = null

    /**
     * Gets or sets a map that contains options for specific gesture types.
     *
     * The getter throws [IllegalStateException] if the setter is not called at least once.
     */
    var gestureTypeOptions: RangeCalendarGestureTypeMap
        get() = _gestureTypeOptions ?: throw IllegalStateException("No value set")
        set(value) {
            _gestureTypeOptions = value
        }

    inline fun enabledGestureTypes(block: RangeCalendarDefaultGestureTypeSetBuilder.() -> Unit) {
        enabledGestureTypes = RangeCalendarDefaultGestureTypeSetBuilder()
            .also(block)
            .build()
    }

    inline fun gestureTypeOptions(block: RangeCalendarGestureTypeMutableMap.() -> Unit) {
        val map = RangeCalendarGestureTypeMutableMap(ArrayMap()).also(block)

        gestureTypeOptions = map
    }

    /**
     * Creates new instance of [RangeCalendarGestureConfiguration].
     *
     * If [enabledGestureTypes] or/and [gestureTypeOptions] aren't set to any value, they are replaced to an empty collection.
     */
    fun build(): RangeCalendarGestureConfiguration {
        val enabledGestureTypes = _enabledGestureTypes ?: emptySet()
        val gestureTypeOptions = _gestureTypeOptions ?: RangeCalendarGestureTypeMap.empty()

        return RangeCalendarGestureConfiguration(enabledGestureTypes, gestureTypeOptions)
    }
}

inline fun RangeCalendarGestureConfiguration(
    block: RangeCalendarGestureConfigurationBuilder.() -> Unit
): RangeCalendarGestureConfiguration {
    return RangeCalendarGestureConfigurationBuilder()
        .also(block)
        .build()
}