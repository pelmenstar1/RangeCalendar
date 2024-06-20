package com.github.pelmenstar1.rangecalendar.complexRange.cell

internal class CellComplexRangeElementCollection(private val bits: Long): Collection<Int> {
    override val size: Int
        get() = bits.countOneBits()

    override fun isEmpty(): Boolean {
        return bits == 0L
    }

    override fun contains(element: Int): Boolean {
        if (element !in 0..<64) {
            return false
        }

        val mask = 1L shl element
        return bits and mask != 0L
    }

    override fun containsAll(elements: Collection<Int>): Boolean {
        return elements.all { it in this }
    }

    override fun iterator(): Iterator<Int> {
        return IteratorImpl(bits)
    }

    private class IteratorImpl(private var bits: Long): Iterator<Int> {
        override fun hasNext(): Boolean {
            return bits != 0L
        }

        override fun next(): Int {
            val b = bits

            val t = b and (-b)
            val index = 63 - t.countLeadingZeroBits()

            bits = b xor t

            return index
        }
    }
}