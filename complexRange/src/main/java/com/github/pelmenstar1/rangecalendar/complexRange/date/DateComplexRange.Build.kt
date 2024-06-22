package com.github.pelmenstar1.rangecalendar.complexRange.date

fun DateComplexRange(fragment: DateFragment): DateComplexRange {
    val list = RawLinkedList<DateFragment>().also {
        it.add(fragment)
    }

    return DateComplexRange(list)
}

fun DateComplexRange(vararg fragments: DateFragment): DateComplexRange {
    return DateComplexRange {
        fragments.forEach(::fragment)
    }
}

fun DateComplexRange(fragments: Iterable<DateFragment>): DateComplexRange {
    return DateComplexRange {
        fragments.forEach(::fragment)
    }
}

inline fun DateComplexRange(block: DateComplexRangeBuilder.() -> Unit): DateComplexRange {
    return newDateComplexRangeBuilder().also(block).build()
}