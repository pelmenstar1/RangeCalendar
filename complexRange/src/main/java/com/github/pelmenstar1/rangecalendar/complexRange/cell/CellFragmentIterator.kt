package com.github.pelmenstar1.rangecalendar.complexRange.cell

interface CellFragmentIterator {
    val current: CellFragment

    fun moveNext(): Boolean
    fun movePrevious(): Boolean

    fun mark()
    fun subRange(): CellComplexRange
}