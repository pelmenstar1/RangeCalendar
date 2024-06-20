package com.github.pelmenstar1.rangecalendar.complexRange.cell

inline fun CellComplexRange(block: CellComplexRangeBuilder.() -> Unit): CellComplexRange {
    return CellComplexRangeBuilder().also(block).build()
}

fun CellComplexRange(range: IntRange): CellComplexRange {
    return CellComplexRange(range.first, range.last)
}

fun CellComplexRange(fragment: CellFragment): CellComplexRange {
    return CellComplexRange(fragment.start, fragment.endInclusive)
}

fun CellComplexRange(start: Int, endInclusive: Int): CellComplexRange {
    ensureValidCellFragment(start, endInclusive)

    val mask = rangeMask(start, endInclusive)
    return CellComplexRange(mask)
}

fun CellComplexRange(fragments: Array<out CellFragment>): CellComplexRange {
    return createComplexRange(
        fragments,
        CellFragment::start, CellFragment::endInclusive,
        ensureValid = { _, _ -> }
    )
}

fun CellComplexRange(fragments: Array<out IntRange>): CellComplexRange {
    return createComplexRange(
        fragments,
        IntRange::first, IntRange::last,
        ::ensureValidCellFragment
    )
}

fun CellComplexRange(fragments: Iterable<CellFragment>): CellComplexRange {
    return createComplexRange(
        fragments,
        CellFragment::start, CellFragment::endInclusive,
        ensureValid = { _, _ -> }
    )
}

private inline fun<T> createComplexRange(
    values: Array<out T>,
    getStart: (T) -> Int, getEnd: (T) -> Int,
    ensureValid: (Int, Int) -> Unit
): CellComplexRange {
    var index = 0

    return createComplexRange(hasNext = { index < values.size }, next = { values[index++] }, getStart, getEnd, ensureValid)
}

private inline fun<T> createComplexRange(
    values: Iterable<T>,
    getStart: (T) -> Int, getEnd: (T) -> Int,
    ensureValid: (Int, Int) -> Unit
): CellComplexRange {
    val iter = values.iterator()

    return createComplexRange(iter::hasNext, iter::next, getStart, getEnd, ensureValid)
}

private inline fun<T> createComplexRange(
    hasNext: () -> Boolean, next: () -> T,
    getStart: (T) -> Int, getEnd: (T) -> Int,
    ensureValid: (Int, Int) -> Unit
): CellComplexRange {
    var mask = 0L

    while(hasNext()) {
        val range = next()
        val start = getStart(range)
        val endInclusive = getEnd(range)

        ensureValid(start, endInclusive)

        mask = mask or rangeMask(start, endInclusive)
    }

    return CellComplexRange(mask)
}