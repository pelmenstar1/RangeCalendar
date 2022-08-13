package com.github.pelmenstar1.rangecalendar.selection

import com.github.pelmenstar1.rangecalendar.*

@JvmInline
internal value class WideSelectionData(val bits: Long) {
    inline val date: PackedDate
        get() = PackedDate(bits.toInt())

    inline val weekYearMonth: YearMonth
        get() = YearMonth(unpackFirstInt(bits))

    inline val weekIndex: Int
        get() = unpackSecondInt(bits)

    inline val yearMonth: YearMonth
        get() = YearMonth(bits.toInt())

    inline val dateRange: PackedDateRange
        get() = PackedDateRange(bits)

    companion object {
        fun cell(date: PackedDate) =
            WideSelectionData(date.bits.toLong())
        fun week(ym: YearMonth, index: Int) =
            WideSelectionData(packInts(ym.totalMonths, index))
        fun month(ym: YearMonth) =
            WideSelectionData(ym.totalMonths.toLong())

        fun customRange(range: PackedDateRange) =
            WideSelectionData(range.bits)

        fun customRange(start: PackedDate, end: PackedDate)
            = customRange(PackedDateRange(start, end))
    }
}

@JvmInline
internal value class NarrowSelectionData(val bits: Int) {
    val cell: Cell
        get() = Cell(bits)

    val weekIndex: Int
        get() = bits

    val range: CellRange
        get() = CellRange(bits)

    companion object {
        val Undefined = NarrowSelectionData(0)

        fun cellSelection(value: Cell) = NarrowSelectionData(value.index)
        fun weekSelection(index: Int) = NarrowSelectionData(index)
        fun customRangeSelection(range: CellRange) = NarrowSelectionData(range.bits)
    }
}