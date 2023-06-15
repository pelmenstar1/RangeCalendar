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
}