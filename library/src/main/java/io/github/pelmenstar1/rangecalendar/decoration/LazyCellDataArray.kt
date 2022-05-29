@file:Suppress("UNCHECKED_CAST")

package io.github.pelmenstar1.rangecalendar.decoration

import io.github.pelmenstar1.rangecalendar.selection.Cell
import java.util.*

internal class LazyCellDataArray<T : Any> {
    @PublishedApi
    internal var elements = EMPTY_ARRAY

    var notNullElementsLength = 0
        private set

    inline fun forEachNotNull(action: (cell: Cell, value: T) -> Unit) {
        if(notNullElementsLength == 0) {
            return
        }

        for(i in 0 until 42) {
            val value = elements[i] as T?

            if(value != null) {
                action(Cell(i), value)
            }
        }
    }

    operator fun get(cell: Cell): T? {
        return if(elements.isEmpty()) null else elements[cell.index] as T?
    }

    fun clear() {
        Arrays.fill(elements, null)

        notNullElementsLength = 0
    }

    operator fun set(cell: Cell, value: T?) {
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
        private val EMPTY_ARRAY = emptyArray<Any?>()
    }
}