package com.github.pelmenstar1.rangecalendar.utils

import kotlin.math.ceil

/**
 * sqrt(2)
 */
internal const val SQRT_2 = 1.41421356237f

@Suppress("NOTHING_TO_INLINE")
internal inline fun ceilToInt(value: Float): Int {
    return ceil(value.toDouble()).toInt()
}

internal fun floorMod(x: Long, y: Long): Long {
    val r = x / y
    var aligned = r * y
    if (x xor y < 0 && aligned != x) {
        aligned -= y
    }
    return x - aligned
}

internal fun distanceSquare(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val xDist = x2 - x1
    val yDist = y2 - y1

    return xDist * xDist + yDist * yDist
}

internal fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}

internal fun lerp(start: Int, end: Int, fraction: Float): Int {
    return start + ((end - start).toFloat() * fraction).toInt()
}

internal fun lerpFloatArray(
    start: FloatArray, end: FloatArray, outArray: FloatArray,
    startIndex: Int, endIndexExclusive: Int,
    fraction: Float,
    startOffset: Int = 0, endOffset: Int = 0
) {
    for(i in startIndex until endIndexExclusive) {
        outArray[i] = lerp(start[i - startOffset], end[i - endOffset], fraction)
    }
}