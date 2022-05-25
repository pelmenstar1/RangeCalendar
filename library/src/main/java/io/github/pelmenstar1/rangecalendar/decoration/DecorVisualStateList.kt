package io.github.pelmenstar1.rangecalendar.decoration

import io.github.pelmenstar1.rangecalendar.selection.Cell

private typealias ForeachAction = (cell: Cell, value: CellDecor.VisualState) -> Unit

internal class DecorVisualStateList {
    @PublishedApi
    internal var elements = EMPTY_ARRAY

     var notNullElementsLength = 0
        private set

    inline fun forEachNotNull(action: ForeachAction) {
        if(notNullElementsLength == 0) {
            return
        }

        for(i in 0 until 42) {
            val value = elements[i]

            if(value != null) {
                action(Cell(i), value)
            }
        }
    }

    operator fun get(cell: Cell): CellDecor.VisualState? {
        return if(elements.isEmpty()) null else elements[cell.index]
    }

    operator fun set(cell: Cell, value: CellDecor.VisualState?) {
        var elements = elements

        if(elements.isEmpty()) {
            elements = arrayOfNulls(42)
            this.elements = elements
        }

        val index = cell.index
        val oldValue = elements[index]

        if(oldValue != value) {
            if((oldValue == null) and (value != null)) {
                notNullElementsLength++
            } else if((oldValue != null) and (value == null)) {
                notNullElementsLength--
            }

            elements[cell.index] = value
        }
    }

    companion object {
        private val EMPTY_ARRAY = emptyArray<CellDecor.VisualState?>()
    }
}