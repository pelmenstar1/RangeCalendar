package com.github.pelmenstar1.rangecalendar.complexRange.cell

import kotlin.test.Test
import kotlin.test.assertEquals

class CellComplexRangeTests {
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
    }
}