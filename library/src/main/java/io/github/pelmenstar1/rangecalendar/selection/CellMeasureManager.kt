package io.github.pelmenstar1.rangecalendar.selection

/**
 * Provides a set of functions to determine cell position in the view.
 */
interface CellMeasureManager {
    /**
     * X-axis of the coordinate that specifies left corner of the first cell.
     */
    val firstCellLeft: Float

    /**
     * Y-axis of the coordinate that specifies top corner of the first cell.
     */
    val firstCellTop: Float

    /**
     * X-axis of the coordinate that specifies right corner of the last cell.
     */
    val lastCellRight: Float

    /**
     * Y-axis of the coordinate that specifies bottom corner of the last cell.
     */
    val lastCellBottom: Float

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

internal fun CellMeasureManager.getCellLeft(cell: Cell) = getCellLeft(cell.index)
internal fun CellMeasureManager.getCellTop(cell: Cell) = getCellTop(cell.index)