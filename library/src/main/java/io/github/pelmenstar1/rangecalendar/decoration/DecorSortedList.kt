package io.github.pelmenstar1.rangecalendar.decoration

import io.github.pelmenstar1.rangecalendar.PackedIntRange
import io.github.pelmenstar1.rangecalendar.selection.Cell

internal class DecorSortedList {
    @PublishedApi
    internal var elements: Array<CellDecor<*>>

    val size: Int
        get() = elements.size

    val readonlyElements: Array<out CellDecor<*>>
        get() = elements

    constructor() {
        elements = emptyArray()
    }

    constructor(capacity: Int) {
        elements = unsafeNewArray(capacity)
    }

    operator fun get(index: Int): CellDecor<*> {
        if (index !in elements.indices) {
            throw IndexOutOfBoundsException()
        }

        return getNoCheck(index)
    }

    private fun getNoCheck(index: Int): CellDecor<*> {
        return elements[index]
    }

    fun indexOf(value: CellDecor<*>): Int {
        return elements.indexOf(value)
    }

    inline fun forEach(block: (CellDecor<*>) -> Unit) {
        elements.forEach(block)
    }

    inline fun forEachBreakable(block: (CellDecor<*>) -> Boolean) {
        for(element in elements) {
            val toBreak = block(element)

            if(toBreak) {
                break
            }
        }
    }

    fun contains(value: CellDecor<*>): Boolean {
        return indexOf(value) >= 0
    }

    fun add(value: CellDecor<*>): Int {
        val index = findIndexForNewElement(value)

        allocatePlaceForInsert(index, 1)
        elements[index] = value

        return index
    }

    fun<T: CellDecor<T>> addAll(values: Array<out CellDecor<T>>): Int {
        val index = findIndexForNewElement(values[0])

        allocatePlaceForInsert(index, values.size)

        System.arraycopy(values, 0, elements, index, values.size)

        return index
    }

    private fun allocatePlaceForInsert(pos: Int, length: Int) {
        val newElements = unsafeNewArray(elements.size + length)

        System.arraycopy(elements, 0, newElements, 0, pos)
        System.arraycopy(elements, pos, newElements, pos + length, elements.size - pos)

        elements = newElements
    }

    inline fun iterateRegions(block: (PackedIntRange, Cell) -> Unit) {
        if (elements.isEmpty()) return

        var i = 0
        val size = elements.size

        while (i < size) {
            val cell = getNoCheck(i).cell

            val end = getRegionEndInclusive(i)

            block(PackedIntRange(i, end), cell)

            i = end + 1
        }
    }

    fun getRegionByCell(cell: Cell): PackedIntRange {
        val size = elements.size

        if (size == 0) {
            return PackedIntRange.Undefined
        }

        for (i in 0 until size) {
            if (elements[i].cell == cell) {
                val end = getRegionEndInclusive(i)

                return PackedIntRange(i, end)
            }
        }

        return PackedIntRange.Undefined
    }

    private fun getRegionEndInclusive(start: Int): Int {
        var index = start
        val size = elements.size

        val cell = elements[start].cell

        while(index < size) {
            if(elements[index].cell != cell) {
                return index - 1
            }

            index++
        }

        return size - 1
    }

    private fun findIndexForNewElement(newElement: CellDecor<*>): Int {
        val region = getRegionByCell(newElement.cell)

        return if(region.isUndefined) {
            elements.size
        } else {
            region.endInclusive + 1
        }
    }

    fun remove(value: CellDecor<*>): Boolean {
        val index = indexOf(value)
        return if (index >= 0) {
            remove(index)

            true
        } else {
            false
        }
    }

    fun remove(index: Int) {
        val size = elements.size

        if(size > 0) {
            val newElements = unsafeNewArray(size - 1)
            System.arraycopy(elements, 0, newElements, 0, index)
            System.arraycopy(elements, index + 1, newElements, index, size - index - 1)

            elements = newElements
        }
    }

    fun removeRange(start: Int, endInclusive: Int) {
        val rangeLength = endInclusive - start + 1

        val newElements = unsafeNewArray(elements.size - rangeLength)
        System.arraycopy(elements, 0, newElements, 0, start)
        System.arraycopy(elements, endInclusive + 1, newElements, start, size - endInclusive - 1)

        elements = newElements
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        private fun unsafeNewArray(size: Int): Array<CellDecor<*>> {
            return arrayOfNulls<CellDecor<*>>(size) as Array<CellDecor<*>>
        }
    }
}