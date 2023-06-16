package com.github.pelmenstar1.rangecalendar

import com.github.pelmenstar1.rangecalendar.selection.Cell
import com.github.pelmenstar1.rangecalendar.selection.CellRange

internal class YearMonthGridInfo {
    var year = 0
    var month = 0

    var firstCellInMonthIndex = 0
    var firstCellInGridDate = PackedDate(0, 1, 1)
    var lastCellInGridDate = PackedDate(0, 1, 1)

    var daysInMonth = 0
    var daysInPrevMonth = 0

    val inMonthRange: CellRange
        get() {
            val start = firstCellInMonthIndex

            return CellRange(start, start + daysInMonth - 1)
        }

    fun set(year: Int, month: Int) {
        this.year = year
        this.month = month

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
        } else if (nextMonth > 12) {
            nextYear++
            nextMonth = 1
        }

        daysInPrevMonth = getDaysInMonth(prevYear, prevMonth)

        firstCellInGridDate = if (firstDayInMonthDayOfWeek != 1) {
            val firstCellInGridDay = daysInPrevMonth - firstDayInMonthDayOfWeek + 2

            PackedDate(prevYear, prevMonth, firstCellInGridDay)
        } else {
            firstDayInMonthDate
        }

        val lastCellInGridDay = CELLS_IN_GRID - (firstDayInMonthDayOfWeek + daysInMonth) + 1
        lastCellInGridDate = PackedDate(nextYear, nextMonth, lastCellInGridDay)
    }

    fun set(ym: YearMonth) {
        set(ym.year, ym.month)
    }

    fun getCellByDate(date: PackedDate): Cell {
        val diff = date.toEpochDay() - firstCellInGridDate.toEpochDay()

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

        val start = firstCellInMonthIndex
        val monthEnd = start + daysInMonth - 1

        return when {
            index < start -> {
                var prevYear = year
                var prevMonth = month - 1

                if (prevMonth == 0) {
                    prevYear--
                    prevMonth = 1
                }

                val day = daysInPrevMonth - start + index + 1

                PackedDate(prevYear, prevMonth, day)
            }

            index <= monthEnd -> {
                val day = index - start + 1

                PackedDate(year, month, day)
            }

            else -> {
                val day = index - monthEnd

                var nextYear = year
                var nextMonth = month + 1

                if (nextMonth > 12) {
                    nextYear++
                    nextMonth = 1
                }

                PackedDate(nextYear, nextMonth, day)
            }
        }
    }

    fun getDateRangeByCellRange(cellRange: CellRange): PackedDateRange {
        return PackedDateRange(getDateAtCell(cellRange.start), getDateAtCell(cellRange.end))
    }

    fun fillGrid(cells: ByteArray) {
        val start = firstCellInMonthIndex
        val daysInMonth = daysInMonth

        val daysInPrevMonth = daysInPrevMonth
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