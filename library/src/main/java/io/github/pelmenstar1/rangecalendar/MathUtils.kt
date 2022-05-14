package io.github.pelmenstar1.rangecalendar

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