package com.github.pelmenstar1.rangecalendar.complexRange

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

abstract class BaseListIteratorTests<T> {
    class SubListData<T>(val elements: Array<T>, val subRange: IntRange)

    abstract fun createIterator(elements: Array<T>): ListIterator<T>
    abstract fun iterateForwardBackwardDataset(): List<Array<T>>

    abstract fun iterateSubListForwardBackwardDataset(): List<SubListData<T>>
    abstract fun createSubIterator(elements: Array<T>, subRange: IntRange): ListIterator<T>

    private fun forwardIteratorTest(iter: ListIterator<T>, expectedValues: Array<T>) {
        var index = 0
        while (iter.hasNext()) {
            val nextIndex = iter.nextIndex()
            assertEquals(index, nextIndex, "next index")

            val iterValue = iter.next()
            val expectedValue = expectedValues[index++]

            assertEquals(expectedValue, iterValue, "next value")
        }

        assertEquals(expectedValues.size, index)
    }

    private fun backwardIteratorTest(iter: ListIterator<T>, expectedValues: Array<T>) {
        var index = expectedValues.size - 1

        while (iter.hasPrevious()) {
            val prevIndex = iter.previousIndex()
            assertEquals(index, prevIndex, "previous index")

            val iterValue = iter.previous()
            val expectedValue = expectedValues[index--]

            assertEquals(expectedValue, iterValue, "previous value")
        }

        assertEquals(-1, index)
    }

    private fun iteratorTestHelper(iter: ListIterator<T>, expectedValues: Array<T>) {
        forwardIteratorTest(iter, expectedValues)
        backwardIteratorTest(iter, expectedValues)
        forwardIteratorTest(iter, expectedValues)
    }

    @Test
    fun iterateForwardBackwardTest() {
        for (data in iterateForwardBackwardDataset()) {
            val iter = createIterator(data)

            iteratorTestHelper(iter, data)
        }
    }

    @Test
    fun iterateSubListForwardBackwardTest() {
        for (data in iterateSubListForwardBackwardDataset()) {
            val subRange = data.subRange

            val iter = createSubIterator(data.elements, subRange)
            val expectedElements = data.elements.copyOfRange(subRange.first, subRange.last + 1)

            iteratorTestHelper(iter, expectedElements)
        }
    }
}