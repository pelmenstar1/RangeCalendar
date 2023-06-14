@file:Suppress("UNCHECKED_CAST")

package com.github.pelmenstar1.rangecalendar.decoration

import com.github.pelmenstar1.rangecalendar.selection.Cell
import java.util.*

/**
 * Represents a special array wrapper, which internal array is allocated only on writing.
 */
internal class LazyCellDataArray<T : Any> {
    @JvmField
    internal var elements = EMPTY_ARRAY

    @JvmField
    internal var notNullBits = 0L

    internal inline fun forEachNotNull(action: (cell: Cell, value: T) -> Unit) {
        val elems = elements

        // Original source: https://lemire.me/blog/2018/02/21/iterating-over-set-bits-quickly/
        var bits = notNullBits

        while (bits != 0L) {
            val t = bits and (-bits)
            val index = 63 - t.countLeadingZeroBits()

            action(Cell(index), elems[index] as T)

            bits = bits xor t
        }
    }

    operator fun get(cell: Cell): T? {
        // If notNullBits is 0, it means that all elements are null
        // Thus there's no sense in making an access even if the 'elements' isn't empty.
        return if(notNullBits == 0L) null else elements[cell.index] as T?
    }

    fun clear() {
        if (notNullBits != 0L) {
            Arrays.fill(elements, null)

            notNullBits = 0L
        }
    }

    operator fun set(cell: Cell, value: T?) {
        var elements = elements

        if(elements.isEmpty()) {
            elements = arrayOfNulls(42)
            this.elements = elements
        }

        val index = cell.index

        val mask = 1L shl index
        var bits = notNullBits
        bits = if (value == null) bits and mask.inv() else bits or mask

        elements[index] = value
        notNullBits = bits
    }

    companion object {
        private val EMPTY_ARRAY = emptyArray<Any?>()
    }
}