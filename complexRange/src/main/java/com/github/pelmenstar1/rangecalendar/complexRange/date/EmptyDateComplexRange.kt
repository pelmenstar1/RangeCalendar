package com.github.pelmenstar1.rangecalendar.complexRange.date

internal object EmptyDateComplexRange : DateComplexRange {
    override fun modify(block: DateComplexRangeModify.() -> Unit): DateComplexRange {
        val fragments = RawLinkedList<DateFragment>()
        LinkedListDateComplexRangeModify(fragments).also(block)

        return LinkedListDateComplexRange(fragments)
    }

    override fun fragments(): List<DateFragment> {
        return emptyList()
    }

    override fun equals(other: Any?): Boolean {
        return when {
            other === this -> true
            other is DateComplexRange -> other.fragments().isEmpty()
            else -> false
        }
    }

    override fun hashCode(): Int {
        return 1
    }

    override fun toString(): String {
        return "DateComplexRange()"
    }
}