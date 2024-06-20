package com.github.pelmenstar1.rangecalendar.complexRange.date

import kotlin.test.*

class RawLinkedListTests {
    private fun createList(values: IntArray): RawLinkedList<Int> {
        return RawLinkedList<Int>().apply {
            values.forEach { add(it) }
        }
    }

    private fun createSubList(values: IntArray): RawLinkedList<Int> {
        val list = RawLinkedList<Int>().apply {
            add(Int.MIN_VALUE)
            values.forEach { add(it) }
            add(Int.MAX_VALUE)
        }

        return list.subList(1, list.size - 1)
    }

    @Test
    fun sizeTest() {
        fun testCase(values: IntArray) {
            val list = createList(values)
            val actual = list.size

            assertEquals(values.size, actual)
        }

        testCase(intArrayOf())
        testCase(intArrayOf(0))
        testCase(intArrayOf(1, 3))
        testCase(intArrayOf(1, 3, 6))
    }

    @Test
    fun isEmptyTest() {
        fun testCase(values: IntArray) {
            val list = createList(values)

            assertEquals(values.isEmpty(), list.isEmpty())
        }

        testCase(intArrayOf())
        testCase(intArrayOf(0))
    }

    @Test
    fun addTest() {
        fun testCase(values: IntArray) {
            val list = createList(values)

            validateList(list, values)
        }

        testCase(intArrayOf())
        testCase(intArrayOf(0))
        testCase(intArrayOf(1, 3))
        testCase(intArrayOf(1, 3, 5))
    }

    @Test
    fun getAtIndexTest() {
        fun testCase(values: IntArray) {
            val list = createList(values)

            for (i in values.indices) {
                val actual = list[i]
                val expected = values[i]

                assertEquals(expected, actual)
            }
        }

        testCase(intArrayOf(0))
        testCase(intArrayOf(1, 3))
        testCase(intArrayOf(1, 3, 5))
    }

    @Test
    fun insertAfterNodeTest() {
        fun testCase(
            initialValues: IntArray,
            insertAfterIndex: Int,
            newValue: Int,
            expectedValues: IntArray
        ) {
            val list = createList(initialValues)
            val node = list.getNode(insertAfterIndex)

            list.insertAfterNode(newValue, node)

            validateList(list, expectedValues)
        }

        testCase(
            initialValues = intArrayOf(0),
            insertAfterIndex = 0,
            newValue = 2,
            expectedValues = intArrayOf(0, 2)
        )

        testCase(
            initialValues = intArrayOf(0, 4),
            insertAfterIndex = 0,
            newValue = 2,
            expectedValues = intArrayOf(0, 2, 4)
        )

        testCase(
            initialValues = intArrayOf(0, 4),
            insertAfterIndex = 1,
            newValue = 2,
            expectedValues = intArrayOf(0, 4, 2)
        )
    }

    @Test
    fun insertBeforeNodeTest() {
        fun testCase(initialValues: IntArray, nodeIndex: Int, newValue: Int, expectedValues: IntArray) {
            val list = createList(initialValues)
            val node = list.getNode(nodeIndex)
            list.insertBeforeNode(newValue, node)

            validateList(list, expectedValues)
        }

        testCase(initialValues = intArrayOf(0), nodeIndex = 0, newValue = 1, expectedValues = intArrayOf(1, 0))
        testCase(initialValues = intArrayOf(0, 2), nodeIndex = 1, newValue = 1, expectedValues = intArrayOf(0, 1, 2))
    }

    @Test
    fun removeValueTest() {
        fun testCase(initialValues: IntArray, valueToRemove: Int, expectedRanges: IntArray) {
            val list = createList(initialValues)
            list.remove(valueToRemove)

            validateList(list, expectedRanges)
        }

        testCase(initialValues = intArrayOf(0), valueToRemove = 0, expectedRanges = intArrayOf())
        testCase(initialValues = intArrayOf(0, 1), valueToRemove = 0, expectedRanges = intArrayOf(1))
        testCase(initialValues = intArrayOf(0, 1, 2), valueToRemove = 0, expectedRanges = intArrayOf(1, 2))
        testCase(initialValues = intArrayOf(0, 1, 2), valueToRemove = 1, expectedRanges = intArrayOf(0, 2))
        testCase(initialValues = intArrayOf(0, 1), valueToRemove = 1, expectedRanges = intArrayOf(0))
    }

    @Test
    fun removeBetweenTest() {
        fun testCase(initialValues: IntArray, removeRange: IntRange, expectedRanges: IntArray) {
            val list = createList(initialValues)
            val removalStartNode = list.getNode(removeRange.first)
            val removalEndNode = list.getNode(removeRange.last)

            list.removeBetween(removalStartNode, removalEndNode)

            validateList(list, expectedRanges)
        }

        testCase(initialValues = intArrayOf(0), removeRange = 0..0, expectedRanges = intArrayOf())
        testCase(initialValues = intArrayOf(0, 1), removeRange = 0..1, expectedRanges = intArrayOf())
        testCase(initialValues = intArrayOf(0, 1), removeRange = 0..0, expectedRanges = intArrayOf(1))
        testCase(initialValues = intArrayOf(0, 1, 2), removeRange = 0..1, expectedRanges = intArrayOf(2))
        testCase(initialValues = intArrayOf(0, 1, 2), removeRange = 1..2, expectedRanges = intArrayOf(0))
        testCase(initialValues = intArrayOf(0, 1, 2), removeRange = 1..1, expectedRanges = intArrayOf(0, 2))
        testCase(initialValues = intArrayOf(0, 1, 2, 3), removeRange = 1..2, expectedRanges = intArrayOf(0, 3))
    }

