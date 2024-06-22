package com.github.pelmenstar1.rangecalendar.selection

import com.github.pelmenstar1.rangecalendar.complexRange.cell.CellComplexRange

internal class DefaultSelectionState(
    override val fragments: List<SelectionFragmentState>,
    override val complexRange: CellComplexRange
) : SelectionState {

    override fun contains(cellIndex: Int): Boolean {
        return complexRange.contains(cellIndex)
    }

    override fun isSingleCell(cellIndex: Int): Boolean {
        return complexRange.isSingleCell(cellIndex)
    }
}