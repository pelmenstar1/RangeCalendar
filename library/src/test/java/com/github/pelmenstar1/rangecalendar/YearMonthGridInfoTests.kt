package com.github.pelmenstar1.rangecalendar

import com.github.pelmenstar1.rangecalendar.selection.Cell
import com.github.pelmenstar1.rangecalendar.selection.CellRange
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class YearMonthGridInfoTests {
    private fun createGridInfo(year: Int, month: Int): YearMonthGridInfo {
        return YearMonthGridInfo().apply { set(year, month) }
    }

    @Test
    fun setTest() {
        fun testCase(
            year: Int, month: Int,
            expectedFirstCellInMonthIndex: Int,
            expectedFirstCellInGridDate: PackedDate,
            expectedLastCellInGridDate: PackedDate,
            expectedDaysInMonth: Int
        ) {
            val gridInfo = createGridInfo(year, month)

            assertEquals(
                expectedFirstCellInMonthIndex,
                gridInfo.firstCellInMonthIndex,
                "firstCellInMonthIndex"
            )
            assertEquals(
                expectedFirstCellInGridDate,
                gridInfo.firstCellInGridDate,
                "firstCellInGridDate"
            )
            assertEquals(
                expectedLastCellInGridDate,
                gridInfo.lastCellInGridDate,
                "lastCellInGridDate"
            )
            assertEquals(expectedDaysInMonth, gridInfo.daysInMonth, "daysInMonth")
        }

        testCase(
            year = 2023, month = 6,
            expectedFirstCellInMonthIndex = 3,
            expectedFirstCellInGridDate = PackedDate(year = 2023, month = 5, dayOfMonth = 29),
            expectedLastCellInGridDate = PackedDate(year = 2023, month = 7, dayOfMonth = 9),
            expectedDaysInMonth = 30
        )

        testCase(
            year = 2023, month = 7,
            expectedFirstCellInMonthIndex = 5,
            expectedFirstCellInGridDate = PackedDate(year = 2023, month = 6, dayOfMonth = 26),
            expectedLastCellInGridDate = PackedDate(year = 2023, month = 8, dayOfMonth = 6),
            expectedDaysInMonth = 31
        )

        testCase(
            year = 2023, month = 1,
            expectedFirstCellInMonthIndex = 6,
            expectedFirstCellInGridDate = PackedDate(year = 2022, month = 12, dayOfMonth = 26),
            expectedLastCellInGridDate = PackedDate(year = 2023, month = 2, dayOfMonth = 5),
            expectedDaysInMonth = 31
        )

        testCase(
            year = 2023, month = 12,
            expectedFirstCellInMonthIndex = 4,
            expectedFirstCellInGridDate = PackedDate(year = 2023, month = 11, dayOfMonth = 27),
            expectedLastCellInGridDate = PackedDate(year = 2024, month = 1, dayOfMonth = 7),
            expectedDaysInMonth = 31
        )

        testCase(
            year = 2023, month = 5,
            expectedFirstCellInMonthIndex = 0,
            expectedFirstCellInGridDate = PackedDate(year = 2023, month = 5, dayOfMonth = 1),
            expectedLastCellInGridDate = PackedDate(year = 2023, month = 6, dayOfMonth = 11),
            expectedDaysInMonth = 31
        )
    }

    @Test
    fun inMonthRangeTest() {
        fun testCase(year: Int, month: Int, expectedRange: CellRange) {
            val info = createGridInfo(year, month)
            val actualRange = info.inMonthRange

            assertEquals(expectedRange, actualRange, "year: $year month: $month")
        }

        testCase(
            year = 2023, month = 6,
            expectedRange = CellRange(start = 3, end = 32)
        )
    }

    @Test
    fun getCellByDateTest() {
        fun testCase(
            year: Int, month: Int,
            date: PackedDate,
            expectedCell: Int
        ) {
            val info = createGridInfo(year, month)
            val actualCell = info.getCellByDate(date).index

            assertEquals(expectedCell, actualCell, "year: $year month: $month date: $date")
        }

        testCase(
            year = 2023, month = 6,
            date = PackedDate(year = 2023, month = 5, dayOfMonth = 29),
            expectedCell = 0
        )

        testCase(
            year = 2023, month = 6,
            date = PackedDate(year = 2023, month = 6, dayOfMonth = 4),
            expectedCell = 6
        )

        testCase(
            year = 2023, month = 6,
            date = PackedDate(year = 2023, month = 7, dayOfMonth = 9),
            expectedCell = 41
        )
    }

    @Test
    fun getDateAtCellTest() {
        fun testCase(
            year: Int, month: Int,
            cell: Int,
            expectedDate: PackedDate
        ) {
            val info = createGridInfo(year, month)
            val actualDate = info.getDateAtCell(Cell(cell))

            assertEquals(expectedDate, actualDate, "year: $year month: $month cell: $cell")
        }

        testCase(
            year = 2023, month = 6,
            cell = 0,
            expectedDate = PackedDate(year = 2023, month = 5, dayOfMonth = 29)
        )

        testCase(
            year = 2023, month = 6,
            cell = 3,
            expectedDate = PackedDate(year = 2023, month = 6, dayOfMonth = 1)
        )

        testCase(
            year = 2023, month = 6,
            cell = 7,
            expectedDate = PackedDate(year = 2023, month = 6, dayOfMonth = 5)
        )

        testCase(
            year = 2023, month = 6,
            cell = 40,
            expectedDate = PackedDate(year = 2023, month = 7, dayOfMonth = 8)
        )

        testCase(
            year = 2023, month = 6,
            cell = 41,
            expectedDate = PackedDate(year = 2023, month = 7, dayOfMonth = 9)
        )
    }

    @Test
    fun fillGridTest() {
        fun testCase(year: Int, month: Int, expectedGrid: ByteArray) {
            val info = createGridInfo(year, month)
            val actualGrid = ByteArray(42)
            info.fillGrid(actualGrid)

            assertContentEquals(expectedGrid, actualGrid, "year: $year month: $month")
        }

        //@formatter:off
        testCase(
            year = 2023, month = 6,
            expectedGrid = byteArrayOf(
                29, 30, 31,  1,  2,  3,  4,
                 5,  6,  7,  8,  9, 10, 11,
                12, 13, 14, 15, 16, 17, 18,
                19, 20, 21, 22, 23, 24, 25,
                26, 27, 28, 29, 30,  1,  2,
                 3,  4,  5,  6,  7,  8,  9
            )
        )

        testCase(
            year = 2023, month = 12,
            expectedGrid = byteArrayOf(
                27, 28, 29, 30,  1,  2,  3,
                 4,  5,  6,  7,  8,  9, 10,
                11, 12, 13, 14, 15, 16, 17,
                18, 19, 20, 21, 22, 23, 24,
                25, 26, 27, 28, 29, 30, 31,
                 1,  2,  3,  4,  5,  6,  7
            )
        )
        //@formatter:on
    }
}