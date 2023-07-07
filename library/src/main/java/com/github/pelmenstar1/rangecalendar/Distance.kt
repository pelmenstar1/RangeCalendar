package com.github.pelmenstar1.rangecalendar

sealed class Distance {
    data class Absolute(val value: Float) : Distance()
    data class Relative(val fraction: Float, val anchor: RelativeAnchor) : Distance()

    enum class RelativeAnchor {
        WIDTH,
        HEIGHT,
        DIAGONAL,
        MIN_DIMENSION,
        MAX_DIMENSION
    }

    companion object {
        private val ZERO = Absolute(0f)

        @JvmStatic
        fun zero() = ZERO
    }
}