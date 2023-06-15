@file:Suppress("NOTHING_TO_INLINE")

package com.github.pelmenstar1.rangecalendar.selection

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

    inline fun sameX(cell: Cell): Boolean = gridX == cell.gridX
    inline fun sameY(cell: Cell): Boolean = gridY == cell.gridY

    override fun toString(): String {
        return "Cell(index=$index)"
    }

    companion object {
        val Undefined = Cell(-1)
    }
}