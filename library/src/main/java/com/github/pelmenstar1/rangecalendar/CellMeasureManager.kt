package com.github.pelmenstar1.rangecalendar

import android.graphics.PointF

/**
 * Provides a set of functions to determine cell position in the view.
 *
 * **API surface of this class is not stable and new members might be added or removed.**
 */
interface CellMeasureManager {
    enum class CoordinateRelativity {
        VIEW,
        GRID
    }

    /**
     * Width of cell (in pixels).
     */
    val cellWidth: Float

    /**
     * Height of cell (in pixels).
     */
    val cellHeight: Float

    /**
     * Round radius of cell (in pixels).
     */
    val roundRadius: Float

    /**
     * Gets x-axis value of the coordinate that specifies left corner of the cell. The coordinate is relative to the grid.
     *
     * @param cellIndex index of the cell, should be in range 0..41
     * @throws IllegalArgumentException if [cellIndex] is out of the range 0..41
     */
    fun getCellLeft(cellIndex: Int): Float

    /**
     * Gets y-axis value of the coordinate that specifies top corner of the cell. The coordinate is relative to the grid.
     *
     * @param cellIndex index of the cell, should be in range 0..41
     * @throws IllegalArgumentException if [cellIndex] is out of the range 0..41
     */
    fun getCellTop(cellIndex: Int): Float

    /**
     * Gets distance of the cell with given [cellIndex]. The distance is defined by an implementation.
     * There's only one rule: distance should be monotonic, in other words, the greater index of a cell, the greater distance is.
     * It can be useful when a cell needs to be transitioned to another cell that is not on the same row.
     *
     * @param cellIndex index of the cell, should be in range 0..41
     */
    fun getCellDistance(cellIndex: Int): Float

    /**
     * Gets points on the calendar view and cell's index nearest to the point by cell [distance]. The point is set to [outPoint].
     * The coordinates of the point are relative to the grid.
     *
     * @param distance cell distance, expected to be non-negative
     * @param outPoint the resulting point is set to this point
     *
     * @return index of the cell nearest to the point
     */
    fun getCellAndPointByDistance(distance: Float, outPoint: PointF): Int

    /**
     * Gets index of a cell nearest to a point with specified coordinates. If there's no such cell, returns `-1`.
     */
    fun getCellAt(x: Float, y: Float, relativity: CoordinateRelativity): Int

    /**
     * Returns the absolute value of a dimension specified by the [anchor].
     */
    fun getRelativeAnchorValue(anchor: Distance.RelativeAnchor): Float
}

/**
 * Gets x-axis value of the coordinate that specifies right corner of the cell.
 *
 * @param cellIndex index of the cell, should be in range 0..41
 */
fun CellMeasureManager.getCellRight(cellIndex: Int): Float {
    return getCellLeft(cellIndex) + cellWidth
}

/**
 * Gets y-axis value of the coordinate that specifies bottom corner of the cell.
 *
 * @param cellIndex index of the cell, should be in range 0..41
 */
fun CellMeasureManager.getCellBottom(cellIndex: Int): Float {
    return getCellTop(cellIndex) + cellHeight
}