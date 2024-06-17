package com.github.pelmenstar1.rangecalendar.decoration

import android.util.SparseArray
import com.github.pelmenstar1.rangecalendar.selection.Cell

internal fun<T : Any> CellDataArray(): CellDataArray<T> {
    return CellDataArray(SparseArray())
}

@JvmInline
internal value class CellDataArray<T : Any>(private val sparseArray: SparseArray<T>) {
    internal inline fun forEachNotNull(crossinline action: (cell: Cell, value: T) -> Unit) {
        val arr = sparseArray

        for (i in 0 until arr.size()) {
            action(Cell(arr.keyAt(i)), arr.valueAt(i))
        }
    }

    operator fun get(cell: Cell): T? = sparseArray[cell.index]

    operator fun set(cell: Cell, value: T?) {
        sparseArray.put(cell.index, value)
    }

    fun clear() {
        sparseArray.clear()
    }


}