package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.RectF

internal data class SelectionShapeInfo(
    var range: CellRange,
    @JvmField var startLeft: Float,
    @JvmField var startTop: Float,
    @JvmField var endRight: Float,
    @JvmField var endTop: Float,
    @JvmField var firstCellOnRowLeft: Float,
    @JvmField var lastCellOnRowRight: Float,
    @JvmField var cellWidth: Float,
    @JvmField var cellHeight: Float,
    @JvmField var roundRadius: Float,
) {
    constructor() : this(CellRange.Invalid, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

    fun set(other: SelectionShapeInfo) {
        range = other.range
        startLeft = other.startLeft
        startTop = other.startTop
        endRight = other.endRight
        endTop = other.endTop
        firstCellOnRowLeft = other.firstCellOnRowLeft
        lastCellOnRowRight = other.lastCellOnRowRight
        cellWidth = other.cellWidth
        cellHeight = other.cellHeight
        roundRadius = other.roundRadius
    }

    fun overlaysRect(bounds: RectF): Boolean {
        val gridYDiff = range.end.gridY - range.start.gridY

        val startTop = startTop
        val startBottom = startTop + cellHeight

        if (gridYDiff == 0) {
            return bounds.intersects(startLeft, startTop, endRight, startBottom)
        } else {
            // Basically gridYDiff > 0 because gridYDiff is non-negative.

            val firstOnRowLeft = firstCellOnRowLeft
            val lastOnRowRight = lastCellOnRowRight

            // Check top part
            if (bounds.intersects(startLeft, startTop, lastOnRowRight, startBottom)) {
                return true
            }

            val endTop = endTop
            val endBottom = endTop + cellHeight

            // Check bottom
            if (bounds.intersects(firstOnRowLeft, endTop, endRight, endBottom)) {
                return true
            }

            // Check center part if it exists
            if (gridYDiff > 1) {
                return bounds.intersects(firstOnRowLeft, startBottom, lastOnRowRight, endTop)
            }

            return false
        }
    }

    fun clone(): SelectionShapeInfo {
        return SelectionShapeInfo().also { it.set(this) }
    }
}