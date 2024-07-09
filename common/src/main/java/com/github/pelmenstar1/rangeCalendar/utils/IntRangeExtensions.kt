package com.github.pelmenstar1.rangecalendar.utils

fun IntRange.useDifference(other: IntRange, block: (start: Int, endInclusive: Int) -> Unit) {
    val start = first
    val endInclusive = last

    val otherStart = other.first
    val otherEndInclusive = other.last

    if (start == otherStart && endInclusive == otherEndInclusive) {
        // Ranges are the same, no difference
        return
    }

    if (start <= otherEndInclusive && otherStart <= endInclusive) {
        block(minOf(start, otherStart), maxOf(start, otherStart) - 1)
        block(minOf(endInclusive, otherEndInclusive) + 1, maxOf(endInclusive, otherEndInclusive) - 1)
    } else {
        block(start, endInclusive)
        block(otherStart, otherEndInclusive)
    }
}