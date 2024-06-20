package com.github.pelmenstar1.rangecalendar.complexRange.date

internal class LinkedListDateComplexRange(
    private val fragments: RawLinkedList<DateFragment>
): DateComplexRange {
    override fun modify(block: DateComplexRangeModify.() -> Unit): DateComplexRange {
        val fragmentsCopy = fragments.copyOf()
        LinkedListDateComplexRangeModify(fragmentsCopy).also(block)

        return LinkedListDateComplexRange(fragmentsCopy)
    }

    override fun fragments(): List<DateFragment> {
        return fragments
    }

    override fun equals(other: Any?): Boolean {
        when {
            other === this -> return true
            other is LinkedListDateComplexRange -> {
                return fragments == other.fragments
            }
            other is DateComplexRange -> {
                val otherFrags = other.fragments()
                val otherIter = otherFrags.iterator()

                var currentNode = fragments.head

                while (true) {
                    val hasNext = otherIter.hasNext()
                    if (!hasNext || currentNode == null) {
                        return !hasNext && currentNode == null
                    }

                    val otherElement = otherIter.next()
                    val currentElement = currentNode.value

                    if (currentElement != otherElement) {
                        return false
                    }

                    currentNode = currentNode.next
                }
            }
            else -> return false
        }
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
}

fun newDateComplexRangeBuilder(): DateComplexRangeBuilder {
    return LinkedListDateComplexRangeBuilder()
}

internal class LinkedListDateComplexRangeBuilder : LinkedListDateComplexRangeBaseBuilder(),
    DateComplexRangeBuilder {
    override fun fragment(value: DateFragment) {
        includeFragment(value)
    }

    override fun build(): DateComplexRange {
        return LinkedListDateComplexRange(fragments)
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