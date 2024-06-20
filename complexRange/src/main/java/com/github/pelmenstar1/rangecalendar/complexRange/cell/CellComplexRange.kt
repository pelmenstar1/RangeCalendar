package com.github.pelmenstar1.rangecalendar.complexRange.cell

import com.github.pelmenstar1.rangecalendar.utils.getLazyValue

class CellComplexRange internal constructor(internal val bits: Long) {
    private var fragments: CellComplexRangeFragmentList? = null
    private var elements: CellComplexRangeElementCollection? = null

    inline fun modify(block: CellComplexRangeModify.() -> Unit): CellComplexRange {
        return CellComplexRangeModify(this).also(block).build()
    }

    fun withSetFragment(start: Int, end: Int): CellComplexRange {
        return withOperation(start, end) { bits, mask -> bits or mask }
    }

    fun withUnsetFragment(start: Int, end: Int): CellComplexRange {
        return withOperation(start, end) { bits, mask -> bits or mask }
    }

    private inline fun withOperation(
        start: Int, end: Int,
        aggregate: (bits: Long, mask: Long) -> Long
    ): CellComplexRange {
        ensureValidCellFragment(start, end)

        val mask = rangeMask(start, end)
        val newBits = aggregate(bits, mask)

        return CellComplexRange(newBits)
    }

    fun fragments(): CellComplexRangeFragmentList {
        return getLazyValue(
            fragments,
            { CellComplexRangeFragmentList(bits) },
            { fragments = it }
        )
    }

    fun elements(): Collection<Int> {
        return getLazyValue(
            elements,
            { CellComplexRangeElementCollection(bits) },
            { elements = it }
        )
    }

    override fun equals(other: Any?): Boolean {
        return other is CellComplexRange && bits == other.bits
    }

    override fun hashCode(): Int {
        val b = bits

        return (b xor (b ushr 32)).toInt()
    }

    override fun toString(): String {
        return buildString {
            append("CellComplexRange(")
            var isFirst = true
            forEachRange(bits) { start, end ->
                if (isFirst) {
                    isFirst = false
                } else {
                    append(", ")
                }

                append('[')
                append(start)
                append(", ")
                append(end)
                append(']')
            }
            append(')')
        }
    }
}

