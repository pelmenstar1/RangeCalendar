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

    constructor(superState: Parcelable) : super(superState)

    constructor(source: Parcel) : super(source) {
        source.run {
            ym = YearMonth(readInt())
            selectionYm = YearMonth(readInt())
            selectionRange = CellRange(readInt())
        }

    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)

        dest.run {
            writeInt(ym.totalMonths)
            writeInt(selectionYm.totalMonths)
            writeInt(selectionRange.bits)
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