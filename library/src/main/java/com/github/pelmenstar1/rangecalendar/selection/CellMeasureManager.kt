package com.github.pelmenstar1.rangecalendar.selection

/**
 * Provides a set of functions to determine cell position in the view.
 */
interface CellMeasureManager {
    /**
     * Size of a cell, measured in pixels.
     */
    val cellSize: Float

    /**
     * Gets x-axis value of the coordinate that specifies left corner of the cell.
     *
     * @param cellIndex index of the cell, should be in range 0..42
     * @throws IllegalArgumentException if [cellIndex] is out of the range 0..42
     */
    fun getCellLeft(cellIndex: Int): Float

    /**
     * Gets y-axis value of the coordinate that specifies top corner of the cell.
     *
     * @param cellIndex index of the cell, should be in range 0..42
     * @throws IllegalArgumentException if [cellIndex] is out of the range 0..42
     */
    fun getCellTop(cellIndex: Int): Float
}

fun CellMeasureManager.getCellRight(cellIndex: Int): Float {
    return getCellLeft(cellIndex) + cellSize
}

internal fun CellMeasureManager.getCellLeft(cell: Cell) = getCellLeft(cell.index)
internal fun CellMeasureManager.getCellTop(cell: Cell) = getCellTop(cell.index)