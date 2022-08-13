package com.github.pelmenstar1.rangecalendar

import android.graphics.Shader

/**
 * Specifies type of bounds for gradient used to fill the selection.
 */
enum class SelectionFillGradientBoundsType {
    /**
     * The gradient distribution is limited to selection's shape.
     * When this type is used, performance can decrease due to the fact [Shader] instance is recreated each time
     * the shape is changed. Especially this can be observed when animation is running
     * and the shape is changed more frequently.
     */
    SHAPE,

    /**
     * The gradient distribution is limited to grid of the calendar.
     * When this type is used, [Shader] instance is recreated only when grid size is changed.
     * This can happen when size of the entire calendar is changed.
     */
    GRID;

    companion object {
        /**
         * Returns [SelectionFillGradientBoundsType] value at specified position
         *
         * @param ordinal position of the value
         */
        fun ofOrdinal(ordinal: Int) = when(ordinal) {
            0 -> SHAPE
            1 -> GRID
            else -> throw IndexOutOfBoundsException("ordinal is out of bounds. ordinal=$ordinal; size of enum = 2")
        }
    }
}