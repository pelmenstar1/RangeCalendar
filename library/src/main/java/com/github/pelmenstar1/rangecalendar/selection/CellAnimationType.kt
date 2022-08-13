package com.github.pelmenstar1.rangecalendar.selection

/**
 * Specifies type of animation for cells.
 */
enum class CellAnimationType {
    /**
     * The cell gradually appears using alpha color animation.
     */
    ALPHA,

    /**
     * The cell rises from the center. Looks better when the cell is a circle.
     */
    BUBBLE;

    companion object {
        fun ofOrdinal(index: Int) = when(index) {
            0 -> ALPHA
            1 -> BUBBLE
            else -> throw IndexOutOfBoundsException("index=$index; length=2")
        }
    }
}