package com.github.pelmenstar1.rangecalendar

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.view.AbsSavedState
import com.github.pelmenstar1.rangecalendar.complexRange.date.DateComplexRange
import com.github.pelmenstar1.rangecalendar.complexRange.date.DateFragment

internal class SavedState : AbsSavedState {
    var ym = YearMonth(0)
    var selectionRange = DateComplexRange.empty()

    constructor(superState: Parcelable) : super(superState)

    constructor(source: Parcel) : super(source) {
        source.run {
            ym = YearMonth(readInt())
            selectionRange = readDateComplexRange(source)
        }
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, flags)

        dest.writeInt(ym.totalMonths)
        writeDateComplexRange(dest, selectionRange)
    }

    companion object {
        @JvmField
        val CREATOR: Creator<SavedState> = object : Creator<SavedState> {
            override fun createFromParcel(source: Parcel) = SavedState(source)
            override fun newArray(size: Int) = arrayOfNulls<SavedState>(size)
        }

        private fun writeDateComplexRange(parcel: Parcel, complexRange: DateComplexRange) {
            val fragments = complexRange.fragments()
            parcel.writeInt(fragments.size)

            for (fragment in fragments) {
                parcel.writeLong(fragment.startEpochDays)
                parcel.writeLong(fragment.endEpochDays)
            }
        }

        private fun readDateComplexRange(parcel: Parcel): DateComplexRange {
            val fragmentCount = parcel.readInt()

            return DateComplexRange {
                repeat(fragmentCount) {
                    val startEpochDay = parcel.readLong()
                    val endEpochDay = parcel.readLong()

                    val dateFragment = DateFragment(startEpochDay, endEpochDay)
                    fragment(dateFragment)
                }
            }
        }
    }
}