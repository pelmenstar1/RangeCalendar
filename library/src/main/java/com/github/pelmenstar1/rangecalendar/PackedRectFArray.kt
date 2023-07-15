package com.github.pelmenstar1.rangecalendar

import android.graphics.RectF
import com.github.pelmenstar1.rangecalendar.utils.lerp

internal fun PackedRectFArray(size: Int): PackedRectFArray {
    return PackedRectFArray(FloatArray(size * 4))
}

@JvmInline
internal value class PackedRectFArray(val array: FloatArray) {
    val isEmpty: Boolean
        get() = array.isEmpty()

    val size: Int
        get() = array.size / 4

    private fun getComponent(index: Int, offset: Int) = array[index * 4 + offset]

    private fun setComponent(index: Int, offset: Int, value: Float) {
        array[index * 4 + offset] = value
    }

    fun getLeft(index: Int) = getComponent(index, LEFT_OFFSET)
    fun getTop(index: Int) = getComponent(index, TOP_OFFSET)
    fun getRight(index: Int) = getComponent(index, RIGHT_OFFSET)
    fun getBottom(index: Int) = getComponent(index, BOTTOM_OFFSET)

    fun setLeft(index: Int, value: Float) = setComponent(index, LEFT_OFFSET, value)
    fun setTop(index: Int, value: Float) = setComponent(index, TOP_OFFSET, value)
    fun setRight(index: Int, value: Float) = setComponent(index, RIGHT_OFFSET, value)
    fun setBottom(index: Int, value: Float) = setComponent(index, BOTTOM_OFFSET, value)

    fun set(index: Int, left: Float, top: Float, right: Float, bottom: Float) {
        val absIndex = index * 4

        array[absIndex + LEFT_OFFSET] = left
        array[absIndex + TOP_OFFSET] = top
        array[absIndex + RIGHT_OFFSET] = right
        array[absIndex + BOTTOM_OFFSET] = bottom
    }

    fun setFromObjectRect(index: Int, rect: RectF) {
        set(index, rect.left, rect.top, rect.right, rect.bottom)
    }

    fun toObjectRect(index: Int, outRect: RectF) {
        val absIndex = index * 4

        outRect.left = array[absIndex + LEFT_OFFSET]
        outRect.top = array[absIndex + TOP_OFFSET]
        outRect.right = array[absIndex + RIGHT_OFFSET]
        outRect.bottom = array[absIndex + BOTTOM_OFFSET]
    }

    fun copyOf(): PackedRectFArray {
        return PackedRectFArray(array.copyOf())
    }

    companion object {
        const val LEFT_OFFSET = 0
        const val TOP_OFFSET = 1
        const val RIGHT_OFFSET = 2
        const val BOTTOM_OFFSET = 3

        fun lerpArray(
            start: PackedRectFArray, end: PackedRectFArray, out: PackedRectFArray,
            startIndex: Int, endIndexExclusive: Int,
            fraction: Float,
            startOffset: Int = 0, endOffset: Int = 0
        ) {
            val absStartOff = startOffset * 4
            val absEndOff = endOffset * 4

            for (i in (startIndex * 4) until (endIndexExclusive * 4)) {
                out.array[i] = lerp(start.array[i - absStartOff], end.array[i - absEndOff], fraction)
            }
        }
    }
}