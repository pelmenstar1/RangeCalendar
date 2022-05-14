package io.github.pelmenstar1.rangecalendar

import android.os.Parcelable
import android.os.Parcel
import android.os.Parcelable.Creator
import android.view.AbsSavedState

internal class SavedState : AbsSavedState {
    var ym = YearMonth(0)
    var selectionType = 0
    var selectionYm = YearMonth(0)
    var selectionData = 0

    constructor(superState: Parcelable) : super(superState)

    constructor(source: Parcel) : super(source) {
        source.run {
            ym = YearMonth(readInt())
            selectionType = readInt()
            selectionYm = YearMonth(readInt())
            selectionData = readInt()
        }

    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)

        dest.run {
            writeInt(ym.totalMonths)
            writeInt(selectionType)
            writeInt(selectionYm.totalMonths)
            writeInt(selectionData)
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