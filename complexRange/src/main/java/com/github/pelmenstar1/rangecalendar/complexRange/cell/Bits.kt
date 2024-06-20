package com.github.pelmenstar1.rangecalendar.complexRange.cell

private const val WORD_MASK = -1L

internal fun startMask(startIndex: Int) = WORD_MASK shl startIndex
internal fun endMask(endIndex: Int) = WORD_MASK ushr (-endIndex - 1)

internal fun rangeMask(startIndex: Int, endIndex: Int): Long {
    return startMask(startIndex) and endMask(endIndex)
}

internal fun findNextSetBitIndex(bits: Long, startIndex: Int): Int {
    return findNextBitBase(bits, startIndex, mapWord = { it })
}

internal fun findNextUnsetBitIndex(bits: Long, startIndex: Int): Int {
    return findNextBitBase(bits, startIndex, mapWord = Long::inv)
}

private inline fun findNextBitBase(bits: Long, startIndex: Int, mapWord: (Long) -> Long): Int {
    if (startIndex < 64) {
        val word = mapWord(bits) and startMask(startIndex)
        if (word != 0L) {
            return word.countTrailingZeroBits()
        }
    }

    return -1
}

internal fun findPreviousSetBitIndex(bits: Long, startIndex: Int): Int {
    return findPreviousBitBase(bits, startIndex, mapWord = { it })
}

internal fun findPreviousUnsetBitIndex(bits: Long, startIndex: Int): Int {
    return findPreviousBitBase(bits, startIndex, mapWord = Long::inv)
}

private inline fun findPreviousBitBase(bits: Long, startIndex: Int, mapWord: (Long) -> Long): Int {
    if (startIndex > 0) {
        val word = mapWord(bits) and endMask(startIndex)

        if (word != 0L) {
            return 63 - word.countLeadingZeroBits()
        }
    }

    return -1
}

internal inline fun forEachRange(bits: Long, block: (start: Int, endInclusive: Int) -> Unit) {
    var start = 0

    while(true) {
        val rangeStart = findNextSetBitIndex(bits, start)
        if (rangeStart < 0) {
            break
        }

        var unsetBitIndex = findNextUnsetBitIndex(bits, rangeStart)
        if (unsetBitIndex < 0) {
            unsetBitIndex = 64
        }

        start = unsetBitIndex
        block(rangeStart, unsetBitIndex - 1)
    }
}

internal inline fun forEachRangeReversed(bits: Long, block: (start: Int, endInclusive: Int) -> Unit) {
    var start = 63

    while (true) {
        val setBitIndex = findPreviousSetBitIndex(bits, start)
        if (setBitIndex < 0) {
            break
        }

        val unsetBitIndex = findPreviousUnsetBitIndex(bits, setBitIndex)

        block(unsetBitIndex + 1, setBitIndex)
        start = unsetBitIndex - 1
    }
}