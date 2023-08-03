package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.github.pelmenstar1.rangecalendar.utils.Radii
import com.github.pelmenstar1.rangecalendar.utils.addRoundRectCompat
import com.github.pelmenstar1.rangecalendar.utils.getLazyValue

internal class SelectionShape {
    private var _path: Path? = null
    val path: Path?
        get() = _path

    private val shapeInfo = SelectionShapeInfo()
    private var origin = -1

    val bounds = RectF()

    private val topRect = RectF()

    private var _bottomRect: RectF? = null

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

    private fun getOrInitBottomRect(): RectF =
        getLazyValue(_bottomRect, ::RectF) { _bottomRect = it }

    fun update(newShapeInfo: SelectionShapeInfo, newOrigin: Int, flags: Int) {
        val forcePath = (flags and FLAG_FORCE_PATH) != 0
        val ignoreRoundRadii = (flags and FLAG_IGNORE_ROUND_RADII) != 0

        // If path is not created and the caller wants it to be created, do not early exit
        if (shapeInfo == newShapeInfo && origin == newOrigin && !(_path == null && forcePath)) {
            return
        }

        shapeInfo.set(newShapeInfo)
        origin = newOrigin

        val (start, end) = shapeInfo.range
        val rr = shapeInfo.roundRadius

        val gridYDiff = end.gridY - start.gridY

        updateRects(newShapeInfo, origin)

        if (ignoreRoundRadii || rr == 0f) {
            buildPathIgnoreRoundRadii()
            return
        }

        // If start and end are on the same row, we can draw the shape even without using Path.
        if (gridYDiff > 0) {
            val path = getEmptyPath()

            val startGridX = start.gridX
            val endGridX = end.gridX

            val bottomRect = _bottomRect!!

            Radii.withRadius(rr) {
                leftTop()
                rightTop()
                leftBottom(condition = startGridX != 0)
                rightBottom(condition = gridYDiff == 1 && endGridX != 6)

                path.addRoundRect(topRect, radii(), Path.Direction.CW)
            }

            Radii.withRadius(rr) {
                rightBottom()
                leftBottom()
                rightTop(condition = endGridX != 6)
                leftTop(condition = gridYDiff == 1 && startGridX != 0)

                path.addRoundRect(bottomRect, radii(), Path.Direction.CW)
            }

            if (gridYDiff > 1) {
                Radii.withRadius(rr) {
                    leftTop(condition = startGridX != 0)
                    rightBottom(condition = endGridX != 6)

                    path.addRoundRectCompat(
                        bottomRect.left,
                        topRect.bottom,
                        topRect.right,
                        bottomRect.top,
                        radii()
                    )
                }
            }
        } else {
            if (forcePath) {
                getEmptyPath().addRoundRect(topRect, rr, rr, Path.Direction.CW)
            }
        }
    }

    private fun updateRects(info: SelectionShapeInfo, origin: Int) {
        val (start, end) = info.range
        val startLeft = info.startLeft
        val startTop = info.startTop

        val endRight = info.endRight
        val endTop = info.endTop

        val cellHeight = info.cellHeight

        val endBottom = endTop + cellHeight

        val gridYDiff = end.gridY - start.gridY

        // If start and end are on the same row, we can draw the shape even without using Path.
        if (gridYDiff > 0) {
            val firstCellLeft = info.firstCellOnRowLeft
            val lastCellRight = info.lastCellOnRowRight

            // If there are more than 1 row, then the shape will always occupy space between first and last cells on a row.
            bounds.set(firstCellLeft, startTop, lastCellRight, endBottom)

            val bottomRect = getOrInitBottomRect()

            if (origin == ORIGIN_LOCAL) {
                val startBottom = startTop + cellHeight

                topRect.set(startLeft, startTop, lastCellRight, startBottom)
                bottomRect.set(firstCellLeft, endTop, endRight, endBottom)
            } else {
                val rowWidth = lastCellRight - firstCellLeft
                val bottomPartTop = endTop - startTop

                topRect.set(startLeft - firstCellLeft, 0f, rowWidth, cellHeight)
                bottomRect.set(0f, bottomPartTop, endRight - firstCellLeft, bottomPartTop + cellHeight)
            }
        } else {
            bounds.set(startLeft, startTop, endRight, endBottom)

            if (origin == ORIGIN_LOCAL) {
                topRect.set(bounds)
            } else {
                topRect.set(0f, 0f, endRight - startLeft, cellHeight)
            }
        }
    }

    private fun buildPathIgnoreRoundRadii() {
        val (start, end) = shapeInfo.range
        val gridYDiff = end.gridY - start.gridY

        val path = getEmptyPath()

        path.apply {
            if (gridYDiff == 0) {
                addRect(topRect, Path.Direction.CW)
            } else {
                val bottomRect = _bottomRect!!

                if (gridYDiff == 1 && topRect.left >= bottomRect.right) {
                    // 5 for each rect = 10
                    incReserve(10)

                    addRect(topRect, Path.Direction.CW)
                    addRect(bottomRect, Path.Direction.CW)
                } else {
                    // 1 moveTo + 3 lineTo + 1 close + 4 lineTo (in special cases)
                    incReserve(9)

                    moveTo(topRect.left, topRect.top)
                    lineTo(topRect.right, topRect.top)

                    if (bottomRect.right != topRect.right) {
                        lineTo(topRect.right, bottomRect.top)
                        lineTo(bottomRect.right, bottomRect.top)
                    }

                    lineTo(bottomRect.right, bottomRect.bottom)
                    lineTo(bottomRect.left, bottomRect.bottom)

                    if (bottomRect.left != topRect.left) {
                        lineTo(bottomRect.left, topRect.bottom)
                        lineTo(topRect.left, topRect.bottom)
                    }

                    close()
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

            canvas.drawRoundRect(topRect, rr, rr, paint)
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