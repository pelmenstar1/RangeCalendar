package com.github.pelmenstar1.rangecalendar.complexRange.cell

import com.github.pelmenstar1.rangecalendar.selection.CellRange

class CellComplexRangeFragmentList(val bits: Long): List<CellFragment> {
    private var _size: Int = -1

    override val size: Int
        get() {
            var result = _size
            if (result < 0) {
                result = computeSize()
                _size = result
            }
            return result
        }

    private fun computeSize(): Int {
        var result = 0
        forEachRange(bits) { _, _ ->
            result++
        }
        return result
    }

    override fun get(index: Int): CellFragment {
        val range = getCellRange(index)

        return CellFragment(range.start.index, range.end.index)
    }

    private fun getCellRange(index: Int): CellRange {
        val s = _size
        if (index >= 0 && (s < 0 || index < s)) {
            var rem = index
            forEachRange(bits) { start, end ->
                if (rem == 0) {
                    return CellRange(start, end)
                }

                rem--
            }
        }

        throw IndexOutOfBoundsException("index")
    }

    override fun isEmpty(): Boolean {
        return bits == 0L
    }

    override fun indexOf(element: CellFragment): Int {
        var index = 0
        forEachRange(bits) { start, end ->
            if (element.start == start && element.endInclusive == end) {
                return index
            }
            index++
        }

        return index
    }

    override fun lastIndexOf(element: CellFragment): Int {
        var index = 0
        forEachRangeReversed(bits) { start, end ->
            if (element.start == start && element.endInclusive == end) {
                return size - index - 1
            }
            index++
        }

        return index
    }

    override fun contains(element: CellFragment): Boolean {
        val mask = rangeMask(element.start, element.endInclusive)

        return bits and mask == mask
    }

    override fun containsAll(elements: Collection<CellFragment>): Boolean {
        return elements.all { it in elements }
    }

    override fun subList(fromIndex: Int, toIndex: Int): List<CellFragment> {
        if (fromIndex >= toIndex) {
            throw IndexOutOfBoundsException()
        }

        val startRange = getCellRange(fromIndex)
        val endRange = getCellRange(toIndex - 1)

        val mask = rangeMask(startRange.start.index, endRange.end.index)
        val newBits = bits and mask

        return CellComplexRangeFragmentList(newBits)
    }

    override fun iterator(): Iterator<CellFragment> {
        return listIterator()
    }

    override fun listIterator(): ListIterator<CellFragment> {
        return ListIteratorImpl(bits)
    }

    override fun listIterator(index: Int): ListIterator<CellFragment> {
        val range = getCellRange(index)
        val maskStart = maxOf(0, range.start.index - 1)

        val iterBits = bits and startMask(maskStart).inv()

        return ListIteratorImpl(iterBits)
    }

    private class ListIteratorImpl(private val bits: Long) : ListIterator<CellFragment> {
        private var currentFragmentIndex = -1
        private var lastFragmentStart = -1
        private var lastFragmentEnd = -1

        override fun hasNext(): Boolean {
            return findNextSetBitIndex(bits, lastFragmentEnd) >= 0
        }

        override fun hasPrevious(): Boolean {
            return findPreviousSetBitIndex(bits, lastFragmentStart) >= 0
        }

        override fun next(): CellFragment {
            val setBitIndex = findNextSetBitIndex(bits, lastFragmentEnd)
            if (setBitIndex < 0) {
                throw NoSuchElementException()
            }

            val unsetBitIndex = findNextUnsetBitIndex(bits, setBitIndex)

            return CellFragment(setBitIndex, unsetBitIndex - 1)
        }

        override fun previous(): CellFragment {
            val setBitIndex = findPreviousSetBitIndex(bits, lastFragmentStart)
            if (setBitIndex < 0) {
                throw NoSuchElementException()
            }

            val unsetBitIndex = findPreviousUnsetBitIndex(bits, setBitIndex)

            return CellFragment(unsetBitIndex + 1, setBitIndex)
        }

        override fun nextIndex(): Int = currentFragmentIndex + 1
        override fun previousIndex(): Int = currentFragmentIndex
    }
}