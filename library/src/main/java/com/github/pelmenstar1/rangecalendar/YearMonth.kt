@file:Suppress("NOTHING_TO_INLINE")

package com.github.pelmenstar1.rangecalendar

import kotlin.math.max
import kotlin.math.min

internal fun YearMonth(year: Int, month: Int): YearMonth {
    return YearMonth(year * 12 + (month - 1))
}

@JvmInline
internal value class YearMonth(val totalMonths: Int) {
    val year: Int
        get() = totalMonths / 12

    val month: Int
        get() = totalMonths % 12 + 1

    inline operator fun component1() = year
    inline operator fun component2() = month

    operator fun compareTo(other: YearMonth) = totalMonths - other.totalMonths

    operator fun minus(other: YearMonth): YearMonth {
        return YearMonth(totalMonths - other.totalMonths)
    }

    operator fun plus(months: Int): YearMonth {
        return YearMonth(totalMonths + months)
    }

    operator fun minus(months: Int): YearMonth {
        return YearMonth(totalMonths - months)
    }

    companion object {
        fun forDate(date: PackedDate) = YearMonth(date.year, date.month)
    }
}

internal fun YearMonthRange(start: YearMonth, end: YearMonth): YearMonthRange {
    return YearMonthRange(packInts(start.totalMonths, end.totalMonths))
}

@JvmInline
internal value class YearMonthRange(private val bits: Long) {
    private val rawStart: Int
        get() = unpackFirstInt(bits)

    private val rawEnd: Int
        get() = unpackSecondInt(bits)

    val start: YearMonth
        get() = YearMonth(rawStart)

    val end: YearMonth
        get() = YearMonth(rawEnd)

    val isValid: Boolean
        get() = rawStart <= rawEnd

    operator fun component1() = start
    operator fun component2() = end

    fun intersectionWith(other: YearMonthRange): YearMonthRange {
        val start = rawStart
        val end = rawEnd
        val otherStart = other.rawStart
        val otherEnd = other.rawEnd

        if (otherStart > end || start > otherEnd) {
            return Invalid
        }

        return YearMonthRange(YearMonth(max(start, otherStart)), YearMonth(min(end, otherEnd)))
    }

    override fun toString(): String {
        return "YearMonthRange(start=$start, end=$end)"
    }

    companion object {
        // start=1, end=0 -> range is invalid
        val Invalid = YearMonthRange(0x00000000_00000001)
    }
}

internal inline fun iterateYearMonth(start: YearMonth, end: YearMonth, block: (YearMonth) -> Unit) {
    for (totalMonths in start.totalMonths..end.totalMonths) {
        block(YearMonth(totalMonths))
    }
}