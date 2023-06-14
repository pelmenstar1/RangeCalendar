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

internal fun Paint.getTextBounds(text: String): PackedSize {
    val bounds = textBoundsCache
    getTextBounds(text, 0, text.length, bounds)

    return PackedSize(bounds.width(), bounds.height())
}

internal fun getTextBoundsArray(
    texts: Array<out String>,
    textSize: Float,
    typeface: Typeface? = null
): PackedSizeArray {
    val array = PackedSizeArray(texts.size)
    getTextBoundsArray(texts, 0, texts.size, textSize, typeface) { i, width, height ->
        array[i] = PackedSize(width, height)
    }

    return array
}

internal inline fun getTextBoundsArray(
    texts: Array<out String>,
    start: Int,
    end: Int,
    textSize: Float,
    typeface: Typeface?,
    block: (index: Int, width: Int, height: Int) -> Unit
) {
    initTempPaint(textSize, typeface)

    tempPaint.getTextBoundsArray(texts, start, end, block)
}

internal inline fun Paint.getTextBoundsArray(
    texts: Array<out String>,
    start: Int,
    end: Int,
    block: (index: Int, width: Int, height: Int) -> Unit
) {
    val bounds = textBoundsCache

    for (i in start until end) {
        val element = texts[i]
        getTextBounds(element, 0, element.length, bounds)

        block(i - start, bounds.width(), bounds.height())
    }
}