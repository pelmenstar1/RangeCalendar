package com.github.pelmenstar1.rangecalendar.complexRange.cell

class CellComplexRangeModify(currentRange: CellComplexRange) {
    private var bits = currentRange.bits

    fun set(start: Int, end: Int) {
        operation(start, end) { bits, mask -> bits or mask }
    }

    fun set(range: IntRange) = set(range.first, range.last)
    fun set(fragment: CellFragment) = set(fragment.start, fragment.endInclusive)

    fun unset(start: Int, end: Int) {
        operation(start, end) { bits, mask -> bits and mask.inv() }
    }

    fun unset(range: IntRange) = unset(range.first, range.last)
    fun unset(fragment: CellFragment) = unset(fragment.start, fragment.endInclusive)

    private inline fun operation(
        start: Int, end: Int,
        aggregateMask: (bits: Long, mask: Long) -> Long
    ) {
        ensureValidCellFragment(start, end)

        val mask = rangeMask(start, end)
        bits = aggregateMask(bits, mask)
    }

    fun build(): CellComplexRange {
        return CellComplexRange(bits)
    }
}