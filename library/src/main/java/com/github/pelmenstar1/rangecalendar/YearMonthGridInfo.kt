package com.github.pelmenstar1.rangecalendar

import com.github.pelmenstar1.rangecalendar.selection.Cell
import com.github.pelmenstar1.rangecalendar.selection.CellRange

internal class YearMonthGridInfo {
    var ym = YearMonth(0)
    var firstCellInMonthIndex = 0
    var firstCellInGridDate = PackedDate(0, 1, 1)
    var lastCellInGridDate = PackedDate(0, 1, 1)
    var daysInMonth = 0

    val inMonthRange: CellRange
        get() {
            val start = firstCellInMonthIndex

            return CellRange(start, start + daysInMonth - 1)
        }

    fun set(year: Int, month: Int) {
        this.ym = YearMonth(year, month)

        setInternal(year, month)
    }

    fun set(ym: YearMonth) {
        this.ym = ym

        setInternal(ym.year, ym.month)
    }

    private fun setInternal(year: Int, month: Int) {
        daysInMonth = getDaysInMonth(year, month)

        val firstDayInMonthDate = PackedDate(year, month, dayOfMonth = 1)
        val firstDayInMonthDayOfWeek = firstDayInMonthDate.dayOfWeek

        firstCellInMonthIndex = firstDayInMonthDayOfWeek - 1

        var prevYear = year
        var prevMonth = month - 1

        var nextYear = year
        var nextMonth = month + 1

        if (prevMonth == 0) {
            prevYear--
            prevMonth = 12
        } else if (nextMonth == 13) {
            nextYear++
            nextMonth = 1
        }

        val prevDaysInMonth = getDaysInMonth(prevYear, prevMonth)
        val firstCellInGridDay = prevDaysInMonth - firstDayInMonthDayOfWeek + 2
        val lastCellInGridDay = CELLS_IN_GRID - (firstDayInMonthDayOfWeek + daysInMonth) + 1

        firstCellInGridDate = PackedDate(prevYear, prevMonth, firstCellInGridDay)
        lastCellInGridDate = PackedDate(nextYear, nextMonth, lastCellInGridDay)
    }

    fun getCellByDate(date: PackedDate): Cell {
        val diff = date.daysDifference(firstCellInGridDate)

        if (diff in 0 until CELLS_IN_GRID) {
            return Cell(diff.toInt())
        }

        return Cell.Undefined
    }

    fun getCellRangeByDateRange(dateRange: PackedDateRange): CellRange {
        val startCell = getCellByDate(dateRange.start)
        if (startCell.isUndefined) {
            return CellRange.Invalid
        }

        val endCell = getCellByDate(dateRange.end)
        if (endCell.isUndefined) {
            return CellRange.Invalid
        }

        return CellRange(startCell, endCell)
    }

    fun getDateAtCell(cell: Cell): PackedDate {
        val index = cell.index

        val ym = ym

        val start = firstCellInMonthIndex
        val monthEnd = start + daysInMonth - 1

        return when {
            index < start -> {
                val prevYm = ym - 1
                val prevYear = prevYm.year
                val prevMonth = prevYm.month

                val day = getDaysInMonth(prevYear, prevMonth) - start + index + 1

                PackedDate(prevYear, prevMonth, day)
            }

            index <= monthEnd -> {
                val day = index - start + 1

                PackedDate(ym, day)
            }

            else -> {
                val day = index - monthEnd

                PackedDate(ym + 1, day)
            }
        }
    }

    fun getDateRangeByCellRange(cellRange: CellRange): PackedDateRange {
        return PackedDateRange(getDateAtCell(cellRange.start), getDateAtCell(cellRange.end))
    }

    fun fillGrid(cells: ByteArray) {
        val ym = ym

        val start = firstCellInMonthIndex
        val daysInMonth = daysInMonth

        val daysInPrevMonth = getDaysInMonth(ym - 1)
        val thisMonthEnd = start + daysInMonth

        for (i in 0 until start) {
            val day = daysInPrevMonth - i
            val index = start - i - 1

            cells[index] = day.toByte()
        }

        for (i in 0 until daysInMonth) {
            val index = start + i
            val day = i + 1

            cells[index] = day.toByte()
        }

        for (i in 0 until CELLS_IN_GRID - thisMonthEnd) {
            val index = thisMonthEnd + i
            val day = i + 1

            cells[index] = day.toByte()
        }
    }

    companion object {
        private const val CELLS_IN_GRID = 42
    }
}