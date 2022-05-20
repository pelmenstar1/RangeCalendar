package io.github.pelmenstar1.rangecalendar.decoration

import android.content.Context
import android.os.Parcelable
import io.github.pelmenstar1.rangecalendar.PackedDate
import io.github.pelmenstar1.rangecalendar.selection.Cell

abstract class CellDecor<TSelf : CellDecor<TSelf>> {
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

    val year: Int
        get() = date.year

    val month: Int
        get() = date.month

    val dayOfMonth: Int
        get() = date.dayOfMonth

    abstract fun newRenderer(context: Context): CellDecorRenderer<TSelf>
}