package com.github.pelmenstar1.rangecalendar.utils

import kotlin.math.ceil

/**
 * sqrt(2)
 */
const val SQRT_2 = 1.4142135f

@Suppress("NOTHING_TO_INLINE")
inline fun ceilToInt(value: Float): Int {
    return ceil(value.toDouble()).toInt()
}

fun floorMod(x: Long, y: Long): Long {
    val r = x / y
    var aligned = r * y
    if (x xor y < 0 && aligned != x) {
        aligned -= y
    }
    return x - aligned
}

fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}

fun lerp(start: Int, end: Int, fraction: Float): Int {
    return lerp(start.toFloat(), end.toFloat(), fraction).toInt()
}

fun getSquareDistance(x0: Float, y0: Float, x1: Float, y1: Float): Float {
    val dx = x0 - x1
    val dy = y0 - y1

    return dx * dx + dy * dy
}