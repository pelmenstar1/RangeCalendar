package com.github.pelmenstar1.rangecalendar.complexRange.cell

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CellComplexRangeFragmentListFragmentIteratorTests {
    private fun createIterator(data: List<CellFragment>): CellFragmentIterator {
        return CellComplexRange(data).fragments().fragmentIterator()
    }

    @Test
    fun iterateForwardBackwardTest() {
        fun forwardPass(iter: CellFragmentIterator, expectedElements: List<CellFragment>) {
            var index = 0

            do {
                val current = iter.current
                val expected = expectedElements[index++]

                assertEquals(expected, current)
            } while (iter.moveNext())

            assertEquals(expectedElements.size, index)
        }

        fun backwardPass(iter: CellFragmentIterator, expectedElements: List<CellFragment>) {
            var index = expectedElements.size - 1

            do {
                val current = iter.current
                val expected = expectedElements[index--]

                assertEquals(expected, current)
            } while (iter.movePrevious())

            assertEquals(-1, index)
        }

        fun testCase(elements: List<IntRange>) {
            val expectedFragments = elements.map { CellFragment(it) }

            val iter = createIterator(expectedFragments)
            iter.moveNext()

            forwardPass(iter, expectedFragments)
            backwardPass(iter, expectedFragments)
            forwardPass(iter, expectedFragments)
        }

        testCase(listOf(1..2))
        testCase(listOf(0..2))
        testCase(listOf(0..41))
        testCase(listOf(1..2, 4..5))
        testCase(listOf(0..2, 4..5))
        testCase(listOf(1..2, 4..5, 7..8))
        testCase(listOf(0..2, 4..5, 7..8, 40..41))
    }

    @Test
    fun iterateNextPreviousForwardTest() {
        fun testCase(elements: List<IntRange>) {
            val expectedFragments = elements.map { CellFragment(it) }

            val iter = createIterator(expectedFragments)
            var index = 1

            iter.moveNext()

            while (true) {
                val moveNextRes = iter.moveNext()

                if (!moveNextRes) {
                    break
                }

                val currentElement1 = iter.current
                val expectedElement1 = CellFragment(elements[index])
                assertEquals(expectedElement1, currentElement1)

                val movePrevRes = iter.movePrevious()
                assertTrue(movePrevRes)

                val currentElement2 = iter.current
                val expectedElement2 = CellFragment(elements[index - 1])
                assertEquals(expectedElement2, currentElement2)

                val moveNextRes2 = iter.moveNext()
                assertTrue(moveNextRes2)

                val currentElement3 = iter.current
                assertEquals(expectedElement1, currentElement3)

                index++
            }

            assertEquals(elements.size, index)
        }

        testCase(listOf(1..1, 3..6))
        testCase(listOf(1..1, 3..3, 5..6))
        testCase(listOf(1..1, 3..3, 5..5, 7..9))
    }

    @Test
    fun subRangeTest() {
        fun testCase(ranges: Array<IntRange>, subRangeIndices: IntRange) {
            val fragments = ranges.map { CellFragment(it) }
            val iter = createIterator(fragments)

            repeat(subRangeIndices.first + 1) {
                iter.moveNext()
            }
            iter.mark()

            repeat(subRangeIndices.count() - 1) {
                iter.moveNext()
            }

            val subRange = iter.subRange()

            val expectedSubFragments =
                fragments.subList(subRangeIndices.first, subRangeIndices.last + 1)
            val actualSubFragments = subRange.fragments().toList()

            assertContentEquals(expectedSubFragments, actualSubFragments)
        }

        testCase(ranges = arrayOf(1..2), subRangeIndices = 0..0)
        testCase(ranges = arrayOf(1..2, 4..5), subRangeIndices = 0..0)
        testCase(ranges = arrayOf(1..2, 4..5), subRangeIndices = 0..1)
        testCase(ranges = arrayOf(1..2, 4..5, 7..8), subRangeIndices = 0..0)
        testCase(ranges = arrayOf(1..2, 4..5, 7..8), subRangeIndices = 0..1)
        testCase(ranges = arrayOf(1..2, 4..5, 7..8), subRangeIndices = 1..1)
        testCase(ranges = arrayOf(1..2, 4..5, 7..8), subRangeIndices = 1..2)
        testCase(ranges = arrayOf(1..2, 4..5, 7..8), subRangeIndices = 0..2)
    }
}