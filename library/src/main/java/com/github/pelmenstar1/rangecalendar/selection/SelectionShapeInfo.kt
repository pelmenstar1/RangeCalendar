package com.github.pelmenstar1.rangecalendar.selection

internal data class SelectionShapeInfo(
    var range: CellRange,
    var startLeft: Float,
    var startTop: Float,
    var endRight: Float,
    var endTop: Float,
    var firstCellOnRowLeft: Float,
    var lastCellOnRowRight: Float,
    var gridTop: Float,
    var cellWidth: Float,
    var cellHeight: Float,
    var roundRadius: Float,
) {
    constructor() : this(CellRange.Invalid, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)

    fun set(other: SelectionShapeInfo) {
        range = other.range
        startLeft = other.startLeft
        startTop = other.startTop
        endRight = other.endRight
        endTop = other.endTop
        firstCellOnRowLeft = other.firstCellOnRowLeft
        lastCellOnRowRight = other.lastCellOnRowRight
        gridTop = other.gridTop
        cellWidth = other.cellWidth
        cellHeight = other.cellHeight
        roundRadius = other.roundRadius
    }
}