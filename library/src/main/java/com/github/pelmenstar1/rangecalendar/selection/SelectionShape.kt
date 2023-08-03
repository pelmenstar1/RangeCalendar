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

    fun update(newPathInfo: SelectionShapeInfo, newOrigin: Int, flags: Int) {
        val forcePath = (flags and FLAG_FORCE_PATH) != 0
        val ignoreRoundRadii = (flags and FLAG_IGNORE_ROUND_RADII) != 0

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

        val cellHeight = shapeInfo.cellHeight
        val rr = shapeInfo.roundRadius

        val endBottom = endTop + cellHeight

        val gridYDiff = end.gridY - start.gridY

        // If start and end are on the same row, we can draw the shape even without using Path.
        if (gridYDiff > 0) {
            val path = getEmptyPath()

            val firstCellLeft = shapeInfo.firstCellOnRowLeft
            val lastCellRight = shapeInfo.lastCellOnRowRight

            // If there are more than 1 row, then the shape will always occupy space between first and last cells on a row.
            bounds.set(firstCellLeft, startTop, lastCellRight, endBottom)

            val rowWidth = lastCellRight - firstCellLeft

            val startGridX = start.gridX
            val endGridX = end.gridX

            val topPartLeft: Float
            val topPartTop: Float
            val topPartRight: Float
            val topPartBottom: Float

            val bottomPartLeft: Float
            val bottomPartTop: Float
            val bottomPartRight: Float
            val bottomPartBottom: Float

            val centerPartLeft: Float
            val centerPartTop: Float
            val centerPartRight: Float
            val centerPartBottom: Float

            if (origin == ORIGIN_LOCAL) {
                val startBottom = startTop + cellHeight

                topPartLeft = startLeft
                topPartTop = startTop
                topPartRight = lastCellRight
                topPartBottom = startBottom

                bottomPartLeft = firstCellLeft
                bottomPartTop = endTop
                bottomPartRight = endRight
                bottomPartBottom = endBottom

                centerPartLeft = firstCellLeft
                centerPartTop = startBottom
                centerPartRight = lastCellRight
                centerPartBottom = endTop
            } else {
                topPartLeft = startLeft - firstCellLeft
                topPartTop = 0f
                topPartRight = rowWidth
                topPartBottom = cellHeight

                bottomPartLeft = 0f
                bottomPartTop = endTop - startTop
                bottomPartRight = endRight - firstCellLeft
                bottomPartBottom = bottomPartTop + cellHeight

                centerPartLeft = 0f
                centerPartTop = cellHeight
                centerPartRight = rowWidth
                centerPartBottom = bottomPartTop
            }

            if (ignoreRoundRadii) {
                path.addRect(topPartLeft, topPartTop, topPartRight, topPartBottom, Path.Direction.CW)
                path.addRect(bottomPartLeft, bottomPartTop, bottomPartRight, bottomPartBottom, Path.Direction.CW)

                if (gridYDiff > 1) {
                    path.addRect(centerPartLeft, centerPartTop, centerPartRight, centerPartBottom, Path.Direction.CW)
                }
            } else {
                Radii.withRadius(rr) {
                    leftTop()
                    rightTop()
                    leftBottom(condition = startGridX != 0)
                    rightBottom(condition = gridYDiff == 1 && endGridX != 6)

                    path.addRoundRectCompat(topPartLeft, topPartTop, topPartRight, topPartBottom, radii())
                }

                Radii.withRadius(rr) {
                    rightBottom()
                    leftBottom()
                    rightTop(condition = endGridX != 6)
                    leftTop(condition = gridYDiff == 1 && startGridX != 0)

                    path.addRoundRectCompat(bottomPartLeft, bottomPartTop, bottomPartRight, bottomPartBottom, radii())
                }

                if (gridYDiff > 1) {
                    Radii.withRadius(rr) {
                        leftTop(condition = startGridX != 0)
                        rightBottom(condition = endGridX != 6)

                        path.addRoundRectCompat(centerPartLeft, centerPartTop, centerPartRight, centerPartBottom, radii())
                    }
                }
            }
        } else {
            // If start and end cells are on the same row, endBottom is startTop + cellHeight.
            bounds.set(startLeft, startTop, endRight, endBottom)

            if (forcePath) {
                val path = getEmptyPath()

                val left: Float
                val top: Float
                val right: Float
                val bottom: Float

                if (origin == ORIGIN_LOCAL) {
                    left = startLeft
                    top = startTop
                    right = endRight
                    bottom = endBottom
                } else {
                    left = 0f
                    top = 0f
                    right = endRight - startLeft
                    bottom = cellHeight
                }

                if (ignoreRoundRadii) {
                    path.addRect(left, top, right, bottom, Path.Direction.CW)
                } else {
                    path.addRoundRectCompat(left, top, right, bottom, rr)
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

        const val FLAG_FORCE_PATH = 1
        const val FLAG_IGNORE_ROUND_RADII = 1 shl 1
    }
}