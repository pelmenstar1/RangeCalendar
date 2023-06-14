package com.github.pelmenstar1.rangecalendar.utils

import android.graphics.RectF

internal const val RECT_ARRAY_LEFT = 0
internal const val RECT_ARRAY_TOP = 1
internal const val RECT_ARRAY_RIGHT = 2
internal const val RECT_ARRAY_BOTTOM = 3

internal fun setRectFromValues(array: FloatArray, absIndex: Int, left: Float, top: Float, right: Float, bottom: Float) {
    array[absIndex + RECT_ARRAY_LEFT] = left
    array[absIndex + RECT_ARRAY_TOP] = top
    array[absIndex + RECT_ARRAY_RIGHT] = right
    array[absIndex + RECT_ARRAY_BOTTOM] = bottom
}

internal fun setRectFromObject(array: FloatArray, absIndex: Int, rect: RectF) {
    setRectFromValues(array, absIndex, rect.left, rect.top, rect.right, rect.bottom)
}

internal fun arrayRectToObject(array: FloatArray, absIndex: Int, outRectF: RectF) {
    outRectF.left = array[absIndex + RECT_ARRAY_LEFT]
    outRectF.top = array[absIndex + RECT_ARRAY_TOP]
    outRectF.right = array[absIndex + RECT_ARRAY_RIGHT]
    outRectF.bottom = array[absIndex + RECT_ARRAY_BOTTOM]
}