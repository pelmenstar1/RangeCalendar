package io.github.pelmenstar1.rangecalendar.utils

import kotlin.math.abs

/**
 * sqrt(2)
 */
internal const val SQRT_2 = 1.41421356237f

internal fun Float.equalsWithPrecision(other: Float, precision: Float) = abs(this - other) <= precision

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