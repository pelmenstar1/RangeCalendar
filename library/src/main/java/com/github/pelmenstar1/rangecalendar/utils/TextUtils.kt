package com.github.pelmenstar1.rangecalendar.utils

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import com.github.pelmenstar1.rangecalendar.PackedSize
import com.github.pelmenstar1.rangecalendar.PackedSizeArray

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

internal fun getTextBoundsArray(
    texts: Array<String>,
    textSize: Float,
    typeface: Typeface? = null
): PackedSizeArray {
    val bitsArray = LongArray(texts.size)
    getTextBoundsArray(texts, 0, texts.size, textSize, typeface) { i, size ->
        bitsArray[i] = size.bits
    }

    return PackedSizeArray(bitsArray)
}

internal inline fun getTextBoundsArray(
    texts: Array<String>,
    start: Int,
    end: Int,
    textSize: Float,
    typeface: Typeface?,
    block: (index: Int, PackedSize) -> Unit
) {
    initTempPaint(textSize, typeface)

    tempPaint.getTextBoundsArray(texts, start, end, block)
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

internal inline fun Paint.getTextBoundsArray(
    texts: Array<String>,
    start: Int,
    end: Int,
    block: (index: Int, PackedSize) -> Unit
) {
    for (i in start until end) {
        block(i - start, getTextBounds(texts[i]))
    }
}

private fun packTextBoundsCache(): PackedSize {
    val bounds = textBoundsCache

    return PackedSize(bounds.width(), bounds.height())
}