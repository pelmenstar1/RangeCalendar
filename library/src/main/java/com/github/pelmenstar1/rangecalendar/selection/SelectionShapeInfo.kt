package com.github.pelmenstar1.rangecalendar.selection

internal class SelectionShapeInfo(
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

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null || javaClass != other.javaClass) return false

        other as SelectionShapeInfo

        return range == other.range &&
                startLeft == other.startLeft && startTop == other.startTop &&
                endRight == other.endRight && endTop == other.endTop &&
                firstCellOnRowLeft == other.firstCellOnRowLeft && lastCellOnRowRight == other.lastCellOnRowRight &&
                cellWidth == other.cellWidth && cellHeight == other.cellHeight &&
                roundRadius == other.roundRadius

    }

    override fun hashCode(): Int {
        var result = range.hashCode()
        result = result * 31 + startLeft.toBits()
        result = result * 31 + startTop.toBits()
        result = result * 31 + endRight.toBits()
        result = result * 31 + endTop.toBits()
        result = result * 31 + firstCellOnRowLeft.toBits()
        result = result * 31 + lastCellOnRowRight.toBits()
        result = result * 31 + cellWidth.toBits()
        result = result * 31 + cellHeight.toBits()
        result = result * 31 + roundRadius.toBits()

        return result
    }

    fun clone(): SelectionShapeInfo {
        return SelectionShapeInfo().also { it.set(this) }
    }
}