package com.github.pelmenstar1.rangecalendar

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.view.AbsSavedState
import com.github.pelmenstar1.rangecalendar.selection.CellRange

internal class SavedState : AbsSavedState {
    var ym = YearMonth(0)
    var selectionYm = YearMonth(0)
    var selectionRange = CellRange.Invalid

    // First day of week is also saved. If when we restore state, first day of week is different than this one,
    // we don't do anything. We could save date range instead of cell range but it won't fix the problem as
    // date range may happen to be on different pages that is currently illegal.
    var firstDayOfWeek = CompatDayOfWeek.Undefined

    constructor(superState: Parcelable) : super(superState)

    constructor(source: Parcel) : super(source) {
        source.run {
            ym = YearMonth(readInt())
            selectionYm = YearMonth(readInt())
            selectionRange = CellRange(readInt())
            firstDayOfWeek = CompatDayOfWeek(readInt())
        }
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)

        dest.run {
            writeInt(ym.totalMonths)
            writeInt(selectionYm.totalMonths)
            writeInt(selectionRange.bits)
            writeInt(firstDayOfWeek.value)
        }
    }

    companion object {
        @JvmField
        val CREATOR: Creator<SavedState> = object : Creator<SavedState> {
            override fun createFromParcel(source: Parcel) = SavedState(source)
            override fun newArray(size: Int) = arrayOfNulls<SavedState>(size)
        }
    }
}