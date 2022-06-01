package io.github.pelmenstar1.rangecalendar.utils

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import io.github.pelmenstar1.rangecalendar.PackedSize

private val textBoundsCache = Rect()
private val tempPaint = Paint()

private fun initTempPaint(textSize: Float, typeface: Typeface?) {
    tempPaint.apply {
        this.textSize = textSize
        this.typeface = typeface
    }
}

internal fun getTextBounds(text: String, textSize: Float, typeface: Typeface? = null): PackedSize {
    initTempPaint(textSize, typeface)

    return tempPaint.getTextBounds(text)
}

internal fun getTextBounds(
    text: CharArray, offset: Int, length: Int, textSize: Float, typeface: Typeface? = null
): PackedSize {
    initTempPaint(textSize, typeface)

    return tempPaint.getTextBounds(text, offset, length)
}

internal fun Paint.getTextBounds(text: CharArray, index: Int, length: Int): PackedSize {
    getTextBounds(text, index, length, textBoundsCache)

    return packTextBoundsCache()
}

internal fun Paint.getTextBounds(text: String): PackedSize {
    getTextBounds(text, 0, text.length, textBoundsCache)

    return packTextBoundsCache()
}

private fun packTextBoundsCache(): PackedSize {
    val bounds = textBoundsCache

    return PackedSize(bounds.width(), bounds.height())
}