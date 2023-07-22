package com.github.pelmenstar1.rangecalendar

import org.junit.Test
import kotlin.test.assertEquals

class RangeCalendarStyleDataTests {
    @Test
    fun setIntStyleTest() {
        fun testCase(propIndex: Int, newValue: Int, expectedChanged: Boolean) {
            val styleData = RangeCalendarStyleData()
            val actualChanged = styleData.set(propIndex, newValue)
            val actualValue = styleData.getInt(propIndex)

            assertEquals(expectedChanged, actualChanged)
            assertEquals(newValue, actualValue)
        }

        testCase(RangeCalendarStyleData.WEEKDAY_TYPE, newValue = 1, expectedChanged = true)
        testCase(RangeCalendarStyleData.WEEKDAY_TEXT_SIZE, newValue = 0, expectedChanged = false)
    }

    @Test
    fun setObjectStyleTest() {
        fun testCase(propIndex: Int, newValue: Any?, expectedChanged: Boolean) {
            val styleData = RangeCalendarStyleData()
            val actualChanged = styleData.set(propIndex, newValue)
            val actualValue = styleData.getObject<Any?>(propIndex)

            assertEquals(expectedChanged, actualChanged)
            assertEquals(newValue, actualValue)
        }

        testCase(RangeCalendarStyleData.WEEKDAYS, newValue = Any(), expectedChanged = true)
        testCase(RangeCalendarStyleData.SELECTION_FILL, newValue = null, expectedChanged = false)
    }
}