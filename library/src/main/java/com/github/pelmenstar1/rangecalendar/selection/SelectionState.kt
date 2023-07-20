package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.RectF

/**
 * A set of properties which are needed to represent a selection.
 * The data in the implementation should be enough to render the state on a canvas.
 *
 * The implementations is excepted to be immutable.
 */
interface SelectionState {
    /**
     * Represents a transitive state between two [SelectionState] instances.
     *
     * It still implements [SelectionState], although the transitive state is allowed not to have a notion of
     * a cell range on which the visual representation of the state is located because this range might be undefined.
     *
     * The implementation is expected to be mutable.
     */
    interface Transitive : SelectionState {
        /**
         * The state from which the transition starts.
         */
        val start: SelectionState

        /**
         * The state to which the transition should come.
         */
        val end: SelectionState

        /**
         * Returns whether the transitive state has the range ([rangeStart] and [rangeEnd] properties) defined.
         * If it returns `false`, [rangeStart] and [rangeEnd] properties are expected to throw.
         */
        val isRangeDefined: Boolean

        /**
         * Returns whether the selection overlays a rect specified by [bounds].
         * [bounds] rect should not be mutated.
         */
        fun overlaysRect(bounds: RectF): Boolean
    }

    /**
     * Index of the cell from which selection begins. Inclusive.
     */
    val rangeStart: Int

    /**
     * Index of the end cell in selection range. **Inclusive**
     */
    val rangeEnd: Int
}

internal inline val SelectionState.startCell: Cell
    get() = Cell(rangeStart)

internal inline val SelectionState.endCell: Cell
    get() = Cell(rangeEnd)

internal val SelectionState.range: CellRange
    get() = CellRange(rangeStart, rangeEnd)

internal val SelectionState.isNone: Boolean
    get() = rangeStart > rangeEnd

internal fun SelectionState.contains(cell: Cell): Boolean {
    return cell.index in rangeStart..rangeEnd
}

internal fun SelectionState.isSingleCell(cell: Cell): Boolean {
    return rangeStart == cell.index && rangeEnd == cell.index
}