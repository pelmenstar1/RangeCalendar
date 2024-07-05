package com.github.pelmenstar1.rangecalendar.complexRange.date

import com.github.pelmenstar1.rangecalendar.complexRange.BaseListIteratorTests

class RawLinkedListIteratorTests : BaseListIteratorTests<Int>() {
    override fun createIterator(elements: Array<Int>): MutableListIterator<Int> {
        return RawLinkedList<Int>().apply {
            elements.forEach { add(it) }
        }.listIterator()
    }

    override fun iterateForwardBackwardDataset(): List<Array<Int>> {
        return listOf(
            emptyArray(),
            arrayOf(1),
            arrayOf(1, 2),
            arrayOf(1, 2, 3),
        )
    }

    override fun createSubIterator(elements: Array<Int>, subRange: IntRange): MutableListIterator<Int> {
        return RawLinkedList<Int>().apply {
            elements.forEach { add(it) }
        }.subList(subRange.first, subRange.last + 1).listIterator()
    }

    override fun iterateSubListForwardBackwardDataset(): List<SubListData<Int>> {
        return listOf(
            SubListData(arrayOf(1, 2, 3), subRange = 1..1),
            SubListData(arrayOf(1, 2, 3), subRange = 1..2),
            SubListData(arrayOf(1, 2, 3), subRange = 0..1),
            SubListData(arrayOf(1, 2, 3), subRange = 0..0),
            SubListData(arrayOf(1, 2), subRange = IntRange.EMPTY),
            SubListData(arrayOf(1, 2, 3, 4), subRange = 1..3),
        )
    }
}