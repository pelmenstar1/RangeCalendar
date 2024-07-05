package com.github.pelmenstar1.rangecalendar

import com.github.pelmenstar1.rangecalendar.utils.MutableYearMonthComplexRange
import kotlin.test.Test
import kotlin.test.assertContentEquals

class MutableYearMonthComplexRangeTests {
    private fun createComplexRange(ranges: Array<IntRange>): MutableYearMonthComplexRange {
        return MutableYearMonthComplexRange().also { complexRange ->
            ranges.forEach { complexRange.addRange(it.first, it.last) }
        }
    }

    private fun getRanges(
        complexRange: MutableYearMonthComplexRange,
    ): Array<IntRange> {
        val result = ArrayList<IntRange>()
        complexRange.forEachRange { start, endInclusive ->
            result.add(start.totalMonths..endInclusive.totalMonths)
        }

        return result.toTypedArray()
    }

    @Test
    fun addRangeTest() {
        fun testCase(initialRanges: Array<IntRange>, target: IntRange, expected: Array<IntRange>) {
            val complexRange = createComplexRange(initialRanges)

            complexRange.addRange(target.first, target.last)

            val actual = getRanges(complexRange)
            assertContentEquals(expected, actual)
        }

        testCase(
            initialRanges = emptyArray(),
            target = 1..2,
            expected = arrayOf(1..2)
        )

        testCase(
            initialRanges = arrayOf(1..2),
            target = 3..4,
            expected = arrayOf(1..4)
        )

        testCase(
            initialRanges = arrayOf(1..2),
            target = 4..5,
            expected = arrayOf(1..2, 4..5)
        )

        testCase(
            initialRanges = arrayOf(1..2),
            target = 2..3,
            expected = arrayOf(1..3)
        )

        testCase(
            initialRanges = arrayOf(1..2),
            target = 1..2,
            expected = arrayOf(1..2)
        )

        testCase(
            initialRanges = arrayOf(1..2, 4..5),
            target = 5..6,
            expected = arrayOf(1..2, 4..6)
        )

        testCase(
            initialRanges = arrayOf(1..2, 4..5),
            target = 7..10,
            expected = arrayOf(1..2, 4..5, 7..10)
        )
    }

    @Test
    fun forEachRangeExceptRangeTest() {
        fun testCase(
            ranges: Array<IntRange>,
            exceptRange: IntRange,
            expected: Array<IntRange>
        ) {
            val complexRange = createComplexRange(ranges)
            val actualList = ArrayList<IntRange>()

            complexRange.forEachRangeExcept(exceptRange.first, exceptRange.last) { start, end ->
                actualList.add(start..end)
            }

            val actual = actualList.toTypedArray()
            assertContentEquals(expected, actual)
        }

        testCase(
            ranges = arrayOf(1..2),
            exceptRange = 1..2,
            expected = emptyArray()
        )

        testCase(
            ranges = arrayOf(1..2),
            exceptRange = 2..3,
            expected = arrayOf(1..1)
        )

        testCase(
            ranges = arrayOf(1..2),
            exceptRange = 0..1,
            expected = arrayOf(2..2)
        )

        testCase(
            ranges = arrayOf(1..2),
            exceptRange = 0..4,
            expected = emptyArray()
        )

        testCase(
            ranges = arrayOf(1..5),
            exceptRange = 2..3,
            expected = arrayOf(1..1, 4..5)
        )

        testCase(
            ranges = arrayOf(1..4, 6..7, 9..10),
            exceptRange = 4..9,
            expected = arrayOf(1..3, 10..10)
        )

        testCase(
            ranges = arrayOf(1..4, 6..7, 9..10),
            exceptRange = 7..9,
            expected = arrayOf(1..4, 6..6, 10..10)
        )
    }

    /*
    @Test
    fun forEachSubRangeExceptTest() {
        fun testCase(
            range: IntRange,
            exceptRanges: Array<IntRange>,
            expected: Array<IntRange>
        ) {
            val exceptComplexRange = createComplexRange(exceptRanges)
            val actualList = ArrayList<IntRange>()

            MutableYearMonthComplexRange.forEachSubRangeExcept(
                range.first, range.last,
                exceptComplexRange
            ) { start, end ->
                actualList.add(start..end)
            }

            val actual = actualList.toTypedArray()
            assertContentEquals(expected, actual)
        }

        testCase(
            range = 1..2,
            exceptRanges = arrayOf(1..2),
            expected = emptyArray()
        )

        testCase(
            range = 1..2,
            exceptRanges = arrayOf(2..3),
            expected = arrayOf(1..1)
        )

        testCase(
            range = 1..2,
            exceptRanges = arrayOf(0..1),
            expected = arrayOf(2..2)
        )

        testCase(
            range = 1..2,
            exceptRanges = arrayOf(0..4),
            expected = emptyArray()
        )

        testCase(
            range = 1..5,
            exceptRanges = arrayOf(2..3),
            expected = arrayOf(1..1, 4..5)
        )

        testCase(
            range = 1..10,
            exceptRanges = arrayOf(2..3, 7..8),
            expected = arrayOf(1..1, 4..6, 9..10)
        )

        testCase(
            range = 1..10,
            exceptRanges = arrayOf(2..3, 7..11),
            expected = arrayOf(1..1, 4..6)
        )

        testCase(
            range = 1..10,
            exceptRanges = arrayOf(0..3, 7..8),
            expected = arrayOf(4..6, 9..10)
        )
    }

    @Test
    fun forEachRangeExceptComplexRangeTest() {
        fun testCase(
            ranges: Array<IntRange>,
            exceptRanges: Array<IntRange>,
            expected: Array<IntRange>
        ) {
            val complexRange = createComplexRange(ranges)
            val exceptComplexRange = createComplexRange(exceptRanges)
            val actualList = ArrayList<IntRange>()

            complexRange.forEachRangeExceptInt(exceptComplexRange) { start, end ->
                actualList.add(start..end)
            }

            val actual = actualList.toTypedArray()
            assertContentEquals(expected, actual)
        }
    }
    */
}