package com.github.pelmenstar1.rangecalendar.complexRange.date

interface DateComplexRangeBuilder {
    fun fragment(value: DateFragment)

    fun build(): DateComplexRange
}