package io.github.pelmenstar1.rangecalendar

import android.graphics.Paint
import android.graphics.Rect
import io.github.pelmenstar1.rangecalendar.PackedSize

private val textBoundsCache = Rect()
private val tempPaint = Paint()

fun getTextBounds(text: String, textSize: Float): Long {
    tempPaint.textSize = textSize

    return tempPaint.getTextBounds(text)
}

fun getTextBounds(text: CharArray, offset: Int, length: Int, textSize: Float): Long {
    tempPaint.textSize = textSize

    return tempPaint.getTextBounds(text, offset, length)
}

fun Paint.getTextBounds(text: CharArray, index: Int, length: Int): Long {
    getTextBounds(text, index, length, textBoundsCache)

    return packTextBoundsCache()
}

fun Paint.getTextBounds(text: String): Long {
    getTextBounds(text, 0, text.length, textBoundsCache)

    return packTextBoundsCache()
}

private fun packTextBoundsCache(): Long {
    val bounds = textBoundsCache

    return PackedSize.create(bounds.width(), bounds.height())
}