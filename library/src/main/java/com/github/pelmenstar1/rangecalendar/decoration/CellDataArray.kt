package com.github.pelmenstar1.rangecalendar.decoration

import android.util.SparseArray
import com.github.pelmenstar1.rangecalendar.selection.Cell

internal fun<T : Any> CellDataArray(): CellDataArray<T> {
    return CellDataArray(SparseArray())
}

@JvmInline
internal value class CellDataArray<T : Any>(private val sparseArray: SparseArray<T>) {
    internal inline fun forEachNotNull(crossinline action: (cell: Int, value: T) -> Unit) {
        val arr = sparseArray

        for (i in 0 until arr.size()) {
            action(arr.keyAt(i), arr.valueAt(i))
        }
    }

    operator fun get(cell: Int): T? = sparseArray[cell]

    operator fun set(cell: Int, value: T?) {
        sparseArray.put(cell, value)
    }

    fun clear() {
        sparseArray.clear()
    }


}