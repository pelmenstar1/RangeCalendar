package com.github.pelmenstar1.rangecalendar.complexRange.cell

import kotlin.test.Test
import kotlin.test.assertEquals

class CellComplexRangeFragmentListTests {
    private fun createFragments(ranges: Array<IntRange>): CellComplexRangeFragmentList {
        return CellComplexRange(ranges).fragments()
    }

    private fun<T> propertyTestHelper(
        ranges: Array<IntRange>,
        property: CellComplexRangeFragmentList.() -> T,
        expected: T
    ) {
        val fragments = createFragments(ranges)
        val actual = fragments.property()

        assertEquals(expected, actual)
    }

    @Test
    fun sizeTest() {
        fun testCase(ranges: Array<IntRange>) {
            propertyTestHelper(ranges, { size }, expected = ranges.size)
        }

        testCase(emptyArray())
        testCase(arrayOf(1..2))
        testCase(arrayOf(1..2, 5..6))
        testCase(arrayOf(1..2, 6..7, 10..12))
    }

    @Test
    fun isEmptyTest() {
        fun testCase(ranges: Array<IntRange>) {
            propertyTestHelper(ranges, { isEmpty() }, expected = ranges.isEmpty())
        }

        testCase(emptyArray())
        testCase(arrayOf(1..2))
        testCase(arrayOf(1..2, 5..6))
    }

    @Test
    fun getTest() {
        fun testCase(ranges: Array<IntRange>, index: Int) {
            propertyTestHelper(ranges, { get(index).toIntRange() }, expected = ranges[index])
        }

        fun testCase(ranges: Array<IntRange>) {
            for (i in ranges.indices) {
                testCase(ranges, i)
            }
        }

        testCase(arrayOf(1..2))
        testCase(arrayOf(1..2, 6..7))
        testCase(arrayOf(1..2, 6..7, 10..41))
    }

    @Test
    fun indexOfTest() {
        fun testCase(ranges: Array<IntRange>, target: IntRange) {
            val expected = ranges.indexOf(target)

            propertyTestHelper(ranges, { indexOf(CellFragment(target)) }, expected)
        }

        fun testCase(ranges: Array<IntRange>) {
            ranges.forEach { testCase(ranges, it) }
        }

        testCase(ranges = emptyArray(), target = 1..2)
        testCase(ranges = arrayOf(2..3))
        testCase(ranges = arrayOf(2..3, 7..8))
        testCase(ranges = arrayOf(2..3, 7..10, 12..17))
        testCase(ranges = arrayOf(1..7), target = 2..7)
        testCase(ranges = arrayOf(1..7), target = 1..6)
    }

    @Test
    fun containsTest() {
        fun testCase(ranges: Array<IntRange>, target: IntRange) {
            val expected = ranges.contains(target)

            propertyTestHelper(ranges, { contains(CellFragment(target)) }, expected)
        }

        fun testCase(ranges: Array<IntRange>) {
            ranges.forEach { testCase(ranges, it) }
        }

        testCase(ranges = emptyArray(), target = 1..2)
        testCase(ranges = arrayOf(2..3))
        testCase(ranges = arrayOf(2..3, 7..8))
        testCase(ranges = arrayOf(2..3, 7..10, 12..17))
        testCase(ranges = arrayOf(1..7), target = 2..7)
        testCase(ranges = arrayOf(1..7), target = 1..6)
    }

    private fun listIteratorTestHelper(iter: Iterator<CellFragment>, expected: Array<IntRange>) {
        fun forwardPass() {

        }
    }

    @Test
    fun listIteratorTest() {

    }

    private fun CellFragment.toIntRange(): IntRange {
        return start..endInclusive
    }
}