package io.github.pelmenstar1.rangecalendar

data class Padding(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    companion object {
        val ZERO = Padding(0f, 0f, 0f, 0f)
    }
}