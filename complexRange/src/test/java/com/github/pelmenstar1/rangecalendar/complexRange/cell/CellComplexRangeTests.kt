package com.github.pelmenstar1.rangecalendar.complexRange.cell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CellComplexRangeTests {
    @Test
    fun allTest() {
        val complexRange = CellComplexRange.All
        val fragment = complexRange.fragments()[0]

        assertEquals(0, fragment.start)
        assertEquals(41, fragment.endInclusive)
    }

    @Test
    fun containsTest() {
        fun testCase(ranges: Array<IntRange>, value: Int, expected: Boolean) {
            val complexRange = CellComplexRange(ranges)
            val actual = complexRange.contains(value)

            assertEquals(expected, actual)
        }

        testCase(ranges = arrayOf(1..5), value = 1, expected = true)
        testCase(ranges = arrayOf(1..5), value = 5, expected = true)
        testCase(ranges = arrayOf(1..5), value = 0, expected = false)
        testCase(ranges = arrayOf(1..5), value = -1, expected = false)
        testCase(ranges = arrayOf(1..5), value = 100, expected = false)
        testCase(ranges = arrayOf(1..5, 7..8), value = 8, expected = true)
        testCase(ranges = arrayOf(1..5, 7..8), value = 6, expected = false)
    }

    @Test
    fun isSingleCellNoArgTest() {
        fun testCase(ranges: Array<IntRange>, expected: Boolean) {
            val complexRange = CellComplexRange(ranges)
            val actual = complexRange.isSingleCell()

            assertEquals(expected, actual)
        }

        testCase(ranges = emptyArray(), expected = false)
        testCase(ranges = arrayOf(0..0), expected = true)
        testCase(ranges = arrayOf(1..1), expected = true)
        testCase(ranges = arrayOf(1..2), expected = false)
        testCase(ranges = arrayOf(1..1, 41..41), expected = false)
    }

    @Test
    fun isSingleCellTest() {
        fun testCase(ranges: Array<IntRange>, cell: Int, expected: Boolean) {
            val complexRange = CellComplexRange(ranges)
            val actual = complexRange.isSingleCell(cell)

            assertEquals(expected, actual)
        }

        testCase(ranges = emptyArray(), cell = 0, expected = false)
        testCase(ranges = arrayOf(0..0), cell = 0, expected = true)
        testCase(ranges = arrayOf(0..0), cell = 1, expected = false)
        testCase(ranges = arrayOf(1..1), cell = 1, expected = true)
        testCase(ranges = arrayOf(1..2), cell = 1, expected = false)
        testCase(ranges = arrayOf(1..1, 41..41), cell = 41, expected = false)
        testCase(ranges = arrayOf(1..1), cell = -1, expected = false)
    }

    @Test
    fun hasIntersectionWithTest() {
        fun testCase(ranges: Array<IntRange>, otherRanges: Array<IntRange>, expected: Boolean) {
            val complexRange = CellComplexRange(ranges)
            val otherComplexRange = CellComplexRange(otherRanges)

            val actual = complexRange.hasIntersectionWith(otherComplexRange)

            assertEquals(expected, actual)
        }

        testCase(ranges = emptyArray(), otherRanges = emptyArray(), expected = false)
        testCase(ranges = arrayOf(1..2), otherRanges = arrayOf(1..2), expected = true)
        testCase(ranges = arrayOf(1..2), otherRanges = arrayOf(2..3), expected = true)
        testCase(ranges = arrayOf(1..5), otherRanges = arrayOf(2..3), expected = true)
        testCase(ranges = arrayOf(1..5, 7..8), otherRanges = arrayOf(6..7), expected = true)
        testCase(ranges = arrayOf(1..5), otherRanges = arrayOf(6..7), expected = false)
    }

    @Test
    fun singleCellTest() {
        fun testCase(cell: Int) {
            val complexRange = CellComplexRange.singleCell(cell)
            val actual = complexRange.isSingleCell(cell)

            assertTrue(actual)
        }

        for (i in 0..41) {
            testCase(i)
        }
    }

    @Test
    fun clampTest() {
        fun testCase(ranges: Array<IntRange>, clampRange: IntRange, expected: Array<IntRange>) {
            val complexRange = CellComplexRange(ranges)
            val actualRange = complexRange.clamp(clampRange.first, clampRange.last)
            val expectedRange = CellComplexRange(expected)

            assertEquals(expectedRange, actualRange)
        }

        testCase(ranges = arrayOf(1..5), clampRange = 0..6, expected = arrayOf(1..5))
        testCase(ranges = arrayOf(1..5), clampRange = 2..4, expected = arrayOf(2..4))
        testCase(ranges = arrayOf(1..2, 7..9), clampRange = 2..8, expected = arrayOf(2..2, 7..8))
    }

    @Test
    fun singleCellFailsOnInvalidArgumentTest() {
        fun testCase(cell: Int) {
            assertFailsWith<IllegalArgumentException> {
                CellComplexRange.singleCell(cell)
            }
        }

        testCase(-1)
        testCase(42)
        testCase(100)
    }

    @Test
    fun equalsTest() {
        fun testCase(ranges1: Array<IntRange>, ranges2: Array<IntRange>, expected: Boolean) {
            val complexRange1 = CellComplexRange(ranges1)
            val complexRange2 = CellComplexRange(ranges2)

            val actual = complexRange1 == complexRange2

            assertEquals(expected, actual)
        }

        val ranges1 = arrayOf(1..2)
        val ranges2 = arrayOf(1..2, 5..7)
        val ranges3 = arrayOf(0..5, 10..12, 40..41)

        testCase(ranges1, ranges1, expected = true)
        testCase(ranges2, ranges2, expected = true)
        testCase(ranges3, ranges3, expected = true)
        testCase(ranges1, ranges2, expected = false)
        testCase(ranges2, ranges3, expected = false)
    }

    @Test
    fun hashCodeTest() {
        val ranges = arrayOf(1..2, 5..7)
        val complexRange1 = CellComplexRange(ranges)
        val complexRange2 = CellComplexRange(ranges)

        val hash1 = complexRange1.hashCode()
        val hash2 = complexRange2.hashCode()

        assertEquals(hash1, hash2)
    }

    @Test
    fun toStringTest() {
        fun testCase(ranges: Array<IntRange>, expected: String) {
            val complexRange = CellComplexRange(ranges)
            val actual = complexRange.toString()

            assertEquals(expected, actual)
        }

        testCase(ranges = emptyArray(), expected = "CellComplexRange()")
        testCase(ranges = arrayOf(1..5), expected = "CellComplexRange([1, 5])")
        testCase(ranges = arrayOf(1..5, 8..9), expected = "CellComplexRange([1, 5], [8, 9])")
        testCase(ranges = arrayOf(1..5, 8..9, 11..11), expected = "CellComplexRange([1, 5], [8, 9], [11, 11])")
    }
}