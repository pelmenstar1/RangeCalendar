package io.github.pelmenstar1.rangecalendar.decoration

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.CallSuper
import io.github.pelmenstar1.rangecalendar.PackedDate
import io.github.pelmenstar1.rangecalendar.selection.Cell

abstract class CellDecor<TSelf : CellDecor<TSelf>> : Parcelable {
    /*
    // Position in RecyclerView
    @get:JvmName("#position")
    @set:JvmName("#position")
    internal var position = 0
    */

    // Index of decor in grid.
    @get:JvmSynthetic
    @set:JvmSynthetic
    internal var cell = Cell.Undefined

    // Exact date of decoration
    @get:JvmSynthetic
    @set:JvmSynthetic
    internal var date = PackedDate(0)

    @set:JvmSynthetic
    var animationFraction = 0f
        internal set

    protected constructor()
    protected constructor(dest: Parcel) {
        date = PackedDate(dest.readInt())
    }

    @CallSuper
    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeInt(date.bits)
    }

    override fun describeContents(): Int = 0

    /*
    @CallSuper
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        other as CellDecor<*>

        return date == other.date
    }

    @CallSuper
    override fun hashCode(): Int {
        return date
    }
     */

    abstract fun newRenderer(context: Context): CellDecorRenderer<TSelf>
}