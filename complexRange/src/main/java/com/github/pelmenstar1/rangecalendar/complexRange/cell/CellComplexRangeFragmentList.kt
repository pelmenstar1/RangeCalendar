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

    internal fun getLastFragmentEndInclusive(): Int {
        if (bits != 0L) {
            return 63 - bits.countLeadingZeroBits()
        }

        return -1
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

        return -1
    }

    override fun lastIndexOf(element: CellFragment): Int {
        // Fragments in the list do not repeat. So lastIndexOf() is equal to indexOf()
        return indexOf(element)
    }

    override fun contains(element: CellFragment): Boolean {
        val (start, endInclusive) = element
        val mask = rangeMask(start, endInclusive)
        var wideMask = mask
        wideMask = wideMask or (1L shl maxOf(0, start - 1))
        wideMask = wideMask or (1L shl (endInclusive + 1))

        return bits and wideMask == mask
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

    fun fragmentIterator(): CellFragmentIterator {
        return FragmentIteratorImpl(bits)
    }

    private class FragmentIteratorImpl(private val bits: Long) : CellFragmentIterator {
        private var _current: CellFragment? = null

        private var lastFragmentStart = -1
        private var lastFragmentEnd = -1

        private var markedBitIndex = -1

        override val current: CellFragment
            get() = _current ?: throw NoSuchElementException()

        override fun moveNext(): Boolean {
            val setBitIndex = findNextSetBitIndex(bits, lastFragmentEnd + 1)
            if (setBitIndex < 0) {
                return false
            }

            var unsetBitIndex = findNextUnsetBitIndex(bits, setBitIndex)
            if (unsetBitIndex < 0) {
                unsetBitIndex = 64
            }

            setFragment(setBitIndex, unsetBitIndex - 1)

            return true
        }

        override fun movePrevious(): Boolean {
            val setBitIndex = findPreviousSetBitIndex(bits, lastFragmentStart - 1)
            if (setBitIndex < 0) {
                return false
            }

            val unsetBitIndex = findPreviousUnsetBitIndex(bits, setBitIndex)

            setFragment(unsetBitIndex + 1, setBitIndex)

            return true
        }

        private fun setFragment(start: Int, end: Int) {
            lastFragmentStart = start
            lastFragmentEnd = end

            _current = CellFragment(start, end)
        }

        override fun mark() {
            markedBitIndex = lastFragmentStart
        }

        override fun subRange(): CellComplexRange {
            val markedIndex = markedBitIndex
            if (markedIndex < 0) {
                throw IllegalStateException("No marked element")
            }

            val mask = rangeMask(markedIndex, lastFragmentEnd)
            val newBits = bits and mask

            return CellComplexRange(newBits)
        }
    }

    private class ListIteratorImpl(private val bits: Long) : ListIterator<CellFragment> {
        private var currentFragmentIndex = -1
        private var currentBitIndex = -1

        override fun hasNext(): Boolean {
            return bits and startMask(currentBitIndex + 1) != 0L
        }

        override fun hasPrevious(): Boolean {
            return currentFragmentIndex >= 0
        }

        private inline fun hasInternal(index: Int, mapWord: (Long) -> Long): Boolean {
            return (mapWord(bits) and startMask(index)) != 0L
        }

        override fun next(): CellFragment {
            val setBitIndex = findNextSetBitIndex(bits, currentBitIndex + 1)
            if (setBitIndex < 0) {
                throw NoSuchElementException()
            }

            val unsetBitIndex = findNextUnsetBitIndex(bits, setBitIndex)

            currentFragmentIndex++
            currentBitIndex = unsetBitIndex

            return CellFragment(setBitIndex, unsetBitIndex - 1)
        }

        override fun previous(): CellFragment {
            val setBitIndex = findPreviousSetBitIndex(bits, currentBitIndex)
            if (setBitIndex < 0) {
                throw NoSuchElementException()
            }

            val unsetBitIndex = findPreviousUnsetBitIndex(bits, setBitIndex)

            currentFragmentIndex--
            currentBitIndex = unsetBitIndex

            return CellFragment(unsetBitIndex + 1, setBitIndex)
        }

        override fun nextIndex(): Int = currentFragmentIndex + 1
        override fun previousIndex(): Int = currentFragmentIndex
    }
}