@file:Suppress("NOTHING_TO_INLINE")

package com.github.pelmenstar1.rangecalendar.selection

import com.github.pelmenstar1.rangecalendar.packShorts
import com.github.pelmenstar1.rangecalendar.unpackFirstShort
import com.github.pelmenstar1.rangecalendar.unpackSecondShort
import kotlin.math.max
import kotlin.math.min

internal fun CellRange(start: Int, end: Int): CellRange {
    return CellRange(packShorts(start, end))
}

internal inline fun CellRange(start: Cell, end: Cell): CellRange {
    return CellRange(start.index, end.index)
}

@JvmInline
internal value class CellRange(val bits: Int) {
    val start: Cell
        get() = Cell(unpackFirstShort(bits))

    val end: Cell
        get() = Cell(unpackSecondShort(bits))

    val isValid: Boolean
        get() = start.index <= end.index

    val isSingleCell: Boolean
        get() = start == end

    inline operator fun component1() = start
    inline operator fun component2() = end

    fun hasIntersectionWith(other: CellRange): Boolean {
        return other.start.index <= end.index && start.index <= other.end.index
    }

    fun intersectionWith(other: CellRange): CellRange {
        val start = start.index
        val end = end.index
        val otherStart = other.start.index
        val otherEnd = other.end.index

        if (otherStart > end || start > otherEnd) {
            return Invalid
        }

        return CellRange(max(start, otherStart), min(end, otherEnd))
    }

    operator fun contains(cell: Cell): Boolean {
        return cell.index in start.index..end.index
    }

    fun normalize(): CellRange {
        val start = start.index
        val end = end.index

        return CellRange(min(start, end), max(start, end))
    }

    override fun toString(): String {
        return "CellRange(start=$start, end=$end)"
    }

    companion object {
        val Invalid = CellRange(0xFFFF_0000.toInt())

        fun single(cell: Cell) = single(cell.index)
        fun single(cellIndex: Int) = CellRange(cellIndex, cellIndex)

        fun week(index: Int): CellRange {
            val start = index * 7
            val end = start + 6

            return CellRange(start, end)
        }
    }
}