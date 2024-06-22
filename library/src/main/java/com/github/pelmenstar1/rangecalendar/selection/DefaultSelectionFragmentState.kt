package com.github.pelmenstar1.rangecalendar.selection

internal interface ShapeBasedSelectionFragmentState : SelectionFragmentState {
    val shapeInfo: SelectionShapeInfo
}

internal class DefaultSelectionFragmentState(
    override val shapeInfo: SelectionShapeInfo
) : ShapeBasedSelectionFragmentState {
    override val rangeStart: Int
        get() = shapeInfo.range.start.index

    override val rangeEnd: Int
        get() = shapeInfo.range.end.index

    override fun equals(other: Any?): Boolean {
        return other is DefaultSelectionFragmentState && shapeInfo == other.shapeInfo
    }

    override fun hashCode(): Int {
        return shapeInfo.hashCode()
    }

    override fun toString(): String {
        return "DefaultSelectionFragmentState(rangeStart=$rangeStart, rangeEnd=$rangeEnd)"
    }
}