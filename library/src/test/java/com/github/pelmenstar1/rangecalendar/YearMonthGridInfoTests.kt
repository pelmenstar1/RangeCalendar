package com.github.pelmenstar1.rangecalendar

import com.github.pelmenstar1.rangecalendar.selection.Cell
import com.github.pelmenstar1.rangecalendar.selection.CellRange
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class YearMonthGridInfoTests {
    private fun createGridInfo(year: Int, month: Int, firstDow: CompatDayOfWeek): YearMonthGridInfo {
        return YearMonthGridInfo().apply { set(year, month, firstDow) }
    }

    @Test
    fun setTest() {
        fun testCase(
            year: Int, month: Int,
            firstDayOfWeek: CompatDayOfWeek,
            expectedFirstCellInMonthIndex: Int,
            expectedFirstCellInGridDate: PackedDate,
            expectedLastCellInGridDate: PackedDate,
            expectedDaysInMonth: Int
        ) {
            val gridInfo = createGridInfo(year, month, firstDayOfWeek)

            assertEquals(
                expectedFirstCellInMonthIndex,
                gridInfo.firstDayOfMonthCellIndex,
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

        // First day of week - Monday

        testCase(
            year = 2023, month = 6,
            firstDayOfWeek = CompatDayOfWeek.Monday,
            expectedFirstCellInMonthIndex = 3,
            expectedFirstCellInGridDate = PackedDate(year = 2023, month = 5, dayOfMonth = 29),
            expectedLastCellInGridDate = PackedDate(year = 2023, month = 7, dayOfMonth = 9),
            expectedDaysInMonth = 30
        )

        testCase(
            year = 2023, month = 7,
            firstDayOfWeek = CompatDayOfWeek.Monday,
            expectedFirstCellInMonthIndex = 5,
            expectedFirstCellInGridDate = PackedDate(year = 2023, month = 6, dayOfMonth = 26),
            expectedLastCellInGridDate = PackedDate(year = 2023, month = 8, dayOfMonth = 6),
            expectedDaysInMonth = 31
        )

        testCase(
            year = 2023, month = 1,
            firstDayOfWeek = CompatDayOfWeek.Monday,
            expectedFirstCellInMonthIndex = 6,
            expectedFirstCellInGridDate = PackedDate(year = 2022, month = 12, dayOfMonth = 26),
            expectedLastCellInGridDate = PackedDate(year = 2023, month = 2, dayOfMonth = 5),
            expectedDaysInMonth = 31
        )

        testCase(
            year = 2023, month = 12,
            firstDayOfWeek = CompatDayOfWeek.Monday,
            expectedFirstCellInMonthIndex = 4,
            expectedFirstCellInGridDate = PackedDate(year = 2023, month = 11, dayOfMonth = 27),
            expectedLastCellInGridDate = PackedDate(year = 2024, month = 1, dayOfMonth = 7),
            expectedDaysInMonth = 31
        )

        testCase(
            year = 2023, month = 5,
            firstDayOfWeek = CompatDayOfWeek.Monday,
            expectedFirstCellInMonthIndex = 0,
            expectedFirstCellInGridDate = PackedDate(year = 2023, month = 5, dayOfMonth = 1),
            expectedLastCellInGridDate = PackedDate(year = 2023, month = 6, dayOfMonth = 11),
            expectedDaysInMonth = 31
        )

        // First day of week - Sunday

        testCase(
            year = 2023, month = 6,
            firstDayOfWeek = CompatDayOfWeek.Sunday,
            expectedFirstCellInMonthIndex = 4,
            expectedFirstCellInGridDate = PackedDate(year = 2023, month = 5, dayOfMonth = 28),
            expectedLastCellInGridDate = PackedDate(year = 2023, month = 7, dayOfMonth = 8),
            expectedDaysInMonth = 30
        )

        testCase(
            year = 2023, month = 7,
            firstDayOfWeek = CompatDayOfWeek.Sunday,
            expectedFirstCellInMonthIndex = 6,
            expectedFirstCellInGridDate = PackedDate(year = 2023, month = 6, dayOfMonth = 25),
            expectedLastCellInGridDate = PackedDate(year = 2023, month = 8, dayOfMonth = 5),
            expectedDaysInMonth = 31
        )

        testCase(
            year = 2023, month = 1,
            firstDayOfWeek = CompatDayOfWeek.Sunday,
            expectedFirstCellInMonthIndex = 0,
            expectedFirstCellInGridDate = PackedDate(year = 2023, month = 1, dayOfMonth = 1),
            expectedLastCellInGridDate = PackedDate(year = 2023, month = 2, dayOfMonth = 11),
            expectedDaysInMonth = 31
        )

        testCase(
            year = 2023, month = 12,
            firstDayOfWeek = CompatDayOfWeek.Sunday,
            expectedFirstCellInMonthIndex = 5,
            expectedFirstCellInGridDate = PackedDate(year = 2023, month = 11, dayOfMonth = 26),
            expectedLastCellInGridDate = PackedDate(year = 2024, month = 1, dayOfMonth = 6),
            expectedDaysInMonth = 31
        )

        testCase(
            year = 2023, month = 5,
            firstDayOfWeek = CompatDayOfWeek.Sunday,
            expectedFirstCellInMonthIndex = 1,
            expectedFirstCellInGridDate = PackedDate(year = 2023, month = 4, dayOfMonth = 30),
            expectedLastCellInGridDate = PackedDate(year = 2023, month = 6, dayOfMonth = 10),
            expectedDaysInMonth = 31
        )
    }

    @Test
    fun inMonthRangeTest() {
        fun testCase(year: Int, month: Int, firstDayOfWeek: CompatDayOfWeek, expectedRange: CellRange) {
            val info = createGridInfo(year, month, firstDayOfWeek)
            val actualRange = info.inMonthRange

            assertEquals(expectedRange, actualRange, "year: $year month: $month")
        }

        testCase(
            year = 2023, month = 6,
            firstDayOfWeek = CompatDayOfWeek.Monday,
            expectedRange = CellRange(start = 3, end = 32)
        )

        testCase(
            year = 2023, month = 7,
            firstDayOfWeek = CompatDayOfWeek.Sunday,
            expectedRange = CellRange(start = 6, end = 36)
        )
    }

    @Test
    fun getCellByDateTest() {
        fun testCase(
            year: Int, month: Int,
            firstDayOfWeek: CompatDayOfWeek,
            date: PackedDate,
            expectedCell: Int
        ) {
            val info = createGridInfo(year, month, firstDayOfWeek)
            val actualCell = info.getCellByDate(date).index

            assertEquals(expectedCell, actualCell, "year: $year month: $month date: $date")
        }

        // First day of week - Monday

        testCase(
            year = 2023, month = 6,
            firstDayOfWeek = CompatDayOfWeek.Monday,
            date = PackedDate(year = 2023, month = 5, dayOfMonth = 29),
            expectedCell = 0
        )

        testCase(
            year = 2023, month = 6,
            firstDayOfWeek = CompatDayOfWeek.Monday,
            date = PackedDate(year = 2023, month = 6, dayOfMonth = 4),
            expectedCell = 6
        )

        testCase(
            year = 2023, month = 6,
            firstDayOfWeek = CompatDayOfWeek.Monday,
            date = PackedDate(year = 2023, month = 7, dayOfMonth = 9),
            expectedCell = 41
        )

        // First day of week - Sunday

        testCase(
            year = 2023, month = 6,
            firstDayOfWeek = CompatDayOfWeek.Sunday,
            date = PackedDate(year = 2023, month = 5, dayOfMonth = 29),
            expectedCell = 1
        )

        testCase(
            year = 2023, month = 6,
            firstDayOfWeek = CompatDayOfWeek.Sunday,
            date = PackedDate(year = 2023, month = 6, dayOfMonth = 4),
            expectedCell = 7
        )

        testCase(
            year = 2023, month = 6,
            firstDayOfWeek = CompatDayOfWeek.Sunday,
            date = PackedDate(year = 2023, month = 7, dayOfMonth = 8),
            expectedCell = 41
        )
    }

    @Test
    fun getDateAtCellTest() {
        fun testCase(
            year: Int, month: Int,
            firstDayOfWeek: CompatDayOfWeek,
            cell: Int,
            expectedDate: PackedDate
        ) {
            val info = createGridInfo(year, month, firstDayOfWeek)
            val actualDate = info.getDateAtCell(Cell(cell))

            assertEquals(expectedDate, actualDate, "year: $year month: $month cell: $cell")
        }

        // First day of week - Monday

        testCase(
            year = 2023, month = 6,
            firstDayOfWeek = CompatDayOfWeek.Monday,
            cell = 0,
            expectedDate = PackedDate(year = 2023, month = 5, dayOfMonth = 29)
        )

        testCase(
            year = 2023, month = 6,
            firstDayOfWeek = CompatDayOfWeek.Monday,
            cell = 3,
            expectedDate = PackedDate(year = 2023, month = 6, dayOfMonth = 1)
        )

        testCase(
            year = 2023, month = 6,
            firstDayOfWeek = CompatDayOfWeek.Monday,
            cell = 7,
            expectedDate = PackedDate(year = 2023, month = 6, dayOfMonth = 5)
        )

        testCase(
            year = 2023, month = 6,
            firstDayOfWeek = CompatDayOfWeek.Monday,
            cell = 40,
            expectedDate = PackedDate(year = 2023, month = 7, dayOfMonth = 8)
        )

        testCase(
            year = 2023, month = 6,
            firstDayOfWeek = CompatDayOfWeek.Monday,
            cell = 41,
            expectedDate = PackedDate(year = 2023, month = 7, dayOfMonth = 9)
        )

        // First day of week - Sunday

        testCase(
            year = 2023, month = 6,
            firstDayOfWeek = CompatDayOfWeek.Sunday,
            cell = 0,
            expectedDate = PackedDate(year = 2023, month = 5, dayOfMonth = 28)
        )

        testCase(
            year = 2023, month = 6,
            firstDayOfWeek = CompatDayOfWeek.Sunday,
            cell = 4,
            expectedDate = PackedDate(year = 2023, month = 6, dayOfMonth = 1)
        )

        testCase(
            year = 2023, month = 6,
            firstDayOfWeek = CompatDayOfWeek.Sunday,
            cell = 7,
            expectedDate = PackedDate(year = 2023, month = 6, dayOfMonth = 4)
        )

        testCase(
            year = 2023, month = 6,
            firstDayOfWeek = CompatDayOfWeek.Sunday,
            cell = 40,
            expectedDate = PackedDate(year = 2023, month = 7, dayOfMonth = 7)
        )

        testCase(
            year = 2023, month = 6,
            firstDayOfWeek = CompatDayOfWeek.Sunday,
            cell = 41,
            expectedDate = PackedDate(year = 2023, month = 7, dayOfMonth = 8)
        )
    }

    @Test
    fun fillGridTest() {
        fun testCase(year: Int, month: Int, firstDayOfWeek: CompatDayOfWeek, expectedGrid: ByteArray) {
            val info = createGridInfo(year, month, firstDayOfWeek)
            val actualGrid = ByteArray(42)
            info.fillGrid(actualGrid)

            assertContentEquals(expectedGrid, actualGrid, "year: $year month: $month")
        }

        //@formatter:off

        // First day of week - Monday

        testCase(
            year = 2023, month = 6,
            firstDayOfWeek = CompatDayOfWeek.Monday,
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
            firstDayOfWeek = CompatDayOfWeek.Monday,
            expectedGrid = byteArrayOf(
                27, 28, 29, 30,  1,  2,  3,
                 4,  5,  6,  7,  8,  9, 10,
                11, 12, 13, 14, 15, 16, 17,
                18, 19, 20, 21, 22, 23, 24,
                25, 26, 27, 28, 29, 30, 31,
                 1,  2,  3,  4,  5,  6,  7
            )
        )

        // First day of week - Sunday

        testCase(
            year = 2023, month = 6,
            firstDayOfWeek = CompatDayOfWeek.Sunday,
            expectedGrid = byteArrayOf(
                28, 29, 30, 31,  1,  2,  3,
                 4,  5,  6,  7,  8,  9, 10,
                11, 12, 13, 14, 15, 16, 17,
                18, 19, 20, 21, 22, 23, 24,
                25, 26, 27, 28, 29, 30,  1,
                 2,  3,  4,  5,  6,  7,  8
            )
        )

        testCase(
            year = 2023, month = 12,
            firstDayOfWeek = CompatDayOfWeek.Sunday,
            expectedGrid = byteArrayOf(
                26, 27, 28, 29, 30,  1,  2,
                 3,  4,  5,  6,  7,  8,  9,
                10, 11, 12, 13, 14, 15, 16,
                17, 18, 19, 20, 21, 22, 23,
                24, 25, 26, 27, 28, 29, 30,
                31,  1,  2,  3,  4,  5,  6
            )
        )

        //@formatter:on
    }
}