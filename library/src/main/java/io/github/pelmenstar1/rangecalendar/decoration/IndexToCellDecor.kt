package io.github.pelmenstar1.rangecalendar.decoration

data class IndexToCellDecor<T : CellDecor<T>>(val index: Int, val decor: CellDecor<T>) {
    init {
        require(index >= 0) {
            "Index is negative"
        }
    }
}

infix fun<T : CellDecor<T>> Int.to(decor: CellDecor<T>): IndexToCellDecor<T> {
    return IndexToCellDecor(this, decor)
}