package com.github.pelmenstar1.rangecalendar.complexRange.cell

class CellFragment(
    val start: Int,
    val endInclusive: Int
) {
    init {
        ensureValidCellFragment(start, endInclusive)
    }

    operator fun contains(value: Int): Boolean {
        return value in start..endInclusive
    }
}