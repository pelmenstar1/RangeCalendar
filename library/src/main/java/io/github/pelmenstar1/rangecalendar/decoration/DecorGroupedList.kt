package io.github.pelmenstar1.rangecalendar.decoration

import io.github.pelmenstar1.rangecalendar.PackedIntRange
import io.github.pelmenstar1.rangecalendar.YearMonth
import io.github.pelmenstar1.rangecalendar.selection.Cell

/**
 * Represents a special list in which items are grouped (not sorted) by year-month and cell.
 *
 * Some terms:
 * - Region is range of items with the same year-month
 * - Subregion is subrange within some region of items with the same cell.
 */
@Suppress("LeakingThis", "UNCHECKED_CAST")
internal class DecorGroupedList {
    internal var elements: Array<CellDecor>

    val size: Int
        get() = elements.size

    constructor() {
        elements = EMPTY_ARRAY
    }

    constructor(capacity: Int) {
        elements = unsafeNewArray(capacity)
    }

    operator fun get(index: Int): CellDecor {
        return elements[index]
    }

    fun indexOf(value: CellDecor): Int {
        return elements.indexOf(value)
    }

    inline fun forEach(block: (CellDecor) -> Unit) {
        elements.forEach(block)
    }

    inline fun forEachBreakable(block: (CellDecor) -> Boolean) {
        for (element in elements) {
            val toBreak = block(element)

            if (toBreak) {
                break
            }
        }
    }

    fun add(value: CellDecor): PackedIntRange {
        val index = findIndexForNewElement(value)

        allocatePlaceForInsert(index, 1)
        elements[index] = value

        return PackedIntRange(index, index)
    }

    fun addAll(values: Array<out CellDecor>): PackedIntRange {
        val index = findIndexForNewElement(values[0])

        allocatePlaceForInsert(index, values.size)

        System.arraycopy(values, 0, elements, index, values.size)

        return PackedIntRange(index, index + values.size - 1)
    }

    fun insertAll(indexInRegion: Int, values: Array<out CellDecor>): PackedIntRange {
        val instance = values[0]
        val region = getRegion(YearMonth.forDate(instance.date))

        val resolvedIndex = if (region.isDefined) {
            val subregion = getSubregion(region, instance.cell)

            if(subregion.isDefined) {
                val length = subregion.endInclusive - subregion.start + 1

                subregion.start + indexInRegion.coerceIn(0, length)
            } else {
                subregion.endInclusive + 1
            }
        } else {
            elements.size
        }

        allocatePlaceForInsert(resolvedIndex, values.size)

        System.arraycopy(values, 0, elements, resolvedIndex, values.size)

        return PackedIntRange(resolvedIndex, resolvedIndex + values.size - 1)
    }

    /**
     * Allocates place for inserting elements at specified position. Length is count of items that should be inserted.
     */
    private fun allocatePlaceForInsert(pos: Int, length: Int) {
        val newElements = unsafeNewArray(size + length)

        System.arraycopy(elements, 0, newElements, 0, pos)
        System.arraycopy(elements, pos, newElements, pos + length, size - pos)

        elements = newElements
    }

    fun getRegion(ym: YearMonth): PackedIntRange {
        return getRegionInternal(
            getStart = { getRegionStart(ym) },
            getEnd = this::getRegionEndInclusive
        )
    }

    fun getRegionStart(ym: YearMonth): Int {
        return getRegionStartInternal(0, size - 1) { YearMonth.forDate(it.date) == ym }
    }

    private fun getRegionEndInclusive(start: Int): Int {
        val ym = YearMonth.forDate(elements[start].date)

        return getRegionEndInclusiveInternal(start, size - 1) { YearMonth.forDate(it.date) == ym }
    }

    fun getSubregion(region: PackedIntRange, cell: Cell): PackedIntRange {
        if(region.isUndefined) {
            return PackedIntRange.Undefined
        }

        return getRegionInternal(
            getStart = { getSubregionStart(region, cell) },
            getEnd = { start -> getSubregionEndInclusive(region, start) }
        )
    }

