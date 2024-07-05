package com.github.pelmenstar1.rangecalendar.complexRange.cell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CellFragmentTests {
    private fun<T> memberTestHelper(
        r1: IntRange, r2: IntRange,
        expected: T,
        func: (CellFragment, CellFragment) -> T,
    ) {
        val actualResult = func(CellFragment(r1), CellFragment(r2))
        assertEquals(expected, actualResult)
    }

    private fun<T> commutativeMemberTestHelper(
        r1: IntRange, r2: IntRange,
        expected: T,
        func: (CellFragment, CellFragment) -> T,
    ) {
        memberTestHelper(r1, r2, expected, func)
        memberTestHelper(r2, r1, expected, func)
    }

    @Test
    fun constructorThrowsOnInvalidArgumentsTest() {
        fun testCase(start: Int, end: Int) {
            assertFailsWith<IllegalArgumentException> {
                CellFragment(start, end)
            }
        }

        testCase(-1, 2)
        testCase(2, 100)
        testCase(100, 120)
        testCase(10, 5)
    }

    @Test
    fun overlapsTest() {
        fun testCase(r1: IntRange, r2: IntRange, expected: Boolean) {
            commutativeMemberTestHelper(r1, r2, expected, CellFragment::overlapsWith)
        }

        testCase(1..2, 2..3, expected = true)
        testCase(1..2, 1..2, expected = true)
        testCase(0..3, 1..2, expected = true)
        testCase(0..2, 3..4, expected = false)
        testCase(0..2, 4..5, expected = false)
    }

    @Test
    fun containsTest() {
        fun testCase(range: IntRange, value: Int) {
            val fragment = CellFragment(range)
            val actual = fragment.contains(value)
            val expected = range.contains(value)

            assertEquals(expected, actual)
        }

        testCase(range = 1..5, value = 1)
        testCase(range = 1..5, value = 5)
        testCase(range = 1..5, value = -1)
        testCase(range = 1..5, value = 0)
        testCase(range = 5..41, value = 41)
        testCase(range = 5..41, value = 42)
    }

    @Test
    fun elementCountTest() {
        fun testCase(range: IntRange) {
            val fragment = CellFragment(range)
            val actual = fragment.elementCount
            val expected = range.count()

            assertEquals(expected, actual)
        }

        testCase(1..1)
        testCase(1..2)
        testCase(5..8)
        testCase(40..41)
    }

    @Test
    fun getDistanceToTest() {
        fun testCase(r1: IntRange, r2: IntRange, expected: Int) {
            commutativeMemberTestHelper(r1, r2, expected, CellFragment::getDistanceTo)
        }

        testCase(1..2, 1..1, expected = 0)
        testCase(1..2, 3..4, expected = 1)
        testCase(1..2, 4..5, expected = 2)
    }

    @Test
    fun equalsTest() {
        fun testCase(r1: IntRange, r2: IntRange) {
            val expected = r1 == r2

            commutativeMemberTestHelper(r1, r2, expected, CellFragment::equals)
        }

        testCase(1..2, 1..2)
        testCase(1..3, 1..2)
        testCase(2..2, 3..3)
    }

    @Test
    fun hashCodeTest() {
        val range = 1..3
        val fragment1 = CellFragment(range)
        val fragment2 = CellFragment(range)

        val hash1 = fragment1.hashCode()
        val hash2 = fragment2.hashCode()

        assertEquals(hash1, hash2)
    }

    @Test
    fun toStringTest() {
        val fragment = CellFragment(2..5)
        val actual = fragment.toString()
        val expected = "[2, 5]"

        assertEquals(expected, actual)
    }
}