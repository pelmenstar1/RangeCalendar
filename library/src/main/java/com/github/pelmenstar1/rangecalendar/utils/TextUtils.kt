package com.github.pelmenstar1.rangecalendar.utils

import android.graphics.Paint
import android.graphics.Rect
import com.github.pelmenstar1.rangecalendar.PackedSize

@JvmField
internal val tempTextBounds = Rect()

internal fun Paint.getTextBounds(text: String): PackedSize {
    val bounds = tempTextBounds
    getTextBounds(text, 0, text.length, bounds)

    return PackedSize(bounds.width(), bounds.height())
}

internal inline fun Paint.getTextBoundsArray(
    texts: Array<out String>,
    block: (index: Int, width: Int, height: Int) -> Unit
) {
    val bounds = tempTextBounds

    for ((i, text) in texts.withIndex()) {
        getTextBounds(text, 0, text.length, bounds)

        block(i, bounds.width(), bounds.height())
    }
}