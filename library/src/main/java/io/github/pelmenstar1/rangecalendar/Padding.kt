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
    companion object {
        /**
         * Padding with all zero values, a zero padding.
         */
        val ZERO = Padding(0f, 0f, 0f, 0f)
    }
}