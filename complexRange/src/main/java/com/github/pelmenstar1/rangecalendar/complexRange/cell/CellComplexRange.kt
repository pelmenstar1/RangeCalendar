package com.github.pelmenstar1.rangecalendar.complexRange.cell

import com.github.pelmenstar1.rangecalendar.GridConstants
import com.github.pelmenstar1.rangecalendar.selection.CellRange
import com.github.pelmenstar1.rangecalendar.utils.getLazyValue

class CellComplexRange internal constructor(internal val bits: Long) {
    val isEmpty: Boolean
        get() = bits == 0L

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

    operator fun contains(value: Int): Boolean {
        return value in 0..<GridConstants.CELL_COUNT && (bits and (1L shl value)) != 0L
    }

    fun isSingleCell(): Boolean {
        val b = bits

        // Check if there's only one set bit
        return b and (b - 1) == 0L
    }

    fun isSingleCell(cellIndex: Int): Boolean {
        if (cellIndex !in 0..GridConstants.CELL_COUNT) {
            return false
        }

        val b = bits

        // Check if there's only one bit set and that bit is equal to cellIndex's bit
        return b and (b - 1) == 0L && b and (1L shl cellIndex) != 0L
    }

    fun hasIntersectionWith(other: CellComplexRange): Boolean {
        return bits and other.bits != 0L
    }

    fun clamp(limitStart: Int, limitEndInclusive: Int): CellComplexRange {
        ensureValidCellFragment(limitStart, limitEndInclusive)

        val mask = rangeMask(limitStart, limitEndInclusive)
        return CellComplexRange(bits and mask)
    }

    infix fun xor(other: CellComplexRange): CellComplexRange {
        return CellComplexRange(bits xor other.bits)
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

    companion object {
        val Empty = CellComplexRange(0L)

        fun singleCell(cellIndex: Int): CellComplexRange {
            ensureValidCell(cellIndex)

            return CellComplexRange(1L shl cellIndex)
        }
    }
}

