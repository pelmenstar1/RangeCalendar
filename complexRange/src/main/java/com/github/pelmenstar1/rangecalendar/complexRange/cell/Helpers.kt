package com.github.pelmenstar1.rangecalendar.complexRange.cell

import com.github.pelmenstar1.rangecalendar.GridConstants

internal fun ensureValidCellFragment(start: Int, end: Int) {
    if (!(start in 0..end && end < GridConstants.CELL_COUNT)) {
        throw IllegalArgumentException("Invalid given fragment")
    }
}

internal fun ensureValidCell(value: Int) {
    if (value !in 0..<GridConstants.CELL_COUNT) {
        throw IllegalArgumentException("Invalid cell index")
    }
}