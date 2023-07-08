package com.github.pelmenstar1.rangecalendar

import androidx.collection.ArraySet
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.pelmenstar1.rangecalendar.gesture.RangeCalendarGestureType
import com.github.pelmenstar1.rangecalendar.gesture.RangeCalendarGestureTypeSet
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(AndroidJUnit4::class)
class RangeCalendarGestureTypeSetTests {
    class IterableAsArray<T>(private val array: Array<T>) : Iterable<T> {
        override fun iterator(): Iterator<T> = array.iterator()
    }

    private val typeOrdinalMinusOne = RangeCalendarGestureType(ordinal = -1, displayName = "typeOrdinalMinusOne")
    private val typeOrdinal0 = RangeCalendarGestureType(ordinal = 0, displayName = "typeOrdinal0")
    private val typeOrdinal1 = RangeCalendarGestureType(ordinal = 1, displayName = "typeOrdinal1")
    private val typeOrdinal5 = RangeCalendarGestureType(ordinal = 5, displayName = "typeOrdinal5")
    private val typeOrdinal64 = RangeCalendarGestureType(ordinal = -1, displayName = "typeOrdinal64")

    private fun createBitsTestHelper(
        expectedBits: Long,
        elements: Array<RangeCalendarGestureType>,
        actualSet: RangeCalendarGestureTypeSet
    ) {
        assertIs<RangeCalendarGestureTypeSet.BitsImpl>(actualSet)
        assertEquals(expectedBits, actualSet.bits, "elements: ${elements.contentToString()}")
        assertEquals(elements.size, actualSet.size)
        assertContentEquals(elements, actualSet.elements)
    }

    private fun createBitsTestCases(testHelper: (bits: Long, elements: Array<RangeCalendarGestureType>) -> Unit) {
        testHelper(0L, emptyArray())
        testHelper(0b1L, arrayOf(typeOrdinal0))
        testHelper(0b10L, arrayOf(typeOrdinal1))
        testHelper(0b11L, arrayOf(typeOrdinal0, typeOrdinal1))
    }

    @Test
    fun createBitsImplArrayTest() {
        createBitsTestCases { bits, elements ->
            createBitsTestHelper(bits, elements, RangeCalendarGestureTypeSet.create(elements))
        }
    }

    @Test
    fun createBitsImplCollectionTest() {
        createBitsTestCases { bits, elements ->
            createBitsTestHelper(bits, elements, RangeCalendarGestureTypeSet.create(elements.toList()))
        }
    }

    @Test
    fun createBitsImplIterableTest() {
        createBitsTestCases { bits, elements ->
            createBitsTestHelper(bits, elements, RangeCalendarGestureTypeSet.create(IterableAsArray(elements)))
        }
    }

    private fun createArraySetBasedImplTestHelper(
        elements: Array<RangeCalendarGestureType>,
        actualSet: RangeCalendarGestureTypeSet
    ) {
        assertIs<RangeCalendarGestureTypeSet.ArraySetBasedImpl>(actualSet)
        assertEquals(actualSet.set, ArraySet(elements.toList()))
    }

    private fun createArraySetBasedTestCases(testHelper: (elements: Array<RangeCalendarGestureType>) -> Unit) {
        testHelper(arrayOf(typeOrdinal0, typeOrdinal64))
        testHelper(arrayOf(typeOrdinal64))
        testHelper(arrayOf(typeOrdinalMinusOne))
    }

    @Test
    fun createArraySetBasedImplArrayTest() {
        createArraySetBasedTestCases { elements ->
            createArraySetBasedImplTestHelper(elements, RangeCalendarGestureTypeSet.create(elements))
        }
    }

    @Test
    fun createArraySetBasedImplCollectionTest() {
        createArraySetBasedTestCases { elements ->
            createArraySetBasedImplTestHelper(elements, RangeCalendarGestureTypeSet.create(elements.toList()))
        }
    }

    @Test
    fun createArraySetBasedImplIterableTest() {
        createArraySetBasedTestCases { elements ->
            createArraySetBasedImplTestHelper(elements, RangeCalendarGestureTypeSet.create(IterableAsArray(elements)))
        }
    }

    @Test
    fun containsTest() {
        fun testHelper(
            elements: Array<RangeCalendarGestureType>,
            element: RangeCalendarGestureType,
            expectedResult: Boolean
        ) {
            val set = RangeCalendarGestureTypeSet.create(elements)
            val actualResult = set.contains(element)

            assertEquals(expectedResult, actualResult, "elements: ${elements.contentToString()}")
        }

        testHelper(
            elements = arrayOf(typeOrdinal0, typeOrdinal1),
            element = typeOrdinal0,
            expectedResult = true
        )

        testHelper(
            elements = arrayOf(typeOrdinal0, typeOrdinal1),
            element = typeOrdinal1,
            expectedResult = true
        )

        testHelper(
            elements = arrayOf(typeOrdinal0),
            element = typeOrdinal1,
            expectedResult = false
        )

        testHelper(
            elements = emptyArray(),
            element = typeOrdinal1,
            expectedResult = false
        )

        testHelper(
            elements = arrayOf(typeOrdinal64, typeOrdinalMinusOne),
            element = typeOrdinalMinusOne,
            expectedResult = true
        )

        testHelper(
            elements = arrayOf(typeOrdinal64),
            element = typeOrdinalMinusOne,
            expectedResult = true
        )
    }

    @Test
    fun toStringTest() {
        fun testHelper(elements: Array<RangeCalendarGestureType>, expectedResult: String) {
            val set = RangeCalendarGestureTypeSet.create(elements)

            val actualResult = set.toString()
            assertEquals(expectedResult, actualResult, "elements: ${elements.contentToString()}")
        }

        testHelper(
            elements = emptyArray(),
            expectedResult = "RangeCalendarGestureTypeSet(elements=[])"
        )

        testHelper(
            elements = arrayOf(typeOrdinal0),
            expectedResult = "RangeCalendarGestureTypeSet(elements=[typeOrdinal0])"
        )

        testHelper(
            elements = arrayOf(typeOrdinal0, typeOrdinal1),
            expectedResult = "RangeCalendarGestureTypeSet(elements=[typeOrdinal0, typeOrdinal1])"
        )

        testHelper(
            elements = arrayOf(typeOrdinal0, typeOrdinal0, typeOrdinal5, typeOrdinal5),
            expectedResult = "RangeCalendarGestureTypeSet(elements=[typeOrdinal0, typeOrdinal5])"
        )

        testHelper(
            elements = arrayOf(typeOrdinalMinusOne),
            expectedResult = "RangeCalendarGestureTypeSet(elements=[typeOrdinalMinusOne])"
        )

        testHelper(
            elements = arrayOf(typeOrdinalMinusOne, typeOrdinalMinusOne),
            expectedResult = "RangeCalendarGestureTypeSet(elements=[typeOrdinalMinusOne])"
        )
    }
}