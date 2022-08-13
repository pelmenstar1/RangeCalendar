package com.github.pelmenstar1.rangecalendar

import com.github.pelmenstar1.rangecalendar.decoration.CellDecor
import com.github.pelmenstar1.rangecalendar.decoration.DecorGroupedList
import com.github.pelmenstar1.rangecalendar.selection.Cell
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

class DecorGroupedListTests {
    private inline fun validateActionOnCommonList(
        initialLength: Int,
        actionOnDecorList: DecorGroupedList.() -> Unit,
        actionOnCommonList: DecorArrayList.() -> Unit,
        message: String? = null
    ) {
        val decorList = DecorGroupedList(initialLength)
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

    private fun createListWithAddAll(vararg elements: Pair<Int, Pair<PackedDate, Int>>): DecorGroupedList {
        return DecorGroupedList().apply {
            elements.forEach { pair ->
                addAll(Array(pair.first) {
                    TestDecor().apply {
                        date = pair.second.first
                        cell = Cell(pair.second.second)
                    }
                })
            }
        }
    }

    private fun createListSingleDateToCell(vararg elements: Pair<PackedDate, Int>): DecorGroupedList {
        return DecorGroupedList().apply {
            elements.forEach { addDecorWithDateAndCell(it.first, it.second) }
        }
    }

    private fun DecorGroupedList.addDecorWithDateAndCell(date: PackedDate, index: Int) {
        add(TestDecor().also {
            it.cell = Cell(index)
            it.date = date
        })
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

    private fun assertRegion(list: DecorGroupedList, ym: YearMonth, expectedRange: IntRange) {
        assertEquals(PackedIntRange(expectedRange.first, expectedRange.last), list.getRegion(ym))
    }

    @Test
    fun addTestRegions() {
        val day0 = PackedDate(2000, 1, 1)
        val day1 = PackedDate(2000, 2, 2)
        val day2 = PackedDate(2000, 3, 3)

        val list = createListSingleDateToCell(
            day0 to 0, day0 to 0, day0 to 0,
            day1 to 1, day1 to 1,
            day2 to 2
        )

        assertRegion(list, YearMonth.forDate(day0), expectedRange = 0..2)
        assertRegion(list, YearMonth.forDate(day1), expectedRange = 3..4)
        assertRegion(list, YearMonth.forDate(day2), expectedRange = 5..5)
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
        val day0 = PackedDate(2000, 1, 1)
        val day1 = PackedDate(2000, 2, 2)
        val day2 = PackedDate(2000, 3, 3)

        val list = this.createListWithAddAll(
            3 to (day0 to 0),
            2 to (day1 to 1),
            1 to (day2 to 3)
        )

        assertRegion(list, YearMonth.forDate(day0), expectedRange = 0..2)
        assertRegion(list, YearMonth.forDate(day1), expectedRange = 3..4)
        assertRegion(list, YearMonth.forDate(day2), expectedRange = 5..5)
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