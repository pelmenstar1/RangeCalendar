package com.github.pelmenstar1.rangecalendar.selection

import com.github.pelmenstar1.rangecalendar.complexRange.cell.CellComplexRange

/**
 * A set of properties which are needed to represent a selection.
 * The data in the implementation should be enough to render the state on a canvas.
 *
 * The implementations is excepted to be immutable.
 */
interface SelectionState {
    val fragments: List<SelectionFragmentState>
    val complexRange: CellComplexRange

    fun contains(cellIndex: Int): Boolean {
        return fragments.any { cellIndex in it.rangeStart..it.rangeEnd }
    }

    fun isSingleCell(cellIndex: Int): Boolean {
        val frags = fragments
        if (frags.size == 1) {
            val frag = frags[0]

            return frag.rangeStart == cellIndex && frag.rangeEnd == cellIndex
        }

        return false
    }
}

internal val SelectionState.isNone: Boolean
    get() = fragments.isEmpty()