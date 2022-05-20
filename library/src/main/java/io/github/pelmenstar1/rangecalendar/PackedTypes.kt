@file:Suppress("NOTHING_TO_INLINE")

package io.github.pelmenstar1.rangecalendar

import io.github.pelmenstar1.rangecalendar.utils.float16To32
import io.github.pelmenstar1.rangecalendar.utils.float32To16

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
        get() = bits == 0L

    val isDefined: Boolean
        get() = bits != 0L

    fun contains(value: Int): Boolean {
        return value in start..endInclusive
    }

    companion object {
        val Undefined = PackedIntRange(0)
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
    val leftBits = float32To16(left).toLong() and 0xFFFF
    val topBits = float32To16(top).toLong() and 0xFFFF
    val rightBits = float32To16(right).toLong() and 0xFFFF
    val bottomBits = float32To16(bottom).toLong() and 0xFFFF

    return PackedRectF(
        (leftBits shl 48) or (topBits shl 32) or (rightBits shl 16) or bottomBits
    )
}

@JvmInline
internal value class PackedRectF(val bits: Long) {
    val left: Float
        get() = float16To32((bits shr 48).toShort())

    val top: Float
        get() = float16To32(((bits shr 32 and 0xFFFFL)).toShort())

    val right: Float
        get() = float16To32(((bits shr 16 and 0xFFFFL)).toShort())

    val bottom: Float
        get() = float16To32(((bits and 0xFFFFL)).toShort())

    val width: Float
        get() = right - left

    val height: Float
        get() = bottom - top
}

internal fun PackedRectFArray(size: Int): PackedRectFArray {
    return PackedRectFArray(LongArray(size))
}

@JvmInline
internal value class PackedRectFArray(val array: LongArray) {
    val size: Int
        get() = array.size

    inline operator fun get(index: Int) = PackedRectF(array[index])
    inline operator fun set(index: Int, value: PackedRectF) {
        array[index] = value.bits
    }
}