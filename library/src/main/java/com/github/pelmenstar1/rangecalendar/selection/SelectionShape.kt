package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.github.pelmenstar1.rangecalendar.utils.Radii
import com.github.pelmenstar1.rangecalendar.utils.addRoundRectCompat
import com.github.pelmenstar1.rangecalendar.utils.drawRoundRectCompat

internal class SelectionShape {
    private var _path: Path? = null
    val path: Path?
        get() = _path

    private val shapeInfo = SelectionShapeInfo()
    private var origin = -1

    val bounds = RectF()

    private fun getEmptyPath(): Path {
        var p = _path

        if (p == null) {
            p = Path()
            _path = p
        } else {
            p.rewind()
        }

        return p
    }

    fun update(newPathInfo: SelectionShapeInfo, newOrigin: Int, forcePath: Boolean) {
        // If path is not created and the caller wants it to be created, do not early exit
        if (shapeInfo == newPathInfo && origin == newOrigin && !(_path == null && forcePath)) {
            return
        }

        shapeInfo.set(newPathInfo)
        origin = newOrigin

        val (start, end) = shapeInfo.range
        val startLeft = shapeInfo.startLeft
        val startTop = shapeInfo.startTop

        val endRight = shapeInfo.endRight
        val endTop = shapeInfo.endTop

        val firstCellLeft = shapeInfo.firstCellOnRowLeft
        val lastCellRight = shapeInfo.lastCellOnRowRight

        val cellHeight = shapeInfo.cellHeight
        val rr = shapeInfo.roundRadius

        val startBottom = startTop + cellHeight
        val endBottom = endTop + cellHeight

        val gridYDiff = end.gridY - start.gridY

        // If start and end are on the same row, we can draw the shape even without using Path.
        if (gridYDiff > 0) {
            val path = getEmptyPath()

            // If there are more than 1 row, then the shape will always occupy space between first and last cells on a row.
            bounds.set(firstCellLeft, startTop, lastCellRight, endBottom)

            val rowWidth = lastCellRight - firstCellLeft

            val startGridX = start.gridX
            val endGridX = end.gridX

            Radii.withRadius(rr) {
                leftTop()
                rightTop()
                leftBottom(condition = startGridX != 0)
                rightBottom(condition = gridYDiff == 1 && endGridX != 6)

                if (origin == ORIGIN_LOCAL) {
                    path.addRoundRectCompat(
                        left = startLeft, top = startTop,
                        right = lastCellRight, bottom = startBottom,
                        radii()
                    )
                } else {
                    path.addRoundRectCompat(
                        left = startLeft - firstCellLeft, top = 0f,
                        right = rowWidth, bottom = cellHeight,
                        radii()
                    )
                }
            }

            Radii.withRadius(rr) {
                rightBottom()
                leftBottom()
                rightTop(condition = endGridX != 6)
                leftTop(condition = gridYDiff == 1 && startGridX != 0)

                if (origin == ORIGIN_LOCAL) {
                    path.addRoundRectCompat(
                        left = firstCellLeft, top = endTop,
                        right = endRight, bottom = endBottom,
                        radii()
                    )
                } else {
                    val partTop = endTop - startTop

                    path.addRoundRectCompat(
                        left = 0f, top = partTop,
                        right = endRight - firstCellLeft, bottom = partTop + cellHeight,
                        radii()
                    )
                }
            }

            if (gridYDiff > 1) {
                Radii.withRadius(rr) {
                    leftTop(condition = startGridX != 0)
                    rightBottom(condition = endGridX != 6)

                    if (origin == ORIGIN_LOCAL) {
                        path.addRoundRectCompat(
                            left = firstCellLeft, top = startBottom,
                            right = lastCellRight, bottom = endTop,
                            radii()
                        )
                    } else {
                        path.addRoundRectCompat(
                            left = 0f, top = cellHeight,
                            right = rowWidth, bottom = endTop - startTop,
                            radii()
                        )
                    }
                }
            }
        } else {
            // If start and end cells are on the same row, endBottom is startTop + cellHeight.
            bounds.set(startLeft, startTop, endRight, endBottom)

            if (forcePath) {
                val path = getEmptyPath()

                if (origin == ORIGIN_LOCAL) {
                    path.addRoundRect(bounds, rr, rr, Path.Direction.CW)
                } else {
                    path.addRoundRectCompat(0f, 0f, endRight - startLeft, cellHeight, rr)
                }
            }
        }
    }

    /**
     * Draws the selection shape on [canvas] using [paint]. The shape will be drawn relative to the [bounds].
     */
    fun draw(canvas: Canvas, paint: Paint) {
        val (startCell, endCell) = shapeInfo.range

        // If startCell and endCell are on the same row, the shape is a simple rect with round corners that can be rendered
        // even without using a Path.
        if (startCell.sameY(endCell)) {
            val rr = shapeInfo.roundRadius

            if (origin == ORIGIN_LOCAL) {
                // Draw in local coordinates.
                canvas.drawRoundRect(bounds, rr, rr, paint)
            } else {
                // Draw in 'bounds' coordinates -- the caller should translate the canvas to (bounds.left, bounds.top) point before
                // calling this method. Width and height of the shape remain the same.
                val width = bounds.width()

                // As the shape is on a single row, height is always the height of a cell.
                val height = shapeInfo.cellHeight

                canvas.drawRoundRectCompat(0f, 0f, width, height, rr, paint)
            }
        } else {
            // _path should not be null if updateShape() has been called.
            canvas.drawPath(_path!!, paint)
        }
    }

    companion object {
        const val ORIGIN_LOCAL = 0
        const val ORIGIN_BOUNDS = 1
    }
}