package com.github.pelmenstar1.rangecalendar.complexRange.date

interface DateComplexRange {
    fun modify(block: DateComplexRangeModify.() -> Unit): DateComplexRange
    fun fragments(): List<DateFragment>

    companion object {
        fun empty(): DateComplexRange {
            return EmptyDateComplexRange
        }
    }
}