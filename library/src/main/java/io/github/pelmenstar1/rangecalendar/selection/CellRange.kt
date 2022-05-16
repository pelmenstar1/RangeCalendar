package io.github.pelmenstar1.rangecalendar.selection

import io.github.pelmenstar1.rangecalendar.ShortRange
import kotlin.math.max
import kotlin.math.min

internal fun CellRange(start: Int, end: Int): CellRange {
    return CellRange(Cell(start), Cell(end))
}

internal fun CellRange(start: Cell, end: Cell): CellRange {
    return CellRange(ShortRange.create(start.index, end.index))
}

@JvmInline
internal value class CellRange(val range: Int) {
    val start: Cell
        get() = Cell(ShortRange.getStart(range))

    val end: Cell
        get() = Cell(ShortRange.getEnd(range))

    val cell: Cell
        get() = start

    fun intersectionWidth(other: CellRange): CellRange {
        val start = start
        val end = end
        val otherStart = other.start
        val otherEnd = other.end

        if (otherStart > end || start > otherEnd) {
            return Invalid
        }

        return CellRange(max(start, otherStart), min(end, otherEnd))
    }

    fun contains(cell: Cell): Boolean {
        return cell.index in start.index..end.index
    }

    fun normalize(): CellRange {
        val start = start
        val end = end

        return CellRange(min(start, end), max(start, end))
    }

    companion object {
        val Invalid = CellRange(ShortRange.create(-1, -1))

        fun cell(cell: Cell) = CellRange(ShortRange.create(cell.index, cell.index))
    }
}