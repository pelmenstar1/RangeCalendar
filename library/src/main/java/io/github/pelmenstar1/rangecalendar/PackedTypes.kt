@file:Suppress("NOTHING_TO_INLINE")

package io.github.pelmenstar1.rangecalendar

import android.graphics.RectF
import io.github.pelmenstar1.rangecalendar.utils.float16To32
import io.github.pelmenstar1.rangecalendar.utils.float32To16
import io.github.pelmenstar1.rangecalendar.utils.lerp

internal fun packInts(first: Int, second: Int): Long {
    return second.toLong() shl 32 or (first.toLong() and 0xffffffffL)
}

internal fun unpackFirstInt(pair: Long): Int = pair.toInt()
internal fun unpackSecondInt(pair: Long): Int = (pair shr 32).toInt()

internal fun packShorts(first: Int, second: Int): Int {
    return second shl 16 or first
}

internal fun unpackFirstShort(packed: Int): Int = packed and 0xFFFF
internal fun unpackSecondShort(packed: Int): Int = packed shr 16

internal fun PackedIntPair(first: Int, second: Int): PackedIntPair {
    return PackedIntPair(packInts(first, second))
}

@JvmInline
internal value class PackedIntPair(val bits: Long) {
    val first: Int
        get() = unpackFirstInt(bits)

    val second: Int
        get() = unpackSecondInt(bits)
}

internal fun PackedIntRange(start: Int, endInclusive: Int): PackedIntRange {
    return PackedIntRange(packInts(start, endInclusive))
}

@JvmInline
internal value class PackedIntRange(val bits: Long) {
    val start: Int
        get() = unpackFirstInt(bits)

    val endInclusive: Int
        get() = unpackSecondInt(bits)

    val isUndefined: Boolean
        get() = this == Undefined

    val isDefined: Boolean
        get() = this != Undefined

    fun contains(value: Int): Boolean {
        return value in start..endInclusive
    }

    companion object {
        val Undefined = PackedIntRange(-1, -1)
    }
}

internal fun PackedShortRange(start: Int, endInclusive: Int): PackedShortRange {
    return PackedShortRange(packShorts(start, endInclusive))
}

@JvmInline
internal value class PackedShortRange(val bits: Int) {
    val start: Int
        get() = unpackFirstShort(bits)

    val endInclusive: Int
        get() = unpackSecondShort(bits)
}

internal fun PackedSize(width: Int, height: Int): PackedSize {
    return PackedSize(packInts(width, height))
}

@JvmInline
internal value class PackedSize(val bits: Long) {
    val width: Int
        get() = unpackFirstInt(bits)

    val height: Int
        get() = unpackSecondInt(bits)
}

internal fun PackedRectF(left: Float, top: Float, right: Float, bottom: Float): PackedRectF {
    return packRectF(float32To16(left), float32To16(top), float32To16(right), float32To16(bottom))
}

internal fun PackedRectF(rect: RectF): PackedRectF {
    return PackedRectF(rect.left, rect.top, rect.right, rect.bottom)
}

internal fun packRectF(leftBits: Short, topBits: Short, rightBits: Short, bottomBits: Short): PackedRectF {
    val left = leftBits.toLong() and 0xFFFF
    val top = topBits.toLong() and 0xFFFF
    val right = rightBits.toLong() and 0xFFFF
    val bottom = bottomBits.toLong() and 0xFFFF

    return PackedRectF(
        (left shl 48) or (top shl 32) or (right shl 16) or bottom
    )
}

@JvmInline
internal value class PackedRectF(val bits: Long) {
    val left: Float
        get() = float16To32(getValueBits(48))

    val top: Float
        get() = float16To32(getValueBits(32))

    val right: Float
        get() = float16To32(getValueBits(16))

    val bottom: Float
        get() = float16To32(getValueBits(0))

    val width: Float
        get() = right - left

    val height: Float
        get() = bottom - top

    fun getValueBits(bitOffset: Int): Short {
        return (bits shr bitOffset and 0xFFFFL).toShort()
    }

    fun setTo(outRect: RectF) {
        outRect.set(left, top, right, bottom)
    }

    fun withLeftAndRight(left: Float, right: Float): PackedRectF {
        val leftBits = float32To16(left).toLong()
        val rightBits = float32To16(right).toLong()

        return PackedRectF(
            (bits and 0x0000FFFF0000FFFFL) or
                    (leftBits shl 48) or
                    (rightBits shl 16)
        )
    }

    companion object {

    }
}

private fun lerpRectComponent(start: PackedRectF, end: PackedRectF, fraction: Float, bitOffset: Int): Short {
    val startComponent = start.getValueBits(bitOffset)
    val endComponent = end.getValueBits(bitOffset)

    if(startComponent == endComponent) {
        return startComponent
    }

    return float32To16(lerp(float16To32(startComponent), float16To32(endComponent), fraction))
}

internal fun lerp(start: PackedRectF, end: PackedRectF, fraction: Float): PackedRectF {
    if(start == end) {
        return start
    }

    return packRectF(
        lerpRectComponent(start, end, fraction, 48),
        lerpRectComponent(start, end, fraction, 32),
        lerpRectComponent(start, end, fraction, 16),
        lerpRectComponent(start, end, fraction, 0),
    )
}


internal fun PackedRectFArray(size: Int): PackedRectFArray {
    return PackedRectFArray(LongArray(size))
}

@JvmInline
internal value class PackedRectFArray(val array: LongArray) {
    inline val size: Int
        get() = array.size

    inline val isEmpty: Boolean
        get() = array.isEmpty()

    inline operator fun get(index: Int) = PackedRectF(array[index])
    inline operator fun set(index: Int, value: PackedRectF) {
        array[index] = value.bits
    }

    inline fun copyOf(): PackedRectFArray {
        return PackedRectFArray(array.copyOf())
    }

    fun copyToWithoutRange(outArray: PackedRectFArray, start: Int, endInclusive: Int) {
        System.arraycopy(array, 0, outArray.array, 0, start)

        val pos = endInclusive + 1

        System.arraycopy(array, pos, outArray.array, pos, outArray.size - pos)
    }
}