package com.github.pelmenstar1.rangecalendar.complexRange.cell

class CellComplexRangeBuilder {
    private var bits = 0L

    fun fragment(start: Int, end: Int) {
        ensureValidCellFragment(start, end)

        bits = bits or rangeMask(start, end)
    }

    fun fragment(range: IntRange) = fragment(range.first, range.last)
    fun fragment(value: CellFragment) = fragment(value.start, value.endInclusive)

    fun build(): CellComplexRange {
        return CellComplexRange(bits)
    }
}