package com.github.pelmenstar1.rangecalendar.complexRange.cell

interface CellFragmentIterator {
    val current: CellFragment

    fun moveNext(): Boolean
    fun movePrevious(): Boolean

    fun pickNext(): CellFragment? {
        if (!moveNext()) {
            return null
        }

        val result = current
        movePrevious()

        return result
    }

    fun mark()
    fun subRange(): CellComplexRange
}