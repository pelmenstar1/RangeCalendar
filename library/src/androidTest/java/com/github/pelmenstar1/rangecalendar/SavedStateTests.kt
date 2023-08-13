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
        val expectedSelRange = PackedDateRange(
            PackedDate(2023, 8, 7),
            PackedDate(2023, 8, 10)
        )
        val expectedYm = YearMonth(2023, 8)

        val savedState = SavedState(AbsSavedState.EMPTY_STATE).apply {
            selectionRange = expectedSelRange
            ym = expectedYm
        }

        val parcel = Parcel.obtain()
        savedState.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)

        val actualState = SavedState.CREATOR.createFromParcel(parcel)

        assertEquals(expectedSelRange, actualState.selectionRange, "selectionRange")
        assertEquals(expectedYm, actualState.ym, "ym")
    }
}