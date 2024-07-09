package com.github.pelmenstar1.rangecalendar

import com.github.pelmenstar1.rangecalendar.complexRange.cell.CellComplexRange
import com.github.pelmenstar1.rangecalendar.complexRange.date.DateComplexRange
import com.github.pelmenstar1.rangecalendar.complexRange.date.DateFragment
import com.github.pelmenstar1.rangecalendar.utils.getDaysInMonth

internal class YearMonthGridInfo {
    var year = 0
    var month = 0

    var firstDayOfMonthCellIndex = 0
    var firstCellInGridDate = PackedDate.INVALID
    var lastCellInGridDate = PackedDate.INVALID

    var daysInMonth = 0
    var daysInPrevMonth = 0

    val inMonthRange: IntRange
        get() {
            val start = firstDayOfMonthCellIndex

            return start..(start + daysInMonth - 1)
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

    fun getCellByDate(epochDay: Long): Int {
        return getCellByDate(PackedDate.fromEpochDay(epochDay))
    }

    fun getCellByDate(date: PackedDate, defaultValue: Int = -1): Int {
        val firstIndex = firstDayOfMonthCellIndex
        val (firstYear, firstMonth, firstDay) = firstCellInGridDate
        val (currentYear, currentMonth, currentDay) = date

        if (firstYear == currentYear && firstMonth == currentMonth && currentDay >= firstDay) {
            return currentDay - firstDay
        }

        if (currentYear == year && currentMonth == month) {
            return firstIndex + currentDay - 1
        }

        val (lastYear, lastMonth, lastDay) = lastCellInGridDate

        if (lastYear == currentYear && lastMonth == currentMonth && currentDay <= lastDay) {
            return firstIndex + daysInMonth + currentDay - 1
        }

        return defaultValue
    }

    fun contains(date: PackedDate): Boolean {
        return date.isBetween(firstCellInGridDate, lastCellInGridDate)
    }

    fun getCellRangeByDateRange(dateRange: DateComplexRange): CellComplexRange {
        var bits = 0L
        val gridStart = firstCellInGridDate
        val gridEnd = lastCellInGridDate

        run {
            dateRange.forEachFragment { dateFragment ->
                val startDate = dateFragment.start
                val endDate = dateFragment.endInclusive

                // If grid date range and given fragment intersects, then there's
                // an intersection we can add to the complex cell range.
                if (startDate <= gridEnd && gridStart <= endDate) {
                    val startCell = getCellByDate(startDate, defaultValue = 0)
                    val endCell = getCellByDate(endDate, defaultValue = GridConstants.CELL_COUNT - 1)

                    bits = bits or CellComplexRange.rawRangeMask(startCell, endCell)

                    if (bits == CellComplexRange.AllBits) {
                        // Bail out. Following fragments won't change the bits.
                        return@run
                    }
                }
            }
        }

        return CellComplexRange.createRaw(bits)
    }

    fun getDateAtCell(cellIndex: Int): PackedDate {
        val start = firstDayOfMonthCellIndex
        val monthEnd = start + daysInMonth - 1

        return when {
            cellIndex < start -> {
                var prevYear = year
                var prevMonth = month - 1

                if (prevMonth == 0) {
                    prevYear--
                    prevMonth = 1
                }

                val day = daysInPrevMonth - start + cellIndex + 1

                PackedDate(prevYear, prevMonth, day)
            }

            cellIndex <= monthEnd -> {
                val day = cellIndex - start + 1

                PackedDate(year, month, day)
            }

            else -> {
                val day = cellIndex - monthEnd

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

    fun getDateRangeByCellRange(cellComplexRange: CellComplexRange): DateComplexRange {
        return DateComplexRange {
            cellComplexRange.forEachFragment { start, endInclusive ->
                val startDate = getDateAtCell(start)
                val endDate = getDateAtCell(endInclusive)

                val dateFragment = DateFragment(startDate.toEpochDay(), endDate.toEpochDay())
                fragment(dateFragment)
            }
        }
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