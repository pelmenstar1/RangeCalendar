package com.github.pelmenstar1.rangecalendar.utils

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * sqrt(2)
 */
internal const val SQRT_2 = 1.41421356237f

@Suppress("NOTHING_TO_INLINE")
internal inline fun ceilToInt(value: Float): Int {
    return ceil(value.toDouble()).toInt()
}

/**
 * Determines whether range `[start; endInclusive]` contains given [value].
 * The method correctly handles a case when `start > endInclusive`
 */
internal fun rangeContains(start: Int, endInclusive: Int, value: Int): Boolean {
    val s = min(start, endInclusive)
    val e = max(start, endInclusive)

    return value in s..e
}

internal fun floorMod(x: Long, y: Long): Long {
    val r = x / y
    var aligned = r * y
    if (x xor y < 0 && aligned != x) {
        aligned -= y
    }
    return x - aligned
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
    for (i in startIndex until endIndexExclusive) {
        outArray[i] = lerp(start[i - startOffset], end[i - endOffset], fraction)
    }
}

fun getDistance(x0: Float, y0: Float, x1: Float, y1: Float): Float {
    val dx = x0 - x1
    val dy = y0 - y1

    return sqrt(dx * dx + dy * dy)
}