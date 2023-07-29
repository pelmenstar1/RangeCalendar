package com.github.pelmenstar1.rangecalendar

import com.github.pelmenstar1.rangecalendar.selection.Cell
import com.github.pelmenstar1.rangecalendar.selection.CellRange

internal class YearMonthGridInfo {
    var year = 0
    var month = 0

    var firstDayOfMonthCellIndex = 0
    var firstCellInGridDate = PackedDate.INVALID
    var lastCellInGridDate = PackedDate.INVALID

    var daysInMonth = 0
    var daysInPrevMonth = 0

    val inMonthRange: CellRange
        get() {
            val start = firstDayOfMonthCellIndex

            return CellRange(start, start + daysInMonth - 1)
        }

    fun set(year: Int, month: Int, firstDayOfWeek: CompatDayOfWeek) {
        this.year = year
        this.month = month

        daysInMonth = getDaysInMonth(year, month)

        val firstDayInMonthDate = PackedDate(year, month, dayOfMonth = 1)
        val firstDayInMonthDayOfWeekMondayBased = firstDayInMonthDate.dayOfWeek

        firstDayOfMonthCellIndex = CompatDayOfWeek.daysBetween(firstDayOfWeek, firstDayInMonthDayOfWeekMondayBased)

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

        firstCellInGridDate = if (firstDayOfMonthCellIndex != 0) {
            val firstCellInGridDay = daysInPrevMonth - firstDayOfMonthCellIndex + 1

            PackedDate(prevYear, prevMonth, firstCellInGridDay)
        } else {
            firstDayInMonthDate
        }

        val lastCellInGridDay = GridConstants.CELL_COUNT - (firstDayOfMonthCellIndex + daysInMonth)
        lastCellInGridDate = PackedDate(nextYear, nextMonth, lastCellInGridDay)
    }

    fun set(ym: YearMonth, firstDayOfWeek: CompatDayOfWeek) {
        set(ym.year, ym.month, firstDayOfWeek)
    }

    fun getCellByDate(date: PackedDate): Cell {
        val firstIndex = firstDayOfMonthCellIndex
        val (firstYear, firstMonth, firstDay) = firstCellInGridDate
        val (currentYear, currentMonth, currentDay) = date

        if (firstYear == currentYear && firstMonth == currentMonth && currentDay >= firstDay) {
            return Cell(currentDay - firstDay)
        }

        if (currentYear == year && currentMonth == month) {
            return Cell(firstIndex + currentDay - 1)
        }

        val (lastYear, lastMonth, lastDay) = lastCellInGridDate

        if (lastYear == currentYear && lastMonth == currentMonth && currentDay <= lastDay) {
            return Cell(firstIndex + daysInMonth + currentDay - 1)
        }

        return Cell.Undefined
    }

    fun contains(date: PackedDate): Boolean {
        return date.isBetween(firstCellInGridDate, lastCellInGridDate)
    }

    fun getCellRangeByDateRange(dateRange: PackedDateRange): CellRange {
        val startCell = getCellByDate(dateRange.start).orIfUndefined(Cell.Min)
        val endCell = getCellByDate(dateRange.end).orIfUndefined(Cell.Max)

        return CellRange(startCell, endCell)
    }

    fun getDateAtCell(cell: Cell): PackedDate {
        val index = cell.index

        val start = firstDayOfMonthCellIndex
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
        val start = firstDayOfMonthCellIndex
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

        for (i in 0 until GridConstants.CELL_COUNT - thisMonthEnd) {
            val index = thisMonthEnd + i
            val day = i + 1

            cells[index] = day.toByte()
        }
    }
}