package com.github.pelmenstar1.rangecalendar.gesture

import com.github.pelmenstar1.rangecalendar.complexRange.cell.CellComplexRange

sealed interface GestureSelectionOperation {
    data class Select(val cellRange: CellComplexRange): GestureSelectionOperation
    data object SelectMonth: GestureSelectionOperation
    data class SelectToggle(val cellIndex: Int): GestureSelectionOperation
}