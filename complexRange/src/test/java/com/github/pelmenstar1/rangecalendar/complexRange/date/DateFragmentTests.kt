package com.github.pelmenstar1.rangecalendar.complexRange.date

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DateFragmentTests {
    private fun dateFragment(range: IntRange): DateFragment {
        return DateFragment(range.first.toLong(), range.last.toLong())
    }

    @Test
    fun constructorThrowsOnInvalidArgsTest() {
        assertFailsWith<IllegalArgumentException> { DateFragment(2L, 1L) }
    }

    private fun<T> memberTestHelper(
        r1: IntRange, r2: IntRange,
        expected: T,
        func: (DateFragment, DateFragment) -> T,
    ) {
        val actualResult = func(dateFragment(r1), dateFragment(r2))
        assertEquals(expected, actualResult)
    }

    private fun<T> commutativeMemberTestHelper(
        r1: IntRange, r2: IntRange,
        expected: T,
        func: (DateFragment, DateFragment) -> T,
    ) {
        memberTestHelper(r1, r2, expected, func)
        memberTestHelper(r2, r1, expected, func)
    }

    @Test
    fun canUniteWithTest() {
        fun testHelper(r1: IntRange, r2: IntRange, expected: Boolean) {
           commutativeMemberTestHelper(r1, r2, expected, DateFragment::canUniteWith)
        }

        testHelper(1..2, 2..3, expected = true)
        testHelper(1..2, 1..2, expected = true)
        testHelper(1..2, 4..5, expected = false)
        testHelper(1..2, 3..4, expected = true)
    }

    @Test
    fun overlapsTest() {
        fun testHelper(r1: IntRange, r2: IntRange, expected: Boolean) {
            commutativeMemberTestHelper(r1, r2, expected, DateFragment::overlapsWith)
        }

        testHelper(1..2, 2..3, expected = true)
        testHelper(1..2, 1..2, expected = true)
        testHelper(0..3, 1..2, expected = true)
        testHelper(0..2, 3..4, expected = false)
        testHelper(0..2, 4..5, expected = false)
    }

    @Test
    fun containsExclusiveTest() {
        fun testHelper(base: IntRange, needle: IntRange, expected: Boolean) {
            memberTestHelper(base, needle, expected, DateFragment::containsExclusive)
        }

        testHelper(base = 1..5, needle = 2..2, expected = true)
        testHelper(base = 1..5, needle = 2..4, expected = true)
        testHelper(base = 1..5, needle = 1..5, expected = false)
        testHelper(base = 1..5, needle = 1..4, expected = false)
        testHelper(base = 1..5, needle = 2..5, expected = false)
        testHelper(base = 1..5, needle = 5..6, expected = false)
    }

    @Test
    fun containsCompletelyTest() {
        fun testHelper(base: IntRange, needle: IntRange, expected: Boolean) {
            memberTestHelper(base, needle, expected, DateFragment::containsCompletely)
        }

        testHelper(base = 1..5, needle = 2..2, expected = true)
        testHelper(base = 1..5, needle = 2..4, expected = true)
        testHelper(base = 1..5, needle = 1..5, expected = true)
        testHelper(base = 1..5, needle = 1..4, expected = true)
        testHelper(base = 1..5, needle = 2..5, expected = true)
        testHelper(base = 1..5, needle = 5..6, expected = false)
    }

    @Test
    fun leftContainsTest() {
        fun testHelper(base: IntRange, needle: IntRange, expected: Boolean) {
            memberTestHelper(base, needle, expected, DateFragment::leftContains)
        }

        testHelper(base = 1..5, needle = 1..5, expected = true)
        testHelper(base = 1..5, needle = 0..3, expected = true)
        testHelper(base = 4..5, needle = 1..2, expected = false)
        testHelper(base = 2..5, needle = 4..7, expected = false)
        testHelper(base = 2..5, needle = 7..8, expected = false)
    }

    @Test
    fun toStringTest() {
        val frag = dateFragment(1..2)
        val actual = frag.toString()
        val expected = "[1970-01-02, 1970-01-03]"

        assertEquals(expected, actual)
    }
}