package io.github.pelmenstar1.rangecalendar.decoration

data class IndexToCellDecor(val index: Int, val decor: CellDecor) {
    init {
        require(index >= 0) {
            "Index is negative"
        }
    }
}

infix fun Int.to(decor: CellDecor): IndexToCellDecor {
    return IndexToCellDecor(this, decor)
}