package com.github.pelmenstar1.rangecalendar.complexRange.date

import com.github.pelmenstar1.rangecalendar.PackedDate

class DateComplexRange internal constructor(
    private val fragments: RawLinkedList<DateFragment>
) {
    val isEmpty: Boolean
        get() = fragments.isEmpty()

    val firstFragment: DateFragment
        get() = fragments.firstValue

    val lastFragment: DateFragment
        get() = fragments.lastValue

    fun modify(block: DateComplexRangeModify.() -> Unit): DateComplexRange {
        val fragmentsCopy = fragments.copyOf()
        LinkedListDateComplexRangeModify(fragmentsCopy).also(block)

        return DateComplexRange(fragmentsCopy)
    }

    fun clamp(startDate: PackedDate, endDate: PackedDate): DateComplexRange {
        val head = fragments.head
        val tail = fragments.tail

        // Check both head and tail for being null to make Kotlin compiler happy about them not being null.
        // If fragments are empty, then there's nothing to clamp.
        if (head == null || tail == null) {
            return this
        }

        val firstFragment = head.value
        val lastFragment = tail.value

        val currentLimitStart = firstFragment.start
        val currentLimitEnd = lastFragment.endInclusive

        if (currentLimitStart >= startDate && currentLimitEnd <= endDate) {
            // The complex range is already inside the given date range.
            return this
        }

        if (!(currentLimitStart <= endDate && startDate <= currentLimitEnd)) {
            // The limiting range of the current complex range and date range [startDate; endDate] don't intersect.
            // The result is an empty one.
            return EMPTY
        }

        // Both cannot be null, because
        val startNode = findFirstFragmentNodeAfter(startDate)!!
        val endNode = findLastFragmentBefore(endDate)!!

        val startFragment = startNode.value
        val endFragment = endNode.value

        val resultList = fragments.copyOf(startNode, endNode)
        val resultHead = resultList.head!!
        val resultTail = resultList.tail!!

        if (resultHead === resultTail) {
            if (startDate > startFragment.start || endDate < startFragment.endInclusive) {
                resultHead.value = DateFragment(startDate, endDate)
            }
        } else {
            if (startDate > startFragment.start) {
                resultHead.value = startFragment.withStart(startDate)
            }

            if (endDate < endFragment.endInclusive) {
                resultTail.value = endFragment.withEnd(endDate)
            }
        }

        return DateComplexRange(resultList)
    }

    private fun findFirstFragmentNodeAfter(date: PackedDate): RawLinkedList.Node<DateFragment>? {
        return fragments.findFirstNode { date <= it.endInclusive }
    }

    private fun findLastFragmentBefore(date: PackedDate): RawLinkedList.Node<DateFragment>? {
        return fragments.findLastNode { date >= it.start }
    }

    fun fragments(): List<DateFragment> {
        return fragments
    }

    override fun equals(other: Any?): Boolean {
        return other is DateComplexRange && fragments == other.fragments
    }

    override fun hashCode(): Int {
        return fragments.hashCode()
    }

    override fun toString(): String {
        return buildString {
            append("DateComplexRange(")
            var isFirst = true
            fragments.forEachNode {
                if (isFirst) {
                    isFirst = false
                } else {
                    append(", ")
                }

                append(it.value)
            }
            append(')')
        }
    }

    companion object {
        private val EMPTY = DateComplexRange(RawLinkedList())

        fun empty(): DateComplexRange = EMPTY
    }
}

fun newDateComplexRangeBuilder(): DateComplexRangeBuilder {
    return LinkedListDateComplexRangeBuilder()
}

internal class LinkedListDateComplexRangeBuilder : LinkedListDateComplexRangeBaseBuilder(), DateComplexRangeBuilder {
    override fun fragment(value: DateFragment) {
        includeFragment(value)
    }

    override fun build(): DateComplexRange {
        return DateComplexRange(fragments)
    }
}

internal class LinkedListDateComplexRangeModify(
    fragments: RawLinkedList<DateFragment>
): LinkedListDateComplexRangeBaseBuilder(fragments), DateComplexRangeModify {
    override fun set(fragment: DateFragment) {
        includeFragment(fragment)
    }

    override fun unset(fragment: DateFragment) {
        excludeFragment(fragment)
    }
}