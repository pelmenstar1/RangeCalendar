package com.github.pelmenstar1.rangecalendar.complexRange.date

private fun<T> MutableList<T>.swap(i: Int, j: Int) {
    val t = this[i]
    this[i] = this[j]
    this[j] = t
}

// Heap's algorithm
fun<T> Collection<T>.allPermutations(): Sequence<Collection<T>> {
    val thisCol = this
    val elements = toMutableList()

    return sequence {
        val c = IntArray(elements.size)

        yield(thisCol)

        var i = 1
        while (i < elements.size) {
            if (c[i] < i) {
                if (i % 2 == 0) {
                    elements.swap(0, i)
                } else {
                    elements.swap(c[i], i)
                }

                yield(elements.toList())

                c[i]++
                i = 1
            } else {
                c[i] = 0
                i++
            }
        }
    }
}

inline fun<reified T> Array<out T>.allPermutations(): Sequence<Array<out T>> {
    return toMutableList().allPermutations().map { it.toTypedArray() }
}

fun IntRange.allSubRanges(): List<IntRange> {
    val rangeSize = endInclusive - start + 1
    val result = ArrayList<IntRange>()

    for (length in 1..rangeSize) {
        for(start in 0..(rangeSize - length)) {
            val subRange = start until (start + length)
            result.add(subRange)
        }
    }

    return result
}