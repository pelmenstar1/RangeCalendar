package com.github.pelmenstar1.rangecalendar

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.view.AbsSavedState
import com.github.pelmenstar1.rangecalendar.selection.NarrowSelectionData

internal class SavedState : AbsSavedState {
    var ym = YearMonth(0)
    var selectionType = SelectionType.NONE
    var selectionYm = YearMonth(0)
    var selectionData = NarrowSelectionData(0)

    constructor(superState: Parcelable) : super(superState)

    constructor(source: Parcel) : super(source) {
        source.run {
            ym = YearMonth(readInt())
            selectionType = SelectionType.ofOrdinal(readInt())
            selectionYm = YearMonth(readInt())
            selectionData = NarrowSelectionData(readInt())
        }

    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)

        dest.run {
            writeInt(ym.totalMonths)
            writeInt(selectionType.ordinal)
            writeInt(selectionYm.totalMonths)
            writeInt(selectionData.bits)
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