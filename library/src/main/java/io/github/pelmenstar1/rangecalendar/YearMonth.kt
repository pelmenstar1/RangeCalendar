@file:Suppress("NOTHING_TO_INLINE")

package io.github.pelmenstar1.rangecalendar

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