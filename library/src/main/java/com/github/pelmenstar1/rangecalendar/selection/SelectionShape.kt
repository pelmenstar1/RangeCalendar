package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.component3
import androidx.core.graphics.component4
import com.github.pelmenstar1.rangecalendar.utils.getLazyValue
import kotlin.math.min

internal class SelectionShape {
    private var _path: Path? = null
    val path: Path?
        get() = _path

    private val shapeInfo = SelectionShapeInfo()
    private var origin = -1
    private var type = -1

    val bounds = RectF()

    val upperRect = RectF()
    private var _lowerRect: RectF? = null

    private fun getOrInitPath(): Path {
        return getLazyValue(_path, ::Path) { _path = it }
    }

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

    private fun clearPath() {
        _path?.rewind()
    }

    private fun getOrInitLowerRect(): RectF =
        getLazyValue(_lowerRect, ::RectF) { _lowerRect = it }

    fun update(newShapeInfo: SelectionShapeInfo, newOrigin: Int, forcePath: Boolean) {
        // If path is not created and the caller wants it to be created, do not early exit
        if (shapeInfo == newShapeInfo && origin == newOrigin && !(_path == null && forcePath)) {
            return
        }

        shapeInfo.set(newShapeInfo)
        origin = newOrigin

        val (start, end) = shapeInfo.range
        var rr = shapeInfo.roundRadius

        val gridYDiff = end.gridY - start.gridY

        updateRects(newShapeInfo, origin)

        // If start and end are on the same row, we can draw the shape even without using Path.
        if (gridYDiff > 0) {
            rr = min(rr, shapeInfo.cellHeight * 0.5f)

            buildPath(rr, forcePath)
        } else {
            type = TYPE_STANDALONE_RECT

            if (forcePath) {
                getEmptyPath().addRoundRect(upperRect, rr, rr, Path.Direction.CW)
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

            val bottomRect = getOrInitLowerRect()

            if (origin == ORIGIN_LOCAL) {
                val startBottom = startTop + cellHeight

                upperRect.set(startLeft, startTop, lastCellRight, startBottom)
                bottomRect.set(firstCellLeft, endTop, endRight, endBottom)
            } else {
                val rowWidth = lastCellRight - firstCellLeft
                val bottomPartTop = endTop - startTop

                upperRect.set(startLeft - firstCellLeft, 0f, rowWidth, cellHeight)
                bottomRect.set(0f, bottomPartTop, endRight - firstCellLeft, bottomPartTop + cellHeight)
            }
        } else {
            bounds.set(startLeft, startTop, endRight, endBottom)

            if (origin == ORIGIN_LOCAL) {
                upperRect.set(bounds)
            } else {
                upperRect.set(0f, 0f, endRight - startLeft, cellHeight)
            }
        }
    }

    // Expects that end.gridY - start.gridY > 0
    private fun buildPath(rr: Float, forcePath: Boolean) {
        if (rr > 0f) {
            buildPathWithRoundRadii(rr, forcePath)
        } else {
            buildPathNoRoundRadii(forcePath)
        }
    }

    private fun buildPathNoRoundRadii(forcePath: Boolean) {
        val (start, end) = shapeInfo.range
        val gridYDiff = end.gridY - start.gridY

        // Clear the path if it's created.
        clearPath()

        val lowerRect = _lowerRect!!

        val (upperPartLeft, upperPartTop, upperPartRight, upperPartBottom) = upperRect
        val (lowerPartLeft, lowerPartTop, lowerPartRight, lowerPartBottom) = lowerRect

        if (gridYDiff == 1 && upperPartLeft >= lowerPartRight) {
            type = TYPE_TWO_STANDALONE_RECTS

            if (forcePath) {
                val path = getOrInitPath()

                path.addRect(upperRect, Path.Direction.CW)
                path.addRect(lowerRect, Path.Direction.CW)
            }
        } else {
            type = TYPE_COMPLEX

            getOrInitPath().apply {
                moveTo(upperPartLeft, upperPartTop)
                lineTo(upperPartRight, upperPartTop)

                if (lowerPartRight != upperPartRight) {
                    lineTo(upperPartRight, lowerPartTop)
                    lineTo(lowerPartRight, lowerPartTop)
                }

                lineTo(lowerPartRight, lowerPartBottom)
                lineTo(lowerPartLeft, lowerPartBottom)

                if (lowerPartLeft != upperPartLeft) {
                    lineTo(lowerPartLeft, upperPartBottom)
                    lineTo(upperPartLeft, upperPartBottom)
                }

                close()
            }
        }
    }

    /**
     * Clears the previous path data and builds the new one.
     * Expects that end.gridY - start.gridY > 0 and rr > 0f && rr <= gridInfo.cellHeight * 0.5f
     * The method doesn't build path if possible, though it can be forced by [forcePath].
     */
    private fun buildPathWithRoundRadii(rr: Float, forcePath: Boolean) {
        val (start, end) = shapeInfo.range
        val gridYDiff = end.gridY - start.gridY

        // Clear the path if it's created.
        clearPath()

        val lowerRect = _lowerRect!!

        val (upperPartLeft, upperPartTop, upperPartRight, upperPartBottom) = upperRect
        val (lowerPartLeft, lowerPartTop, lowerPartRight, lowerPartBottom) = lowerRect

        // Check if there's a common part between top and bottom parts
        // This shortcut doesn't work if gridYDiff > 1 because in that case, there's also a center part
        if (gridYDiff == 1 && upperPartLeft + rr >= lowerPartRight - rr) {
            type = TYPE_TWO_STANDALONE_RECTS

            if (forcePath) {
                val path = getOrInitPath()

                path.addRoundRect(upperRect, rr, rr, Path.Direction.CW)
                path.addRoundRect(lowerRect, rr, rr, Path.Direction.CW)
            }
        } else {
            type = TYPE_COMPLEX

            getOrInitPath().apply {
                // The logic treats y-radius as equal to x-radius
                // y-radius is always less or equal to the half of cell height - height of the part,
                // but x-radius can be greater than width of the respective part. It causes
                // visual artifacts.
                val upperPartRx = min(rr, (upperPartRight - upperPartLeft) * 0.5f)
                val lowerPartRx = min(rr, (lowerPartRight - lowerPartLeft) * 0.5f)

                val end1Y = upperPartTop + rr
                val start2Y = lowerPartTop - rr
                val end3Y = lowerPartTop + rr
                val start4Y = lowerPartBottom - rr
                val end5Y = start4Y
                val start6Y = upperPartBottom + rr
                val end7Y = upperPartBottom - rr
                val start8Y = end1Y

                // Second and sixth corner can belong to either center part or upper/below part
                // based on these conditions.
                val secondCornerRx = if (start2Y == end1Y) upperPartRx else rr
                val sixthCornerRx = if (start6Y == end5Y) lowerPartRx else rr

                val start1X = upperPartRight - upperPartRx
                val end2X = upperPartRight - secondCornerRx
                val start3X = lowerPartRight - lowerPartRx
                val end4X = start3X
                val start5X = lowerPartLeft + lowerPartRx
                val end6X = lowerPartLeft + sixthCornerRx
                val start7X = upperPartLeft + upperPartRx
                val end8X = start7X

                moveTo(end8X, upperPartTop)
                lineTo(start1X, upperPartTop)

                // 1st corner
                addRoundCorner(
                    start1X, end1Y, upperPartRight, end1Y,
                    upperPartRx, rr,
                    CORNER_RT
                )

                if (lowerPartRight != upperPartRight) {
                    if (start2Y != end1Y) {
                        lineTo(upperPartRight, start2Y)
                    }

                    // 2nd corner
                    addRoundCorner(
                        end2X, start2Y, end2X, lowerPartTop,
                        secondCornerRx, rr,
                        CORNER_RB
                    )
                    lineTo(start3X, lowerPartTop)

                    // 3rd corner
                    addRoundCorner(
                        start3X, end3Y, lowerPartRight, end3Y,
                        lowerPartRx, rr,
                        CORNER_RT
                    )
                }

                lineTo(lowerPartRight, start4Y)

                // 4th corner
                addRoundCorner(
                    end4X, start4Y, end4X, lowerPartBottom,
                    lowerPartRx, rr,
                    CORNER_RB
                )

                lineTo(start5X, lowerPartBottom)

                // 5th corner
                addRoundCorner(
                    start5X, end5Y, lowerPartLeft, end5Y,
                    lowerPartRx, rr,
                    CORNER_LB
                )

                if (lowerPartLeft != upperPartLeft) {
                    if (start6Y != end5Y) {
                        lineTo(lowerPartLeft, start6Y)
                    }

                    // 6th corner
                    addRoundCorner(
                        end6X, start6Y, end6X, upperPartBottom,
                        sixthCornerRx, rr,
                        CORNER_LT
                    )

                    lineTo(start7X, upperPartBottom)

                    // 7th corner
                    addRoundCorner(
                        start7X, end7Y, upperPartLeft, end7Y,
                        upperPartRx, rr,
                        CORNER_LB
                    )
                }

                lineTo(upperPartLeft, start8Y)

                // 8th corner
                addRoundCorner(
                    end8X, start8Y, end8X, upperPartTop,
                    upperPartRx, rr,
                    CORNER_LT
                )

                close()
            }
        }
    }

    fun draw(canvas: Canvas, paint: Paint) {
        val rr = shapeInfo.roundRadius

        when (type) {
            TYPE_STANDALONE_RECT -> {
                canvas.drawRoundRect(upperRect, rr, rr, paint)
            }
            TYPE_TWO_STANDALONE_RECTS -> {
                canvas.drawRoundRect(upperRect, rr, rr, paint)
                canvas.drawRoundRect(_lowerRect!!, rr, rr, paint)
            }
            TYPE_COMPLEX -> {
                canvas.drawPath(_path!!, paint)
            }
        }
    }

    companion object {
        const val ORIGIN_LOCAL = 0
        const val ORIGIN_BOUNDS = 1

        private const val TYPE_STANDALONE_RECT = 0
        private const val TYPE_TWO_STANDALONE_RECTS = 1
        private const val TYPE_COMPLEX = 2

        // "Approximation of a cubic bezier curve by circular arcs and vice versa", by Aleksas Riškus.
        // In the paper, this constant is called 'k'
        private const val BEZIER_CIRCULAR_ARC_COEFFICIENT = 0.5522848f

        private const val CORNER_X_POSITIVE_FLAG = 1
        private const val CORNER_Y_POSITIVE_FLAG = 1 shl 1

        private const val CORNER_LT = CORNER_Y_POSITIVE_FLAG
        private const val CORNER_RT = CORNER_X_POSITIVE_FLAG or CORNER_Y_POSITIVE_FLAG
        private const val CORNER_RB = CORNER_X_POSITIVE_FLAG
        private const val CORNER_LB = 0

        private fun Path.addRoundCorner(
            originX: Float, originY: Float,
            endX: Float, endY: Float,
            rx: Float, ry: Float,
            cornerType: Int
        ) {
            val rrx = if ((cornerType and CORNER_X_POSITIVE_FLAG) != 0) rx else -rx
            val rry = if ((cornerType and CORNER_Y_POSITIVE_FLAG) != 0) -ry else ry

            val c1x = originX + rrx * BEZIER_CIRCULAR_ARC_COEFFICIENT
            val c1y = originY + rry

            val c2x = originX + rrx
            val c2y = originY + rry * BEZIER_CIRCULAR_ARC_COEFFICIENT

            if (cornerType == CORNER_RT || cornerType == CORNER_LB) {
                cubicTo(c1x, c1y, c2x, c2y, endX, endY)
            } else {
                cubicTo(c2x, c2y, c1x, c1y, endX, endY)
            }
        }
    }
}