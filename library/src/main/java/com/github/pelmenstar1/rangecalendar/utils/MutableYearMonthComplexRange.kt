package com.github.pelmenstar1.rangecalendar.utils

import com.github.pelmenstar1.rangecalendar.PackedIntRange
import com.github.pelmenstar1.rangecalendar.YearMonth

internal class MutableYearMonthComplexRange {
    private var storage = IntArray(DEFAULT_RANGE_COUNT * 2)
    private var rangeCount = 0

    val isEmpty: Boolean
        get() = rangeCount == 0

    fun clear() {
        rangeCount = 0
    }

    fun copyTo(other: MutableYearMonthComplexRange) {
        other.ensureCapacity(storage.size)
        storage.copyInto(other.storage)
        other.rangeCount = rangeCount
    }

    fun addRange(start: YearMonth, endInclusive: YearMonth) {
        addRange(start.totalMonths, endInclusive.totalMonths)
    }

    fun addRange(start: Int, end: Int) {
        require(start <= end) { "Invalid range: start is greater than end" }

        val rc = rangeCount
        if (rc == 0) {
            addRangeToEnd(start, end)
            return
        }

        val lastStartIndex = rc * 2 - 2
        val lastEndIndex = rc * 2 - 1

        val lastStart = storage[lastStartIndex]
        val lastEnd = storage[lastEndIndex]

        if (start in lastStart..(lastEnd + 1)) {
            // Union last range and range to be added
            storage[lastEndIndex] = maxOf(end, lastEnd)
        } else if (start > lastEnd) {
            addRangeToEnd(start, end)
        } else {
            throw IllegalArgumentException("Given range doesn't met the conditions")
        }
    }

    private fun addRangeToEnd(start: Int, end: Int) {
        val rc = rangeCount
        val lastIndex = rc * 2

        ensureCapacity(requiredCapacity = lastIndex)

        storage.also {
            it[lastIndex] = start
            it[lastIndex + 1] = end
        }

        rangeCount++
    }

    private fun ensureCapacity(requiredCapacity: Int) {
        if (requiredCapacity >= storage.size) {
            IntArray(requiredCapacity).also { newStorage ->
                storage.copyInto(newStorage)
                storage = newStorage
            }
        }
    }

    inline fun forEachRange(block: (start: YearMonth, endInclusive: YearMonth) -> Unit) {
        val storage = storage

        for (i in 0 until rangeCount) {
            val start = storage[i * 2]
            val endInclusive = storage[i * 2 + 1]

            block(YearMonth(start), YearMonth(endInclusive))
        }
    }

    inline fun forEachRangeExcept(
        startYm: YearMonth, endYm: YearMonth,
        block: (start: YearMonth, endInclusive: YearMonth) -> Unit
    ) {
        forEachRangeExcept(startYm.totalMonths, endYm.totalMonths) { start, endInclusive ->
            block(YearMonth(start), YearMonth(endInclusive))
        }
    }

    inline fun forEachRangeExcept(
        exceptStart: Int, exceptEnd: Int,
        block: (start: Int, endInclusive: Int) -> Unit
    ) {
        val storage = storage

        for (i in 0 until rangeCount) {
            val start = storage[i * 2]
            val endInclusive = storage[i * 2 + 1]

            if (start >= exceptStart && endInclusive <= exceptEnd) {
                // If [start; endInclusive] is completely in the [exceptStart; exceptEnd],
                // then skip the range.
                continue
            }

            if (exceptStart > start && exceptEnd < endInclusive) {
                // If [exceptStart; exceptEnd] is completely in the (start; endInclusive),
                // then there will be two ranges: [start; exceptStart - 1] and [exceptEnd + 1, endInclusive]
                block(start, exceptStart - 1)
                block(exceptEnd + 1, endInclusive)

                continue
            }

            var resultStart = start
            var resultEnd = endInclusive

            // If [start; endInclusive] overlaps [exceptStart; exceptEnd]
            if (start <= exceptEnd && exceptStart <= endInclusive) {
                if (start > exceptStart) {
                    resultStart = exceptEnd + 1
                } else {
                    resultEnd = exceptStart - 1
                }
            }

            block(resultStart, resultEnd)
        }
    }

    // TODO: replace it wit for-each-range-except version
    inline fun forEachValueExcept(
        exceptRange: MutableYearMonthComplexRange,
        block: (start: YearMonth, end: YearMonth) -> Unit
    ) {
        forEachValueExceptInt(exceptRange) { start, end ->
            block(YearMonth(start), YearMonth(end))
        }
    }

    inline fun forEachValueExceptInt(
        exceptRange: MutableYearMonthComplexRange,
        block: (start: Int, end: Int) -> Unit
    ) {
        val st = storage

        for (i in 0 until rangeCount) {
            val start = st[i * 2]
            val endInclusive = st[i * 2 + 1]

            for (value in start..endInclusive) {
                if (!exceptRange.contains(value)) {
                    block(value, value)
                }
            }
        }
    }

    fun contains(value: Int): Boolean {
        val st = storage

        for (i in 0 until rangeCount) {
            val start = st[i * 2]
            val endInclusive = st[i * 2 + 1]

            if (value in start..endInclusive) {
                return true
            }
        }

        return false
    }

    private fun findOverlapRange(targetStart: Int, targetEnd: Int): PackedIntRange {
        val st = storage
        
        for (i in 0 until rangeCount) {
            val start = st[i * 2]
            val endInclusive = st[i * 2 + 1]

            if (start <= targetEnd && targetStart <= endInclusive) {
                return PackedIntRange(start, endInclusive)
            }
        }

        return PackedIntRange.Undefined
    }

    companion object {
        private const val DEFAULT_RANGE_COUNT = 4

        private fun rangeOverlaps(start: Int, end: Int, otherStart: Int, otherEnd: Int): Boolean {
            return start <= otherEnd && otherStart <= end
        }
    }
}