package com.github.pelmenstar1.rangecalendar.complexRange.date

import com.github.pelmenstar1.rangecalendar.utils.emptyIterator
import com.github.pelmenstar1.rangecalendar.utils.singleItemIterator

internal class SingleFragmentDateComplexRange(private val fragment: DateFragment) :
    DateComplexRange {
    private val fragments = FragmentListImpl(fragment)

    override fun modify(block: DateComplexRangeModify.() -> Unit): DateComplexRange {
        val fragmentList = RawLinkedList<DateFragment>().also {
            it.add(fragment)
        }

        LinkedListDateComplexRangeModify(fragmentList).also(block)

        return LinkedListDateComplexRange(fragmentList)
    }

    override fun fragments(): List<DateFragment> {
        return fragments
    }

    override fun equals(other: Any?): Boolean {
        when {
            other === this -> return true
            other is SingleFragmentDateComplexRange -> return fragment == other.fragment
            other is DateComplexRange -> {
                val otherFrags = other.fragments()
                if (otherFrags.isNotEmpty()) {
                    val iter = otherFrags.iterator()
                    val firstFragment = iter.next()

                    if (!iter.hasNext()) {
                        return fragment == firstFragment
                    }
                }
            }
        }

        return false
    }

    override fun hashCode(): Int {
        return 31 + fragment.hashCode()
    }

    override fun toString(): String {
        return "DateComplexRange($fragment)"
    }

    private class FragmentListImpl(private val fragment: DateFragment): List<DateFragment> {
        override val size: Int
            get() = 1

        override fun isEmpty(): Boolean = false

        override fun get(index: Int): DateFragment {
            return if (index == 0) fragment else throw IndexOutOfBoundsException("index")
        }

        override fun indexOf(element: DateFragment): Int {
            return if (fragment == element) 0 else -1
        }

        override fun lastIndexOf(element: DateFragment): Int = indexOf(element)

        override fun contains(element: DateFragment): Boolean {
            return element == fragment
        }

        override fun containsAll(elements: Collection<DateFragment>): Boolean {
            return elements.all { it in this }
        }

        override fun subList(fromIndex: Int, toIndex: Int): List<DateFragment> {
            if (fromIndex == 0) {
                when(toIndex) {
                    0 -> return emptyList()
                    1 -> return this
                }
            }

            throw IndexOutOfBoundsException()
        }

        override fun iterator(): Iterator<DateFragment> {
            return listIterator()
        }

        override fun listIterator(): ListIterator<DateFragment> {
            return singleItemIterator(fragment)
        }

        override fun listIterator(index: Int): ListIterator<DateFragment> {
            return when (index) {
                0 -> singleItemIterator(fragment)
                1 -> emptyIterator()
                else -> throw IndexOutOfBoundsException()
            }
        }
    }
}