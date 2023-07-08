package com.github.pelmenstar1.rangecalendar.utils

internal fun Long.iterateSetBits(block: (bitIndex: Int) -> Unit) {
    // Original source: https://lemire.me/blog/2018/02/21/iterating-over-set-bits-quickly/
    var bits = this

    while (bits != 0L) {
        val t = bits and (-bits)
        val index = 63 - t.countLeadingZeroBits()

        block(index)

        bits = bits xor t
    }
}