package io.github.pelmenstar1.rangecalendar

import io.github.pelmenstar1.rangecalendar.decoration.CellDecor
import io.github.pelmenstar1.rangecalendar.decoration.DecorSortedList
import io.github.pelmenstar1.rangecalendar.selection.Cell
import org.junit.Test
import kotlin.collections.ArrayList
import kotlin.math.min
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

private typealias DecorArrayList = ArrayList<CellDecor>

private class TestDecor : CellDecor() {
    override fun visual(): Visual {
        throw RuntimeException()
    }

    override fun toString(): String {
        return System.identityHashCode(this).toString(16)
    }
}

class DecorSortedListTests {
    private inline fun validateActionOnCommonList(
        initialLength: Int,
        actionOnDecorList: DecorSortedList.() -> Unit,
        actionOnCommonList: DecorArrayList.() -> Unit,
        message: String? = null
    ) {
        val decorList = DecorSortedList(initialLength)
        val arrayList = DecorArrayList(initialLength)

        repeat(initialLength) { i ->
            val decor = TestDecor()

            arrayList.add(decor)
            decorList.elements[i] = decor
        }

        decorList.actionOnDecorList()
        arrayList.actionOnCommonList()

        val arrayListContent = arrayList.toTypedArray()

        assertContentEquals(arrayListContent, decorList.elements, message)
    }

    private fun createList(vararg cellIndices: Pair<Int, Int>): DecorSortedList {
        return DecorSortedList().apply {
            cellIndices.forEach { pair ->
                addAll(Array(pair.second) {
                    TestDecor().apply { cell = Cell(pair.first) }
                })
            }
        }
    }

    private fun DecorSortedList.addDecorWithCell(index: Int) {
        add(TestDecor().apply { cell = Cell(index) })
    }

    private fun createList(vararg cellIndices: Int): DecorSortedList {
        return DecorSortedList().apply {
            cellIndices.forEach { addDecorWithCell(it) }
        }
    }

    // Primary operations when all the decors are in one region
    @Test
    fun addTestPrimary() {
        fun validateAdd(initialLength: Int) {
            val decor = TestDecor()

            validateActionOnCommonList(
                initialLength,
                actionOnDecorList = { add(decor) },
                actionOnCommonList = { add(decor) }
            )
        }

        validateAdd(initialLength = 0)
        validateAdd(initialLength = 1)
        validateAdd(initialLength = 2)
    }

    private fun assertRegion(list: DecorSortedList, cellIndex: Int, expectedRange: PackedIntRange) {
        assertEquals(expectedRange, list.getRegionByCell(Cell(cellIndex)))
    }

    @Test
    fun addTestRegions() {
        val list = createList(0, 0, 0, 1, 1, 2)

        assertRegion(list, cellIndex = 0, expectedRange = PackedIntRange(0, 2))
        assertRegion(list, cellIndex = 1, expectedRange = PackedIntRange(3, 4))
        assertRegion(list, cellIndex = 2, expectedRange = PackedIntRange(5, 5))
    }

    @Test
    fun addAllTest() {
        fun validateAddAll(initialLength: Int, addAmount: Int) {
            val array = Array(addAmount) { TestDecor() }

            validateActionOnCommonList(
                initialLength,
                actionOnDecorList = { addAll(array) },
                actionOnCommonList = { addAll(listOf(*array)) }
            )
        }

        validateAddAll(0, 1)
        validateAddAll(0, 5)
        validateAddAll(2, 2)
        validateAddAll(3, 7)
    }

    @Test
    fun addAllTestRegions() {
        val list = createList(0 to 3, 1 to 2, 2 to 1)

        assertRegion(list, cellIndex = 0, expectedRange = PackedIntRange(0, 2))
        assertRegion(list, cellIndex = 1, expectedRange = PackedIntRange(3, 4))
        assertRegion(list, cellIndex = 2, expectedRange = PackedIntRange(5, 5))
    }

    @Test
    fun insertAllTest() {
        fun validateAddAll(initialLength: Int, position: Int, addAmount: Int) {
            val array = Array(addAmount) { TestDecor() }

            validateActionOnCommonList(
                initialLength,
                actionOnDecorList = { insertAll(position, array) },
                actionOnCommonList = { addAll(min(size, position), listOf(*array)) },
                "Elements to insert: ${array.contentToString()}"
            )
        }

        validateAddAll(4, 1, 2)
        validateAddAll(2, 5, 3)
        validateAddAll(4, 0, 3)
        validateAddAll(4, 3, 1)
    }

    @Test
    fun iterateRegionsTest() {
        val list = createList(0 to 3, 1 to 2, 2 to 1)
        val expectedSequence = arrayOf(
            PackedIntRange(0, 2) to 0,
            PackedIntRange(3, 4) to 1,
            PackedIntRange(5, 5) to 2
        )
        var seqIndex = 0

        list.iterateRegions { range, cell ->
            val pair = expectedSequence[seqIndex++]

            assertEquals(pair.first, range)
            assertEquals(Cell(pair.second), cell)
        }
    }

    @Test
    fun removeTest() {
        fun validateSingleRemove(initialLength: Int, removeIndex: Int) {
            validateActionOnCommonList(
                initialLength,
                actionOnCommonList = { removeAt(removeIndex) },
                actionOnDecorList = { remove(removeIndex) }
            )
        }

        validateSingleRemove(4, 0)
        validateSingleRemove(1, 0)
        validateSingleRemove(5, 1)
        validateSingleRemove(5, 4)
    }

    @Test
    fun removeRangeTest() {
        fun validateRangeRemove(initialLength: Int, range: IntRange) {
            validateActionOnCommonList(
                initialLength,
                actionOnCommonList = { subList(range.first, range.last + 1).clear() },
                actionOnDecorList = { removeRange(PackedIntRange(range.first, range.last)) }
            )
        }

        validateRangeRemove(4, 0..1)
        validateRangeRemove(4, 0..0)
        validateRangeRemove(4, 3..3)
        validateRangeRemove(4, 0..2)

        validateRangeRemove(1, 0..0)
        validateRangeRemove(4, 1..2)
        validateRangeRemove(4, 0..3)
        validateRangeRemove(5, 1..3)
    }
}