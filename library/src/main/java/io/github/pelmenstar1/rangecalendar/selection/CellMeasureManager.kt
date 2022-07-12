package io.github.pelmenstar1.rangecalendar.selection

interface CellMeasureManager {
    val firstCellLeft: Float
    val lastCellRight: Float

    val gridTop: Float
    val gridBottom: Float

    fun getCellLeft(cellIndex: Int): Float
    fun getCellTop(cellIndex: Int): Float
}

internal fun CellMeasureManager.getCellLeft(cell: Cell) = getCellLeft(cell.index)
internal fun CellMeasureManager.getCellTop(cell: Cell) = getCellTop(cell.index)