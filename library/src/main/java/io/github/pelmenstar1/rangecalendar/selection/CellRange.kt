@file:Suppress("NOTHING_TO_INLINE")

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

    inline operator fun component1() = start
    inline operator fun component2() = end

    fun hasIntersectionWith(other: CellRange): Boolean {
        return !(other.start > end || start > other.end)
    }

    fun intersectionWith(other: CellRange): CellRange {
        val (start, end) = this
        val (otherStart, otherEnd) = other

        if (otherStart > end || start > otherEnd) {
            return Invalid
        }

        return CellRange(max(start, otherStart), min(end, otherEnd))
    }

    fun contains(cell: Cell) = contains(cell.index)

    fun contains(cellIndex: Int): Boolean {
        return cellIndex in start.index..end.index
    }

    fun normalize(): CellRange {
        val (start, end) = this

        return CellRange(min(start, end), max(start, end))
    }

    companion object {
        val Invalid = CellRange(packShorts(-1, -1))

        fun cell(cell: Cell) = CellRange(cell, cell)

        fun week(index: Int): CellRange {
            val start = index * 7
            val end = start + 6

            return CellRange(start, end)
        }
    }
}