    fun getSubregion(ym: YearMonth, cell: Cell): PackedIntRange {
        val region = getRegion(ym)
        if(region.isUndefined) {
            return PackedIntRange.Undefined
        }

        return getSubregion(region, cell)
    }

    fun getSubregionStart(region: PackedIntRange, cell: Cell): Int {
        return getRegionStartInternal(region.start, region.endInclusive) { it.cell == cell }
    }

    fun getSubregionEndInclusive(region: PackedIntRange, start: Int): Int {
        val cell = elements[start].cell

        return getRegionEndInclusiveInternal(start, region.endInclusive) { it.cell == cell }
    }

    private inline fun getRegionInternal(
        getStart: () -> Int,
        getEnd: (start: Int) -> Int
    ): PackedIntRange {
        val start = getStart()
        if (start == -1) {
            return PackedIntRange.Undefined
        }

        val endInclusive = getEnd(start)

        return PackedIntRange(start, endInclusive)
    }

    private inline fun getRegionStartInternal(
        start: Int, endInclusive: Int,
        equalsFunc: (CellDecor) -> Boolean
    ): Int {
        val elements = elements

        for (i in start..endInclusive) {
            if (equalsFunc(elements[i])) {
                return i
            }
        }

        return -1
    }

    private inline fun getRegionEndInclusiveInternal(
        start: Int, endInclusive: Int,
        equalsFunc: (CellDecor) -> Boolean
    ): Int {
        for (i in start..endInclusive) {
            if (!equalsFunc(elements[i])) {
                return i - 1
            }
        }

        return size - 1
    }

    inline fun iterateSubregions(region: PackedIntRange, block: (subregion: PackedIntRange) -> Unit) {
        if(size == 0 || region.isUndefined) {
            return
        }

        var startIndex = 0

        while(startIndex < size) {
            val endIndex = getSubregionEndInclusive(region, startIndex)

            // If there's no end of region found, then it's the last region and end of region is the last item.
            val resolvedEndIndex = if(endIndex == -1) size - 1 else endIndex

            block(PackedIntRange(startIndex, resolvedEndIndex))

            startIndex = endIndex + 1
        }
    }

    private fun findIndexForNewElement(newElement: CellDecor): Int {
        val region = getRegion(YearMonth.forDate(newElement.date))
        if (region.isUndefined) {
            return elements.size
        }

        val subregion = getSubregion(region, newElement.cell)
        return if (subregion.isUndefined) {
            region.endInclusive + 1
        } else {
            subregion.endInclusive + 1
        }
    }

    fun remove(index: Int) {
        if(size > 0) {
            val newElements = unsafeNewArray(size - 1)

            System.arraycopy(elements, 0, newElements, 0, index)
            System.arraycopy(elements, index + 1, newElements, index, size - index - 1)

            elements = newElements
        }
    }

    fun removeRange(range: PackedIntRange) {
        val start = range.start
        val endInclusive = range.endInclusive

        val rangeLength = endInclusive - start + 1

        val newElements = unsafeNewArray(elements.size - rangeLength)
        System.arraycopy(elements, 0, newElements, 0, start)
        System.arraycopy(elements, endInclusive + 1, newElements, start, size - endInclusive - 1)

        elements = newElements
    }

    companion object {
        private val EMPTY_ARRAY = emptyArray<CellDecor>()

        /**
         * Returns array of not-null [CellDecor]'s in signature.
         * Nevertheless it's filled with null inside.
         * The array is returned with assumption it will be filled with actually not-null elements later.
         * It's unsafe because it breaks Kotlin in some way.
         */
        private fun unsafeNewArray(size: Int): Array<CellDecor> {
            return arrayOfNulls<CellDecor>(size) as Array<CellDecor>
        }
    }
}