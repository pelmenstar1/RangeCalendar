package com.github.pelmenstar1.rangecalendar.selection

import com.github.pelmenstar1.rangecalendar.GridConstants

object Cell {
    fun create(gridX: Int, gridY: Int): Int {
        return gridY * GridConstants.COLUMN_COUNT + gridX
    }

    fun gridX(index: Int) = index % GridConstants.COLUMN_COUNT
    fun gridY(index: Int) = index / GridConstants.COLUMN_COUNT
}