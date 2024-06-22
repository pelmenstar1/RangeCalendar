package com.github.pelmenstar1.rangecalendar.complexRange.date

import com.github.pelmenstar1.rangecalendar.PackedDate
import java.time.LocalDate
import java.util.Calendar

class DateFragment {
    val start: PackedDate
    val endInclusive: PackedDate

    internal val endExclusive: PackedDate
        get() = endInclusive.plusDays(1)

    val startEpochDays: Long
        get() = start.toEpochDay()

    val endEpochDays: Long
        get() = endInclusive.toEpochDay()

    val startDate: LocalDate
        get() = start.toLocalDate()

    val endDate: LocalDate
        get() = endInclusive.toLocalDate()

    internal constructor(startDate: PackedDate, endDate: PackedDate) {
        start = startDate
        endInclusive = endDate
    }

    constructor(startEpochDays: Long, endEpochDays: Long) {
        ensureValidEpochDays(startEpochDays, "startEpochDays")
        ensureValidEpochDays(endEpochDays, "endEpochDays")

        if (startEpochDays > endEpochDays) {
            throw IllegalArgumentException("Start epoch days is greater than end epoch days")
        }

        start = PackedDate.fromEpochDay(startEpochDays)
        endInclusive = PackedDate.fromEpochDay(endEpochDays)
    }

    constructor(startDate: LocalDate, endDate: LocalDate) {
        if (startDate.isAfter(endDate)) {
            throw IllegalArgumentException("Start date is after end date")
        }

        start = PackedDate.fromLocalDate(startDate)
        endInclusive = PackedDate.fromLocalDate(endDate)
    }

    constructor(startCalendar: Calendar, endCalendar: Calendar) {
        val startDate = PackedDate.fromCalendar(startCalendar)
        val endDate = PackedDate.fromCalendar(endCalendar)

        if (startDate > endDate) {
            throw IllegalArgumentException("Start date is after end date")
        }

        start = startDate
        endInclusive = endDate
    }

    private fun ensureValidEpochDays(value: Long, paramName: String) {
        if(value !in PackedDate.MIN_DATE_EPOCH..PackedDate.MAX_DATE_EPOCH) {
            throw IllegalArgumentException("$paramName is out of bounds")
        }
    }

    internal fun withStart(value: PackedDate): DateFragment {
        return if (value == start) this else DateFragment(value, endInclusive)
    }

    internal fun withEnd(value: PackedDate): DateFragment {
        return if (value == endInclusive) this else DateFragment(start, value)
    }

    internal fun withEndExclusive(value: PackedDate): DateFragment {
        return withEnd(value.plusDays(-1))
    }

    operator fun contains(value: Long): Boolean {
        return value in start.toEpochDay()..endInclusive.toEpochDay()
    }

    operator fun contains(value: LocalDate): Boolean {
        return contains(PackedDate.fromLocalDate(value))
    }

    internal operator fun contains(value: PackedDate): Boolean {
        return value >= start && value <= endInclusive
    }

    fun containsExclusive(other: DateFragment): Boolean {
        return other.start > start && other.endInclusive < endInclusive
    }

    fun containsCompletely(other: DateFragment) =
        containsCompletely(other.start, other.endInclusive)

    internal fun containsCompletely(otherStart: PackedDate, otherEndInclusive: PackedDate): Boolean {
        return otherStart >= start && otherEndInclusive <= endInclusive
    }

    fun leftContains(other: DateFragment): Boolean {
        return other.endInclusive in this && other.start <= start
    }

    fun overlapsWith(other: DateFragment): Boolean {
        return overlapsWith(other.start, other.endInclusive)
    }

    internal fun overlapsWith(otherStart: PackedDate, otherEndInclusive: PackedDate): Boolean {
        return start <= otherEndInclusive && otherStart <= endInclusive
    }

    fun canUniteWith(other: DateFragment): Boolean {
        return canUniteWith(other.start, other.endInclusive)
    }

    internal fun canUniteWith(otherStart: PackedDate, otherEndInclusive: PackedDate): Boolean {
        return overlapsWith(otherStart, otherEndInclusive) ||
                isAdjacentTo(otherStart, otherEndInclusive)
    }

    fun isAdjacentTo(other: DateFragment) = isAdjacentTo(other.start, other.endInclusive)

    internal fun isAdjacentTo(otherStart: PackedDate, otherEndInclusive: PackedDate): Boolean {
        return isAdjacentLeft(otherStart) || isAdjacentRight(otherEndInclusive)
    }

    internal fun isAdjacentLeft(otherStart: PackedDate): Boolean {
        return endInclusive.hasTomorrow() && endInclusive.plusDays(1) == otherStart
    }

    internal fun isAdjacentRight(otherEndInclusive: PackedDate): Boolean {
        return start.hasYesterday() && start.plusDays(-1) == otherEndInclusive
    }

    fun isBefore(other: DateFragment): Boolean {
        return start <= other.start
    }

    fun isAfter(other: DateFragment): Boolean {
        return endInclusive >= other.endInclusive
    }

    override fun equals(other: Any?): Boolean {
        return other is DateFragment &&
                start == other.start &&
                endInclusive == other.endInclusive
    }

    override fun hashCode(): Int {
        return start.bits * 31 + endInclusive.bits
    }

    override fun toString(): String {
        return buildString {
            append("[")
            append(start.toIsoString())
            append(", ")
            append(endInclusive.toIsoString())
            append(']')
        }
    }
}