package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.PointF

/**
 * Provides a set of functions to determine cell position in the view.
 *
 * **API surface of this class is not stable and new members might be added or removed.**
 */
interface CellMeasureManager {
    /**
     * Width of cell (in pixels).
     */
    val cellWidth: Float

    /**
     * Height of cell (in pixels).
     */
    val cellHeight: Float

    val roundRadius: Float

    /**
     * Gets x-axis value of the coordinate that specifies left corner of the cell.
     *
     * @param cellIndex index of the cell, should be in range 0..41
     * @throws IllegalArgumentException if [cellIndex] is out of the range 0..41
     */
    fun getCellLeft(cellIndex: Int): Float

    /**
     * Gets y-axis value of the coordinate that specifies top corner of the cell.
     *
     * @param cellIndex index of the cell, should be in range 0..41
     * @throws IllegalArgumentException if [cellIndex] is out of the range 0..41
     */
    fun getCellTop(cellIndex: Int): Float

    fun getCellDistance(cellIndex: Int): Float

    fun getCellAndPointByDistance(distance: Float, outPoint: PointF): Int

    fun getXOfCellDistance(distance: Float): Float

    fun getCellFractionByDistance(distance: Float): Float
}

/**
 * Gets x-axis value of the coordinate that specifies right corner of the cell.
 *
 * @param cellIndex index of the cell, should be in range 0..42
 */
fun CellMeasureManager.getCellRight(cellIndex: Int): Float {
    return getCellLeft(cellIndex) + cellWidth
}

/**
 * Gets y-axis value of the coordinate that specifies bottom corner of the cell.
 *
 * @param cellIndex index of the cell, should be in range 0..42
 */
fun CellMeasureManager.getCellBottom(cellIndex: Int): Float {
    return getCellTop(cellIndex) + cellHeight
}

internal fun CellMeasureManager.getCellLeft(cell: Cell) = getCellLeft(cell.index)
internal fun CellMeasureManager.getCellTop(cell: Cell) = getCellTop(cell.index)