package com.github.pelmenstar1.rangecalendar.complexRange.cell

class CellFragment {
    val start: Int
    val endInclusive: Int

    val elementCount: Int
        get() = endInclusive - start + 1

    constructor(start: Int, endInclusive: Int) {
        ensureValidCellFragment(start, endInclusive)

        this.start = start
        this.endInclusive = endInclusive
    }

    constructor(range: IntRange) : this(range.first, range.last)

    fun overlapsWith(other: CellFragment): Boolean {
        return overlapsWith(other.start, other.endInclusive)
    }

    internal fun overlapsWith(otherStart: Int, otherEndInclusive: Int): Boolean {
        return start <= otherEndInclusive && otherStart <= endInclusive
    }

    fun getDistanceTo(other: CellFragment): Int {
        return if (overlapsWith(other)) {
            0
        } else {
           val thisStart = start
           val otherStart = other.start

           if (thisStart >= otherStart) {
               thisStart - other.endInclusive
           } else {
               otherStart - endInclusive
           }
        }
    }

    operator fun contains(value: Int): Boolean {
        return value in start..endInclusive
    }

    override fun equals(other: Any?): Boolean {
        return other is CellFragment && start == other.start && endInclusive == other.endInclusive
    }

    override fun hashCode(): Int {
        return 31 * start + endInclusive
    }

    override fun toString(): String {
        return "[$start, $endInclusive]"
    }
}