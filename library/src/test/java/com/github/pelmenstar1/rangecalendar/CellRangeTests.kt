package com.github.pelmenstar1.rangecalendar

import com.github.pelmenstar1.rangecalendar.selection.CellRange
import org.junit.Test
import kotlin.test.assertEquals

class CellRangeTests {
    @Test
    fun invalidTest() {
        val range = CellRange.Invalid

        assertEquals(0, range.start.index, "start")
        assertEquals(-1, range.end.index, "end")
    }

    @Test
    fun allTest() {
        val range = CellRange.All

        assertEquals(0, range.start.index, "start")
        assertEquals(GridConstants.CELL_COUNT - 1, range.end.index, "end")
    }

    @Test
    fun weekTest() {
        fun testCase(weekIndex: Int, expectedStart: Int, expectedEnd: Int) {
            val range = CellRange.week(weekIndex)

            assertEquals(expectedStart, range.start.index, "start. weekIndex: $weekIndex")
            assertEquals(expectedEnd, range.end.index, "start. weekIndex: $weekIndex")
        }

        testCase(weekIndex = 0, expectedStart = 0, expectedEnd = 6)
        testCase(weekIndex = 1, expectedStart = 7, expectedEnd = 13)
        testCase(weekIndex = 2, expectedStart = 14, expectedEnd = 20)
    }

    @Test
    fun hasIntersectionTest() {
        fun testCase(start1: Int, end1: Int, start2: Int, end2: Int, expectedResult: Boolean) {
            val actualResult = CellRange(start1, end1).hasIntersectionWith(CellRange(start2, end2))

            assertEquals(expectedResult, actualResult)
        }

        testCase(
            start1 = 1, end1 = 2,
            start2 = 1, end2 = 1,
            expectedResult = true
        )

        testCase(
            start1 = 1, end1 = 1,
            start2 = 1, end2 = 2,
            expectedResult = true
        )

        testCase(
            start1 = 1, end1 = 5,
            start2 = 2, end2 = 4,
            expectedResult = true
        )

        testCase(
            start1 = 1, end1 = 3,
            start2 = 4, end2 = 5,
            expectedResult = false
        )

        testCase(
            start1 = 1, end1 = 1,
            start2 = 1, end2 = 1,
            expectedResult = true
        )

        testCase(
            start1 = 2, end1 = 5,
            start2 = 2, end2 = 5,
            expectedResult = true
        )

        testCase(
            start1 = 1, end1 = 3,
            start2 = 3, end2 = 5,
            expectedResult = true
        )
    }

    @Test
    fun intersectionWithTest() {
        fun testCase(
            start1: Int, end1: Int,
            start2: Int, end2: Int,
            expectedResult: CellRange
        ) {
            val actualResult = CellRange(start1, end1).intersectionWith(CellRange(start2, end2))

            assertEquals(expectedResult, actualResult)
        }

        testCase(
            start1 = 1, end1 = 2,
            start2 = 1, end2 = 1,
            expectedResult = CellRange(start = 1, end = 1)
        )

        testCase(
            start1 = 1, end1 = 1,
            start2 = 1, end2 = 2,
            expectedResult = CellRange(start = 1, end = 1)
        )

        testCase(
            start1 = 1, end1 = 5,
            start2 = 2, end2 = 4,
            expectedResult = CellRange(start = 2, end = 4)
        )

        testCase(
            start1 = 1, end1 = 3,
            start2 = 4, end2 = 5,
            expectedResult = CellRange.Invalid
        )

        testCase(
            start1 = 1, end1 = 1,
            start2 = 1, end2 = 1,
            expectedResult = CellRange(start = 1, end = 1)
        )

        testCase(
            start1 = 2, end1 = 5,
            start2 = 2, end2 = 5,
            expectedResult = CellRange(start = 2, end = 5)
        )

        testCase(
            start1 = 1, end1 = 3,
            start2 = 3, end2 = 5,
            expectedResult = CellRange(start = 3, end = 3)
        )
    }

    @Test
    fun normalizeTest() {
        fun testCase(start: Int, end: Int, expectedResult: CellRange) {
            val actualResult = CellRange(start, end).normalize()

            assertEquals(expectedResult, actualResult)
        }

        testCase(start = 1, end = 3, expectedResult = CellRange(start = 1, end = 3))
        testCase(start = 2, end = 2, expectedResult = CellRange(start = 2, end = 2))
        testCase(start = 3, end = 1, expectedResult = CellRange(start = 1, end = 3))
    }
}