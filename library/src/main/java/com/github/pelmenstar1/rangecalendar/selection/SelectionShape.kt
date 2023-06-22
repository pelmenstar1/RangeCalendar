package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.github.pelmenstar1.rangecalendar.utils.Radii
import com.github.pelmenstar1.rangecalendar.utils.addRoundRectCompat
import com.github.pelmenstar1.rangecalendar.utils.drawRoundRectCompat

internal class SelectionShape {
    // Initialized when necessary.
    private var path: Path? = null
    private val shapeInfo = SelectionShapeInfo()

    val bounds = RectF()

    private fun getEmptyPath(): Path {
        var p = path

        if (p == null) {
            p = Path()
            path = p
        } else {
            p.rewind()
        }

        return p
    }

    fun updateShapeIfNecessary(newPathInfo: SelectionShapeInfo) {
        if (shapeInfo == newPathInfo) {
            // Path hasn't changed. No need for update.
            return
        }

        shapeInfo.set(newPathInfo)

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

            path.rewind()

            Radii.withRadius(rr) {
                leftTop()
                rightTop()
                leftBottom(condition = start.gridX != 0)
                rightBottom(condition = gridYDiff == 1 && end.gridX != 6)

                path.addRoundRectCompat(startLeft, startTop, lastCellRight, startBottom, radii())
            }

            Radii.withRadius(rr) {
                rightBottom()
                leftBottom()
                rightTop(condition = end.gridX != 6)
                leftTop(condition = gridYDiff == 1 && start.gridX != 0)

                path.addRoundRectCompat(firstCellLeft, startBottom, endRight, endBottom, radii())
            }

            if (gridYDiff > 1) {
                Radii.withRadius(rr) {
                    leftTop(condition = start.gridX != 0)
                    rightBottom(condition = end.gridX != 6)

                    path.addRoundRectCompat(
                        firstCellLeft, startBottom,
                        lastCellRight, endTop,
                        radii()
                    )
                }
            }
        } else {
            bounds.set(startLeft, startTop, endRight, endBottom)
        }
    }

    fun draw(canvas: Canvas, paint: Paint) {
        val (startCell, endCell) = shapeInfo.range

        if (startCell.sameY(endCell)) {
            val startTop = shapeInfo.startTop
            val startBottom = startTop + shapeInfo.cellHeight

            canvas.drawRoundRectCompat(
                shapeInfo.startLeft, startTop, shapeInfo.endRight, startBottom,
                shapeInfo.roundRadius,
                paint
            )
        } else {
            // Path should not be null if updateShapeIfNecessary was called and startCell, endCell aren't on the same row.
            canvas.drawPath(path!!, paint)
        }
    }
}