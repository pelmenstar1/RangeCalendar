package com.github.pelmenstar1.rangecalendar

import android.os.Parcel
import android.view.AbsSavedState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.pelmenstar1.rangecalendar.selection.CellRange
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SavedStateTests {
    @Test
    fun readWriteTest() {
        val expectedSelYm = YearMonth(2023, 7)
        val expectedSelRange = CellRange(3, 4)
        val expectedYm = YearMonth(2023, 8)
        val expectedFirstDow = CompatDayOfWeek.Friday

        val savedState = SavedState(AbsSavedState.EMPTY_STATE).apply {
            selectionYm = expectedSelYm
            selectionRange = expectedSelRange
            ym = expectedYm
            firstDayOfWeek = expectedFirstDow
        }

        val parcel = Parcel.obtain()
        savedState.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val actualState = SavedState.CREATOR.createFromParcel(parcel)

        assertEquals(expectedSelYm, actualState.selectionYm, "selectionYm")
        assertEquals(expectedSelRange, actualState.selectionRange, "selectionRange")
        assertEquals(expectedYm, actualState.ym, "ym")
        assertEquals(expectedFirstDow, actualState.firstDayOfWeek, "firstDayOfWeek")
    }
}