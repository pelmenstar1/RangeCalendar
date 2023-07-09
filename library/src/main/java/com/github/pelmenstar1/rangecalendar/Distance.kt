package com.github.pelmenstar1.rangecalendar

/**
 * Represents abstract concept of distance that can be either absolute or relative to another dimension.
 *
 * By the definition, distance can't be negative.
 */
sealed class Distance {
    /**
     * Represents an absolute distance, i.e. it doesn't depend on any other dimension.
     */
    data class Absolute(val value: Float) : Distance() {
        init {
            require(value >= 0) { "value should be non-negative" }
        }
    }

    /**
     * Represents a relative dimension that depends on the dimension specified by [anchor].
     *
     * The relativity is expressed as a coefficient [fraction] that is multiplied to the absolute value, [anchor] represents.
     * The [fraction] is always in range `0..1`.
     */
    data class Relative(val fraction: Float, val anchor: RelativeAnchor) : Distance() {
        init {
            require (fraction in 0f..1f) { "fraction should be in the range 0..1" }
        }
    }

    /**
     * Represents various types of dimensions.
     */
    enum class RelativeAnchor {
        WIDTH,
        HEIGHT,
        DIAGONAL,

        /**
         * Minimum of width and height
         */
        MIN_DIMENSION,

        /**
         * Maximum of width and height.
         */
        MAX_DIMENSION
    }

    companion object {
        private val ZERO = Absolute(0f)

        /**
         * Returns a distance absolute value is zero.
         */
        @JvmStatic
        fun zero() = ZERO
    }
}