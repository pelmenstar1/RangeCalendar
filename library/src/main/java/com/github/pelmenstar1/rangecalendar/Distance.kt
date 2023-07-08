package com.github.pelmenstar1.rangecalendar

/**
 * Represents abstract concept of distance that can be either absolute or relative to another dimension.
 */
sealed class Distance {
    /**
     * Represents an absolute distance, i.e. it doesn't depend on any other dimension.
     */
    data class Absolute(val value: Float) : Distance()

    /**
     * Represents a relative dimension that depends on the dimension specified by [anchor].
     *
     * The relativity is expressed as a coefficient [fraction] that is multiplied to the absolute value, [anchor] represents.
     * The [fraction] is always in range `0..1`.
     */
    // TODO: Add check whether fraction is [0; 1]
    data class Relative(val fraction: Float, val anchor: RelativeAnchor) : Distance()

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