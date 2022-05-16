package io.github.pelmenstar1.rangecalendar.decoration

import android.graphics.Canvas

interface CellDecorRenderer<T : CellDecor<T>> {
    fun decorationClass(): Class<T>

    fun render(
        canvas: Canvas,
        decorations: Array<out CellDecor<*>>,
        start: Int, end: Int,
        info: CellInfo,
    )
}