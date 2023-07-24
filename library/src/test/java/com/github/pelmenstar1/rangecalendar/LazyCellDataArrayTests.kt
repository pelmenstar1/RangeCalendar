package com.github.pelmenstar1.rangecalendar

import com.github.pelmenstar1.rangecalendar.decoration.LazyCellDataArray
import com.github.pelmenstar1.rangecalendar.selection.Cell
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.fail

class LazyCellDataArrayTests {
    @Test
    fun getTestOnEmptyArray() {
        val array = LazyCellDataArray<Any>()

        assertNull(array[Cell(0)])
        assertNull(array[Cell(2)])
    }

    @Test
    fun getSetTest() {
        val array = LazyCellDataArray<Any>()
        val obj0 = Any()
        val obj1 = Any()
        val obj2 = Any()

        array[Cell(0)] = obj0
        array[Cell(1)] = obj1
        array[Cell(2)] = obj2

        assertSame(obj0, array[Cell(0)], "index: 0")
        assertSame(obj1, array[Cell(1)], "index: 1")
        assertSame(obj2, array[Cell(2)], "index: 2")

        array[Cell(2)] = null

        assertNull(array[Cell(2)], "index: 2")
    }

    private fun assertEmpty(array: LazyCellDataArray<Any>) {
        for (i in 0 until GridConstants.CELL_COUNT) {
            assertNull(array[Cell(i)], "index: $i")
        }
    }

    @Test
    fun clearOnEmptyTest() {
        val array = LazyCellDataArray<Any>()
        array.clear()

        assertEmpty(array)
    }

    @Test
    fun clearTest() {
        val array = LazyCellDataArray<Any>()

        for (i in 0 until GridConstants.CELL_COUNT) {
            array[Cell(i)] = Any()
        }

        array.clear()

        assertEmpty(array)
    }

    @Test
    fun forEachNotNullOnEmptyTest() {
        LazyCellDataArray<Any>().forEachNotNull { _, _ ->
            fail("The lambda should not be called")
        }
    }

    @Test
    fun forEachNotNullEmptyTest() {
        fun testCase(indices: IntArray) {
            val array = LazyCellDataArray<Any>()
            val expectedArray = arrayOfNulls<Any>(GridConstants.CELL_COUNT)

            val indicesAndElements = indices.map { it to Any() }

            for ((index, value) in indicesAndElements) {
                array[Cell(index)] = value
                expectedArray[index] = value
            }

            val actualIndicesAndElements = ArrayList<Pair<Int, Any>>()

            array.forEachNotNull { cell, value ->
                actualIndicesAndElements.add(cell.index to value)
            }

            assertContentEquals(indicesAndElements, actualIndicesAndElements)
        }

        testCase(intArrayOf(0, 1, 5, 6))
        testCase((0 until 41).toList().toIntArray())
        testCase(intArrayOf(0))
        testCase(intArrayOf(41))
        testCase(intArrayOf(1, 5, 6, 40, 41))
    }
}