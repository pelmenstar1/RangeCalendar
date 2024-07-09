package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.RectF

internal data class SelectionShapeInfo(
    var rangeStart: Int,
    var rangeEnd: Int,
    var startLeft: Float,
    var startTop: Float,
    var endRight: Float,
    var endTop: Float,
    var firstCellOnRowLeft: Float,
    var lastCellOnRowRight: Float,
    var cellWidth: Float,
    var cellHeight: Float,
    var roundRadius: Float,
    var useInMonthShape: Boolean,
    var inMonthShapeInfo: SelectionShapeInfo?
) {
    constructor() : this(
        rangeStart = 0, rangeEnd = -1,
        startLeft = 0f, startTop = 0f, endRight = 0f, endTop = 0f,
        firstCellOnRowLeft = 0f, lastCellOnRowRight = 0f,
        cellWidth = 0f, cellHeight = 0f,
        roundRadius = 0f,
        useInMonthShape = false,
        inMonthShapeInfo = null
    )

    fun set(other: SelectionShapeInfo) {
        rangeStart = other.rangeStart
        rangeEnd = other.rangeEnd
        startLeft = other.startLeft
        startTop = other.startTop
        endRight = other.endRight
        endTop = other.endTop
        firstCellOnRowLeft = other.firstCellOnRowLeft
        lastCellOnRowRight = other.lastCellOnRowRight
        cellWidth = other.cellWidth
        cellHeight = other.cellHeight
        roundRadius = other.roundRadius
        useInMonthShape = other.useInMonthShape
        inMonthShapeInfo = other.inMonthShapeInfo
    }

    fun overlaysRect(bounds: RectF): Boolean {
        val gridYDiff = Cell.gridY(rangeStart) - Cell.gridY(rangeEnd)

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