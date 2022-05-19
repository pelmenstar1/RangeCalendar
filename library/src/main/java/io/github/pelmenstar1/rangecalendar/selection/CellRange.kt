package io.github.pelmenstar1.rangecalendar.selection

import io.github.pelmenstar1.rangecalendar.packShorts
import io.github.pelmenstar1.rangecalendar.unpackFirstShort
import io.github.pelmenstar1.rangecalendar.unpackSecondShort

internal fun CellRange(start: Int, end: Int): CellRange {
    return CellRange(Cell(start), Cell(end))
}

internal fun CellRange(start: Cell, end: Cell): CellRange {
    return CellRange(packShorts(start.index, end.index))
}

@JvmInline
internal value class CellRange(val bits: Int) {
    val start: Cell
        get() = Cell(unpackFirstShort(bits))

    val end: Cell
        get() = Cell(unpackSecondShort(bits))

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
        val Invalid = CellRange(packShorts(-1, -1))

        fun cell(cell: Cell) = CellRange(cell, cell)
    }
}