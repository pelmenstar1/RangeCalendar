package com.github.pelmenstar1.rangecalendar.complexRange.date

import com.github.pelmenstar1.rangecalendar.PackedDate
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DateComplexRangeTests {
    private fun intToPackedDate(value: Int) =
        PackedDate.fromEpochDay(value.toLong())

    private fun createComplexRange(ranges: Array<IntRange>): DateComplexRange {
        return DateComplexRange {
            ranges.forEach {
                fragment(DateFragment(it.first.toLong(), it.last.toLong()))
            }
        }
    }

    @Test
    fun toStringTest() {
        fun testCase(fragmentRanges: Array<IntRange>, expectedResult: String) {
            val range = createComplexRange(fragmentRanges)

            val actualResult = range.toString()
            assertEquals(expectedResult, actualResult)
        }

        testCase(emptyArray(), "DateComplexRange()")
        testCase(arrayOf(1..2), "DateComplexRange([1970-01-02, 1970-01-03])")
        testCase(arrayOf(1..2, 4..5), "DateComplexRange([1970-01-02, 1970-01-03], [1970-01-05, 1970-01-06])")
    }

    @Test
    fun modifySetTest() {
        val range = DateComplexRange {
            fragment(DateFragment(1L, 2L))
        }

        val newRange = range.modify {
            set(DateFragment(4L, 5L))
        }

        val expectedFragments = arrayOf(
            DateFragment(1L, 2L),
            DateFragment(4L, 5L)
        )

        val actualFragments = newRange.fragments().toTypedArray()

        assertContentEquals(expectedFragments, actualFragments)
    }

    @Test
    fun modifyUnsetTest() {
        fun testCase(initialRanges: Array<IntRange>, unsetRange: IntRange, expectedRanges: Array<IntRange>) {
            val initial = createComplexRange(initialRanges)
            val rangeAfterUnset = initial.modify {
                unset(DateFragment(unsetRange.first.toLong(), unsetRange.last.toLong()))
            }

            val expectedFragments = expectedRanges.map {
                DateFragment(it.first.toLong(), it.last.toLong())
            }.toTypedArray()
            val actualFragments = rangeAfterUnset.fragments().toTypedArray()

            assertContentEquals(expectedFragments, actualFragments)
        }

        testCase(
            initialRanges = arrayOf(0..1),
            unsetRange = 1..1,
            expectedRanges = arrayOf(0..0)
        )

        testCase(
            initialRanges = arrayOf(0..1),
            unsetRange = 0..0,
            expectedRanges = arrayOf(1..1)
        )

        testCase(
            initialRanges = arrayOf(0..1),
            unsetRange = 0..1,
            expectedRanges = emptyArray()
        )

        testCase(
            initialRanges = arrayOf(1..2),
            unsetRange = 0..3,
            expectedRanges = emptyArray()
        )

        testCase(
            initialRanges = arrayOf(0..1, 3..4),
            unsetRange = 0..4,
            expectedRanges = emptyArray()
        )

        testCase(
            initialRanges = arrayOf(1..2, 4..5),
            unsetRange = 0..6,
            expectedRanges = emptyArray()
        )

        testCase(
            initialRanges = arrayOf(0..1, 3..4),
            unsetRange = 0..3,
            expectedRanges = arrayOf(4..4)
        )

        testCase(
            initialRanges = arrayOf(0..1, 3..4, 6..7),
            unsetRange = 0..3,
            expectedRanges = arrayOf(4..4, 6..7)
        )

        testCase(
            initialRanges = arrayOf(0..1, 3..4),
            unsetRange = 1..4,
            expectedRanges = arrayOf(0..0)
        )

        testCase(
            initialRanges = arrayOf((-3)..(-2), 0..1, 3..4),
            unsetRange = 1..4,
            expectedRanges = arrayOf((-3)..(-2), 0..0)
        )

        testCase(
            initialRanges = arrayOf(0..1, 3..4),
            unsetRange = 1..3,
            expectedRanges = arrayOf(0..0, 4..4)
        )

        testCase(
            initialRanges = arrayOf((-3)..(-2), 0..1, 3..4, 6..7),
            unsetRange = 1..3,
            expectedRanges = arrayOf((-3)..(-2), 0..0, 4..4, 6..7)
        )

        testCase(
            initialRanges = arrayOf(0..3),
            unsetRange = 1..2,
            expectedRanges = arrayOf(0..0, 3..3)
        )

        testCase(
            initialRanges = arrayOf(0..1, 3..4, 6..7),
            unsetRange = 1..6,
            expectedRanges = arrayOf(0..0, 7..7)
        )

        testCase(
            initialRanges = arrayOf(0..1, 3..4, 6..7, 9..10),
            unsetRange = 1..9,
            expectedRanges = arrayOf(0..0, 10..10)
        )
    }

    @Test
    fun clampTest() {
        fun testCase(fragments: Array<IntRange>, clampRange: IntRange, expected: Array<IntRange>) {
            val complexRange = createComplexRange(fragments)
            val actualRange = complexRange.clamp(intToPackedDate(clampRange.first), intToPackedDate(clampRange.last))
            val expectedRange = createComplexRange(expected)

            assertEquals(expectedRange, actualRange)
        }

        testCase(
            fragments = emptyArray(),
            clampRange = 0..5,
            expected = emptyArray()
        )

        testCase(
            fragments = arrayOf(1..5),
            clampRange = 2..4,
            expected = arrayOf(2..4)
        )

        testCase(
            fragments = arrayOf(1..5),
            clampRange = 2..5,
            expected = arrayOf(2..5)
        )

        testCase(
            fragments = arrayOf(1..5, 7..8),
            clampRange = 0..10,
            expected = arrayOf(1..5, 7..8)
        )

        testCase(
            fragments = arrayOf(1..5, 7..8),
            clampRange = 1..8,
            expected = arrayOf(1..5, 7..8)
        )

        testCase(
            fragments = arrayOf(1..5, 7..8, 10..12),
            clampRange = 6..9,
            expected = arrayOf(7..8)
        )

        testCase(
            fragments = arrayOf(1..5, 7..8, 10..12),
            clampRange = 6..10,
            expected = arrayOf(7..8, 10..10)
        )
    }

    // We're testing whether the equals() correctly handles the 'equals to null' case
    @Suppress("SENSELESS_COMPARISON")
    @Test
    fun equalsNullTest() {
        val complexRange = createComplexRange(emptyArray())

        val actual = complexRange == null
        assertFalse(actual)
    }

    @Test
    fun equalsSameClassTest() {
        fun testCase(fragments: Array<IntRange>, otherFragments: Array<IntRange>, expected: Boolean) {
            val complexRange = createComplexRange(fragments)
            val otherComplexRange = createComplexRange(otherFragments)

            val actual = complexRange == otherComplexRange
            assertEquals(expected, actual)
        }

        val fragments0 = emptyArray<IntRange>()
        val fragments1 = arrayOf(1..2)
        val fragments2 = arrayOf(1..2, 5..7)
        val fragments3 = arrayOf(2..3)

        testCase(fragments0, fragments0, expected = true)
        testCase(fragments1, fragments1, expected = true)
        testCase(fragments1, fragments2, expected = false)
        testCase(fragments2, fragments1, expected = false)
        testCase(fragments2, fragments3, expected = false)
        testCase(fragments2, fragments2, expected = true)
    }

    @Test
    fun hashCodeSameDataTest() {
        val fragments = arrayOf(1..3, 6..9)

        val complexRange1 = createComplexRange(fragments)
        val complexRange2 = createComplexRange(fragments)

        val hash1 = complexRange1.hashCode()
        val hash2 = complexRange2.hashCode()

        assertEquals(hash1, hash2)
    }
}