    @Test
    fun replaceBetweenWithTest() {
        fun testCase(
            initialRanges: IntArray,
            replaceValue: Int,
            startIndex: Int,
            endIndex: Int,
            expectedRanges: IntArray
        ) {
            val list = createList(initialRanges)
            val removalStartNode = list.getNode(startIndex)
            val removalEndNode = list.getNode(endIndex)

            list.replaceBetweenWith(replaceValue, removalStartNode, removalEndNode)

            validateList(list, expectedRanges)
        }

        testCase(
            initialRanges = intArrayOf(0),
            replaceValue = 1,
            startIndex = 0,
            endIndex = 0,
            expectedRanges = intArrayOf(1)
        )

        testCase(
            initialRanges = intArrayOf(0, 2),
            replaceValue = 1,
            startIndex = 0,
            endIndex = 0,
            expectedRanges = intArrayOf(1, 2)
        )

        testCase(
            initialRanges = intArrayOf(0, 2),
            replaceValue = 1,
            startIndex = 0,
            endIndex = 0,
            expectedRanges = intArrayOf(1, 2)
        )

        testCase(
            initialRanges = intArrayOf(0, 2),
            replaceValue = 1,
            startIndex = 1,
            endIndex = 1,
            expectedRanges = intArrayOf(0, 1)
        )

        testCase(
            initialRanges = intArrayOf(0, 1, 2, 3),
            replaceValue = 4,
            startIndex = 1,
            endIndex = 2,
            expectedRanges = intArrayOf(0, 4, 3)
        )

        testCase(
            initialRanges = intArrayOf(0, 1, 2, 3),
            replaceValue = 4,
            startIndex = 2,
            endIndex = 3,
            expectedRanges = intArrayOf(0, 1, 4)
        )
    }

    @Test
    fun copyOfTest() {
        fun testCase(elements: IntArray) {
            val origin = createList(elements)
            val copy = origin.copyOf()

            var originCurrent = origin.head
            var copyCurrent = copy.head

            while (originCurrent != null) {
                assertNotNull(copyCurrent)
                assertNotSame(originCurrent, copyCurrent)

                originCurrent = originCurrent.next
                copyCurrent = copyCurrent.next
            }

            validateList(copy, elements)
        }

        testCase(intArrayOf())
        testCase(intArrayOf(1))
        testCase(intArrayOf(1, 2))
        testCase(intArrayOf(1, 2, 3))
    }

    @Test
    fun subListTest() {
        fun testCase(elements: IntArray, subRange: IntRange) {
            val list = createList(elements)

            val subElements = elements.copyOfRange(subRange.first, subRange.last + 1)
            val subList = list.subList(subRange.first, subRange.last + 1)

            validateList(subList, subElements)
        }

        testCase(elements = intArrayOf(1, 2, 3), subRange = 1..1)
        testCase(elements = intArrayOf(1, 2, 3), subRange = 0..1)
        testCase(elements = intArrayOf(1, 2, 3), subRange = 1..2)
        testCase(elements = intArrayOf(1, 2, 3, 4), subRange = 1..2)
        testCase(elements = intArrayOf(1, 2, 3, 4), subRange = 1..3)
    }

    @Test
    fun equalsTest() {
        fun testCase(firstElements: IntArray, secondElements: IntArray, expected: Boolean) {
            val firstList = createList(firstElements)
            val secondList = createList(secondElements)

            val actual = firstList == secondList
            assertEquals(expected, actual)
        }

        testCase(firstElements = intArrayOf(), secondElements = intArrayOf(), expected = true)
        testCase(firstElements = intArrayOf(1), secondElements = intArrayOf(), expected = false)
        testCase(firstElements = intArrayOf(), secondElements = intArrayOf(1), expected = false)
        testCase(firstElements = intArrayOf(1), secondElements = intArrayOf(1), expected = true)
        testCase(firstElements = intArrayOf(1), secondElements = intArrayOf(2), expected = false)
        testCase(firstElements = intArrayOf(1, 2), secondElements = intArrayOf(1, 2), expected = true)
        testCase(firstElements = intArrayOf(1, 2), secondElements = intArrayOf(1, 2, 3), expected = false)
        testCase(firstElements = intArrayOf(1, 2, 3), secondElements = intArrayOf(1, 2, 3), expected = true)
    }

    @Test
    fun toStringTest() {
        fun testCase(elements: IntArray, expected: String) {
            val list = createList(elements)
            val actual = list.toString()

            assertEquals(expected, actual)
        }

        testCase(intArrayOf(), expected = "RangeFragmentList()")
        testCase(intArrayOf(1), expected = "RangeFragmentList(1)")
        testCase(intArrayOf(1, 2), expected = "RangeFragmentList(1, 2)")
        testCase(intArrayOf(1, 2, 3), expected = "RangeFragmentList(1, 2, 3)")
    }

    private fun validateList(list: RawLinkedList<Int>, expectedElements: IntArray) {
        val expectedSize = expectedElements.size
        val head = list.head
        val tail = list.tail

        val listSize = list.size
        assertEquals(expectedSize, listSize)

        if (expectedSize == 0) {
            assertNull(head)
            assertNull(tail)
            return
        } else if (expectedSize == 1) {
            assertNotNull(head)
            assertSame(head, tail)
            return
        }

        assertNotNull(head)
        assertNotNull(tail)

        var index = 0
        var previous: RawLinkedList.Node<Int>? = head.previous

        list.forEachNode { current ->
            val expectedValue = expectedElements[index]
            val actualValue = current.value

            assertSame(previous, current.previous)
            assertEquals(expectedValue, actualValue)

            index++
            previous = current
        }

        assertSame(tail, previous)
        assertEquals(expectedSize, index)
    }
}
