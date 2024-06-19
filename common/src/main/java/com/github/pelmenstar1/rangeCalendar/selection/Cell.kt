package com.github.pelmenstar1.rangecalendar.selection

import com.github.pelmenstar1.rangecalendar.GridConstants

fun Cell(gridX: Int, gridY: Int): Cell {
    return Cell(gridY * GridConstants.COLUMN_COUNT + gridX)
}

@JvmInline
value class Cell(val index: Int) {
    val gridX: Int
        get() = index % GridConstants.COLUMN_COUNT

    val gridY: Int
        get() = index / GridConstants.COLUMN_COUNT

    val isUndefined: Boolean
        get() = index < 0

    val isDefined: Boolean
        get() = index >= 0

    operator fun component1() = gridX
    operator fun component2() = gridY

    fun orIfUndefined(value: Cell): Cell {
        return if (isUndefined) value else this
    }

    fun sameX(cell: Cell): Boolean = gridX == cell.gridX
    fun sameY(cell: Cell): Boolean = gridY == cell.gridY

    override fun toString(): String {
        return "Cell(index=$index)"
    }

    companion object {
        val Undefined = Cell(-1)

        val Min = Cell(0)
        val Max = Cell(GridConstants.CELL_COUNT - 1)
    }
}