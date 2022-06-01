package io.github.pelmenstar1.rangecalendar

/**
 * Represents tuple of [left], [top], [right], [bottom] values
 */
data class Padding(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init {
        require(left >= 0) { "left is negative" }
        require(top >= 0) { "top is negative" }
        require(right >= 0) { "right is negative" }
        require(bottom >= 0) { "bottom is negative" }
    }

    companion object {
        /**
         * Padding with all zero values, a zero padding.
         */
        val ZERO = Padding(0f, 0f, 0f, 0f)
    }
}