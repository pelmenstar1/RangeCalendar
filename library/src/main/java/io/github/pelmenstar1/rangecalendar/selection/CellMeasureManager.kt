package io.github.pelmenstar1.rangecalendar.selection

import android.graphics.PointF

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

    /**
     * Linear interpolate position of the left corner of the start cell.
     * to position of the left corner of the end cell.
     *
     * @param startIndex index of the start cell
     * @param endIndex index of the end cell
     * @param fraction fraction of the interpolation
     * @param outPoint point to write the result position to.
     *
     * @throws IllegalArgumentException if [startIndex] or [endIndex] are out of the range 0..42
     */
    fun lerpCellLeft(startIndex: Int, endIndex: Int, fraction: Float, outPoint: PointF)

    /**
     * Linear interpolate position of the right corner of the start cell.
     * to position of the right corner of the end cell.
     *
     * @param startIndex index of the start cell
     * @param endIndex index of the end cell
     * @param fraction fraction of the interpolation
     * @param outPoint point to write the result position to.
     *
     * @throws IllegalArgumentException if [startIndex] or [endIndex] are out of the range 0..42
     */
    fun lerpCellRight(startIndex: Int, endIndex: Int, fraction: Float, outPoint: PointF)
}

fun CellMeasureManager.getCellRight(cellIndex: Int): Float {
    return getCellLeft(cellIndex) + cellSize
}

internal fun CellMeasureManager.getCellLeft(cell: Cell) = getCellLeft(cell.index)
internal fun CellMeasureManager.getCellTop(cell: Cell) = getCellTop(cell.index)