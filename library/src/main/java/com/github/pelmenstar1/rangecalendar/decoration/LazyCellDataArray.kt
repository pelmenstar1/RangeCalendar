@file:Suppress("UNCHECKED_CAST")

package com.github.pelmenstar1.rangecalendar.decoration

import com.github.pelmenstar1.rangecalendar.selection.Cell
import java.util.*

/**
 * Represents a special array wrapper, which internal array is allocated only on writing.
 */
internal class LazyCellDataArray<T : Any> {
    @PublishedApi
    internal var elements = EMPTY_ARRAY

    var notNullElementsLength = 0
        private set

    inline fun forEachNotNull(action: (cell: Cell, value: T) -> Unit) {
        // Check if there's need to iterate through each element.
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
        // If elements are not initialized,
        // then writing to array was never happened, meaning all the elements must be null
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
            // Update not-null elements counter. I haven't found better way.

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