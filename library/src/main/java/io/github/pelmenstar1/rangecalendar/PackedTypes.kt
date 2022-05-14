package io.github.pelmenstar1.rangecalendar

import kotlin.math.max
import kotlin.math.min

internal object IntPair {
    fun create(first: Int, second: Int): Long {
        return second.toLong() shl 32 or (first.toLong() and 0xffffffffL)
    }

    fun getFirst(pair: Long): Int {
        return pair.toInt()
    }

    fun getSecond(pair: Long): Int {
        return (pair shr 32).toInt()
    }
}

internal object IntRange {
    fun create(start: Int, end: Int): Long {
        return IntPair.create(start, end)
    }

    fun getStart(range: Long): Int {
        return IntPair.getFirst(range)
    }

    fun getEnd(range: Long): Int {
        return IntPair.getSecond(range)
    }

    fun contains(range: Long, value: Int): Boolean {
        val start = getStart(range)
        val end = getEnd(range)

        return value in start..end
    }
}

internal object ShortRange {
    fun create(start: Int, end: Int): Int {
        return end shl 16 or start
    }

    fun getStart(range: Int): Int {
        return range and 0xFFFF
    }

    fun getEnd(range: Int): Int {
        return range shr 16
    }

    fun findIntersection(a: Int, b: Int, invalidRange: Int): Int {
        val aStart = getStart(a)
        val aEnd = getEnd(a)
        val bStart = getStart(b)
        val bEnd = getEnd(b)

        if (bStart > aEnd || aStart > bEnd) {
            return invalidRange
        }

        val start = max(aStart, bStart)
        val end = min(aEnd, bEnd)

        return create(start, end)
    }

    fun contains(range: Int, value: Int): Boolean {
        val start = getStart(range)
        val end = getEnd(range)

        return value in start..end
    }
}

internal object PackedSize {
    fun create(width: Int, height: Int): Long = IntPair.create(width, height)
    fun getWidth(size: Long): Int = IntPair.getFirst(size)
    fun getHeight(size: Long): Int = IntPair.getSecond(size)
}