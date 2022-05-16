package io.github.pelmenstar1.rangecalendar.decoration

import io.github.pelmenstar1.rangecalendar.IntPair
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

    fun add(value: CellDecor<*>) {
        val index = findIndexForNewElement(value)

        val oldSize = elements.size

        allocatePlaceForInsert(index, 1)
        elements[index] = value
    }

    private fun allocatePlaceForInsert(pos: Int, length: Int) {
        val newElements = unsafeNewArray(elements.size + length)

        System.arraycopy(elements, 0, newElements, 0, pos)
        System.arraycopy(elements, pos, newElements, pos + length, elements.size - pos)

        elements = newElements
    }

    fun ensureCapacity(capacity: Int) {
        val size = elements.size

        if (capacity > size - 1) {
            val newElements = unsafeNewArray(capacity)
            System.arraycopy(elements, 0, newElements, 0, size)

            elements = newElements
        }
    }

    inline fun iterateRegions(block: (Region, Cell) -> Unit) {
        if (elements.isEmpty()) return

        var i = 0
        val size = elements.size

        while (i < size) {
            val cell = getNoCheck(i).cell

            val end = getRegionEnd(i)

            block(Region(i, end), cell)

            i = end
        }

       // if (prevStart != size - 1) {
            //block(Region(prevStart, size), currentRangeCell)
        //}
    }

    fun getRegionByCell(cell: Cell): Region {
        val size = elements.size

        if (size == 0) {
            return Region.Undefined
        }

        for (i in 0 until size) {
            if (elements[i].cell == cell) {
                val end = getRegionEnd(i)

                return Region(i, end)
            }
        }

        return Region.Undefined
    }

    private fun getRegionEnd(start: Int): Int {
        var index = start
        val size = elements.size

        val cell = elements[start].cell

        while(index < size) {
            if(elements[index].cell != cell) {
                return index
            }

            index++
        }

        return size
    }

    private fun findIndexForNewElement(newElement: CellDecor<*>): Int {
        val region = getRegionByCell(newElement.cell)

        return if(region.isUndefined) {
            elements.size
        } else {
            region.end - 1
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

        val numMoved = size - index - 1
        if (numMoved > 0) {
            System.arraycopy(elements, index + 1, elements, index, numMoved)
        }

        val newElements = unsafeNewArray(size - 1)
        System.arraycopy(elements, 0, newElements, 0, newElements.size)

        elements = newElements
    }

    @JvmInline
    value class Region(val packed: Long) {
        val start: Int
            get() = IntPair.getFirst(packed)

        val end: Int
            get() = IntPair.getSecond(packed)

        val isUndefined: Boolean
            get() = packed == 0L

        val isDefined: Boolean
            get() = packed != 0L

        companion object {
            val Undefined = Region(0)
        }
    }

    companion object {
        fun Region(start: Int, end: Int) = Region(IntPair.create(start, end))

        @Suppress("UNCHECKED_CAST")
        private fun unsafeNewArray(size: Int): Array<CellDecor<*>> {
            return arrayOfNulls<CellDecor<*>>(size) as Array<CellDecor<*>>
        }
    }
}