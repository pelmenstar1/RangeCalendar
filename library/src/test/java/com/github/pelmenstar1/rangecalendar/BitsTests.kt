package com.github.pelmenstar1.rangecalendar

import com.github.pelmenstar1.rangecalendar.utils.iterateSetBits
import org.junit.Test
import kotlin.test.assertContentEquals

class BitsTests {
    private fun <T> iterateSetBitsTestHelper(
        value: T,
        iterateSetBits: T.((bitIndex: Int) -> Unit) -> Unit,
        valueSetBits: IntArray
    ) {
        val actualSetBitsList = ArrayList<Int>()
        value.iterateSetBits { bitIndex ->
            actualSetBitsList.add(bitIndex)
        }

        val actualSetBits = actualSetBitsList.toIntArray()
        assertContentEquals(valueSetBits, actualSetBits)
    }

    @Test
    fun intIterateSetBitsTest() {
        fun testHelper(value: Int, valueSetBits: IntArray) {
            iterateSetBitsTestHelper(value, Int::iterateSetBits, valueSetBits)
        }

        testHelper(value = 0, valueSetBits = intArrayOf())
        testHelper(value = 0b11000000_00000000_00000000_10000001.toInt(), valueSetBits = intArrayOf(0, 7, 30, 31))
        testHelper(value = -1, valueSetBits = (0..31).toList().toIntArray())
    }

    @Test
    fun longIterateSetBitsTest() {
        fun testHelper(value: Long, valueSetBits: IntArray) {
            iterateSetBitsTestHelper(value, Long::iterateSetBits, valueSetBits)
        }

        testHelper(value = 0, valueSetBits = intArrayOf())
        testHelper(
            value = 0b00000011_00000000_00000000_00000000_00000000_00000000_00000000_10000001L,
            valueSetBits = intArrayOf(0, 7, 56, 57)
        )
    }
}