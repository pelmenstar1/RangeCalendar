@file:Suppress("NOTHING_TO_INLINE")

package io.github.pelmenstar1.rangecalendar.selection

import kotlin.math.min
import kotlin.math.max

@JvmInline
internal value class Cell(val index: Int) {
    val gridX: Int
        get() = index % 7

    val gridY: Int
        get() = index / 7

    val isUndefined: Boolean
        get() = index < 0

    val isDefined: Boolean
        get() = index >= 0

    inline operator fun plus(value: Int): Cell = Cell(index + value)

    inline operator fun compareTo(cell: Cell): Int =  index - cell.index

    inline fun sameX(cell: Cell): Boolean = gridX == cell.gridX
    inline fun sameY(cell: Cell): Boolean = gridY == cell.gridY

    companion object {
        val Undefined = Cell(-1)
    }
}

internal inline fun min(a: Cell, b: Cell): Cell = Cell(min(a.index, b.index))
internal inline fun max(a: Cell, b: Cell): Cell = Cell(max(a.index, b.index))