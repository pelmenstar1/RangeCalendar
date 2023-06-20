package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.github.pelmenstar1.rangecalendar.utils.Radii
import com.github.pelmenstar1.rangecalendar.utils.addRoundRectCompat

internal class SelectionShape {
    private val path = Path()
    private val pathInfo = SelectionShapeInfo()

    val bounds = RectF()

    fun updateShapeIfNecessary(newPathInfo: SelectionShapeInfo) {
        if (pathInfo == newPathInfo) {
            // Path hasn't changed. No need for update.
            return
        }

        pathInfo.set(newPathInfo)

        val (start, end) = pathInfo.range

        val startLeft = pathInfo.startLeft
        val startTop = pathInfo.startTop

        val endRight = pathInfo.endRight
        val endTop = pathInfo.endTop

        val firstCellLeft = pathInfo.firstCellOnRowLeft
        val lastCellRight = pathInfo.lastCellOnRowRight

        val cellHeight = pathInfo.cellHeight
        val rr = pathInfo.roundRadius

        val startBottom = startTop + cellHeight
        val endBottom = endTop + cellHeight

        val gridYDiff = end.gridY - start.gridY

        path.rewind()
        bounds.set(firstCellLeft, startTop, lastCellRight, endBottom)

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
    }

    fun draw(canvas: Canvas, paint: Paint) {
        canvas.drawPath(path, paint)
    }
}