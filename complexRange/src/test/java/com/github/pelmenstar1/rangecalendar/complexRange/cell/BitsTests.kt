package com.github.pelmenstar1.rangecalendar.complexRange.cell

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.expect

class BitsTests {
    @Test
    fun startMaskTest() {
        fun testCase(index: Int, expected: ULong) {
            val actual = startMask(index)

            assertEquals(expected.toLong(), actual)
        }

        testCase(index = 0, expected = 0xFFFFFFFFFFFFFFFFU)
        testCase(index = 1, expected = 0xFFFFFFFFFFFFFFFEU)
        testCase(index = 2, expected = 0xFFFFFFFFFFFFFFFCU)
        testCase(index = 3, expected = 0xFFFFFFFFFFFFFFF8U)
        testCase(index = 63, expected = 0x8000000000000000U)
    }

    @Test
    fun endMaskTest() {
        fun testCase(index: Int, expected: ULong) {
            val actual = endMask(index)

            assertEquals(expected.toLong(), actual)
        }

        testCase(index = 0, expected = 0x1U)
        testCase(index = 1, expected = 0x3U)
        testCase(index = 2, expected = 0x7U)
        testCase(index = 3, expected = 0xFU)
        testCase(index = 63, expected = 0xFFFFFFFFFFFFFFFFU)
    }

    @Test
    fun rangeMaskTest() {
        fun testCase(start: Int, end: Int, expected: ULong) {
            val actual = rangeMask(start, end)

            assertEquals(expected.toLong(), actual)
        }

        testCase(start = 0, end = 0, expected = 0x1U)
        testCase(start = 0, end = 1, expected = 0x3U)
        testCase(start = 0, end = 2, expected = 0x7U)
        testCase(start = 1, end = 2, expected = 0x6U)
        testCase(start = 5, end = 8, expected = 0x1E0U)
    }

    private fun forEachRangeTestHelper(
        bits: ULong,
        expected: List<IntRange>,
        method: (Long, (Int, Int) -> Unit) -> Unit
    ) {
        val actual = ArrayList<IntRange>()
        method(bits.toLong()) { start, end ->
            actual.add(start..end)
        }

        assertContentEquals(expected, actual)
    }

    @Test
    fun forEachRangeTest() {
        fun testCase(bits: ULong, expected: List<IntRange>) {
            forEachRangeTestHelper(bits, expected, ::forEachRange)
        }

        testCase(bits = 0U, expected = emptyList())
        testCase(bits = 1U, expected = listOf(0..0))
        testCase(bits = 3U, expected = listOf(0..1))
        testCase(bits = 6U, expected = listOf(1..2))
        testCase(bits = 0xFFFFFFFFFFFFFFFFU, expected = listOf(0..63))
        testCase(
            bits = 0xE0000000000000C6U,
            expected = listOf(1..2, 6..7, 61..63)
        )
        testCase(
            bits = 0xE00000000F0000C6U,
            expected = listOf(1..2, 6..7, 24..27, 61..63)
        )
    }

    @Test
    fun forEachRangeReversedTest() {
        fun testCase(bits: ULong, expected: List<IntRange>) {
            forEachRangeTestHelper(bits, expected, ::forEachRangeReversed)
        }

        testCase(bits = 0U, expected = emptyList())
        testCase(bits = 1U, expected = listOf(0..0))
        testCase(bits = 3U, expected = listOf(0..1))
        testCase(bits = 6U, expected = listOf(1..2))
        testCase(bits = 0xFFFFFFFFFFFFFFFFU, expected = listOf(0..63))
        testCase(
            bits = 0xE0000000000000C6U,
            expected = listOf(61..63, 6..7, 1..2)
        )
        testCase(
            bits = 0xE00000000F0000C6U,
            expected = listOf(61..63, 24..27, 6..7, 1..2)
        )
    }
}