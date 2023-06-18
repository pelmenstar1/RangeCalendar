package com.github.pelmenstar1.rangecalendar.utils

import java.util.Arrays

// Helps to fill radii array for drawing round rectangles.
internal object RadiiHelper {
    internal val array = FloatArray(8)

    private const val LT = 0
    private const val RT = 2
    private const val RB = 4
    private const val LB = 6

    private fun setCorner(offset: Int, radius: Float) {
        val arr = array

        arr[offset] = radius
        arr[offset + 1] = radius
    }

    private fun setCorner(offset: Int, condition: Boolean, radius: Float) {
        setCorner(offset, radius = if (condition) radius else 0f)
    }

    fun leftTop(radius: Float) = setCorner(LT, radius)
    fun leftTop(condition: Boolean, radius: Float) = setCorner(LT, condition, radius)

    fun rightTop(radius: Float) = setCorner(RT, radius)
    fun rightTop(condition: Boolean, radius: Float) = setCorner(RT, condition, radius)

    fun rightBottom(radius: Float) = setCorner(RB, radius)
    fun rightBottom(condition: Boolean, radius: Float) = setCorner(RB, condition, radius)

    fun leftBottom(radius: Float) = setCorner(LB, radius)
    fun leftBottom(condition: Boolean, radius: Float) = setCorner(LB, condition, radius)

    // It's important to use this method as it clears all the previous data.
    internal inline fun use (block: RadiiHelper.() -> Unit) {
        Arrays.fill(array, 0f)

        RadiiHelper.block()
    }

    fun radii(): FloatArray = array
}