package com.github.pelmenstar1.rangecalendar.gesture

import com.github.pelmenstar1.rangecalendar.Distance
import kotlin.math.PI

/**
 * Configuration of various touch gestures.
 */
data class RangeCalendarGestureConfiguration @JvmOverloads constructor(
    /**
     * The set of enabled (allowed) gesture types.
     */
    val enabledGestureTypes: RangeCalendarGestureTypeSet,
    val angleDeviation: Float,
    val horizontalWeekMinDistance: Distance,
    val diagonalMonthMinDistance: Distance,
    val additionalOptions: Any? = null
) {
    companion object {
        private val DEFAULT = RangeCalendarGestureConfiguration(
            enabledGestureTypes = RangeCalendarDefaultGestureTypes.allEnabledSet,
            // 15 degrees
            angleDeviation = 15f * (PI.toFloat() / 180),
            horizontalWeekMinDistance = Distance.Relative(fraction = 0.15f, anchor = Distance.RelativeAnchor.WIDTH),
            diagonalMonthMinDistance = Distance.Relative(fraction = 0.15f, anchor = Distance.RelativeAnchor.DIAGONAL)
        )

        /**
         * Returns the default configuration.
         */
        fun default() = DEFAULT
    }
}

class RangeCalendarGestureConfigurationBuilder {
    private var _enabledGestureTypes: RangeCalendarGestureTypeSet? = null
    var enabledGestureTypes: RangeCalendarGestureTypeSet
        get() = _enabledGestureTypes ?: throw IllegalStateException("No value set")
        set(value) {
            _enabledGestureTypes = value
        }

    var angleDeviation = 0f
    var horizontalWeekMinDistance = Distance.zero()
    var diagonalMonthMinDistance = Distance.zero()
    var additionalOptions: Any? = null

    inline fun enabledGestureTypes(block: RangeCalendarDefaultGestureTypeSetBuilder.() -> Unit) {
        val builder = RangeCalendarDefaultGestureTypeSetBuilder()
        builder.block()

        enabledGestureTypes = builder.toSet()
    }

    /**
     * Builds new instance of [RangeCalendarGestureConfiguration].
     */
    fun build(): RangeCalendarGestureConfiguration {
        val enabledGestureTypes = _enabledGestureTypes ?: throw IllegalStateException("enabledGestureTypes should be non-null")

        return RangeCalendarGestureConfiguration(
            enabledGestureTypes, angleDeviation, horizontalWeekMinDistance, diagonalMonthMinDistance, additionalOptions
        )
    }
}

inline fun RangeCalendarGestureConfiguration(
    block: RangeCalendarGestureConfigurationBuilder.() -> Unit
): RangeCalendarGestureConfiguration {
    return RangeCalendarGestureConfigurationBuilder()
        .also(block)
        .build()
}