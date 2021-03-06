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

    override fun toString(): String {
        return "$start..$endInclusive"
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

    inline operator fun component1() = width
    inline operator fun component2() = height

    override fun toString(): String {
        return "PackedSize(width=$width, height=$height)"
    }
}

internal inline fun PackedSizeArray(size: Int): PackedSizeArray {
    return PackedSizeArray(LongArray(size))
}

@JvmInline
internal value class PackedSizeArray(val array: LongArray) {
    inline val size: Int
        get() = array.size

    inline operator fun get(index: Int) = PackedSize(array[index])

    inline operator fun set(index: Int, value: PackedSize) {
        array[index] = value.bits
    }
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

    inline operator fun component1() = left
    inline operator fun component2() = top
    inline operator fun component3() = right
    inline operator fun component4() = bottom

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

    override fun toString(): String {
        return "PackedRectF(left=$left, top=$top, right=$right, bottom=$bottom)"
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

internal fun lerpRectArray(
    start: PackedRectFArray, end: PackedRectFArray, outArray: PackedRectFArray,
    startIndex: Int, endIndexInclusive: Int,
    fraction: Float,
    startOffset: Int = 0, endOffset: Int = 0
) {
    for(i in startIndex..endIndexInclusive) {
        outArray[i] = lerp(
            start[i - startOffset],
            end[i - endOffset],
            fraction
        )
    }
}

internal fun PackedPointF(x: Float, y: Float): PackedPointF {
    return PackedPointF(packInts(x.toBits(), y.toBits()))
}

@JvmInline
internal value class PackedPointF(val bits: Long) {
    inline val x: Float
        get() = Float.fromBits(unpackFirstInt(bits))

    inline val y: Float
        get() = Float.fromBits(unpackSecondInt(bits))

    inline operator fun component1() = x
    inline operator fun component2() = y

    override fun toString(): String {
        return "PackedPointF(x=$x, y=$y)"
    }
}

internal fun lerp(start: PackedPointF, end: PackedPointF, fraction: Float): PackedPointF {
    return PackedPointF(lerp(start.x, end.x, fraction), lerp(start.y, end.y, fraction))
}

internal fun PackedPointFArray(size: Int): PackedPointFArray {
    return PackedPointFArray(LongArray(size))
}

@JvmInline
internal value class PackedPointFArray(val array: LongArray) {
    inline val size: Int
        get() = array.size

    inline val isEmpty: Boolean
        get() = array.isEmpty()

    inline operator fun get(index: Int) = PackedPointF(array[index])
    inline operator fun set(index: Int, value: PackedPointF) {
        array[index] = value.bits
    }

    inline fun copyOf(): PackedPointFArray {
        return PackedPointFArray(array.copyOf())
    }

    fun copyToWithoutRange(outArray: PackedPointFArray, start: Int, endInclusive: Int) {
        System.arraycopy(array, 0, outArray.array, 0, start)

        val pos = endInclusive + 1

        System.arraycopy(array, pos, outArray.array, pos, outArray.size - pos)
    }
}

internal inline fun PackedInt(value: Float) = PackedInt(value.toBits())
internal inline fun PackedInt(value: Boolean) = PackedInt(if(value) 1 else 0)
internal inline fun <T : Enum<T>> PackedInt(value: T) = PackedInt(value.ordinal)

@JvmInline
internal value class PackedInt(val value: Int) {
    inline fun float() = Float.fromBits(value)
    inline fun boolean() = value == 1
    inline fun <T : Enum<T>> enum(fromInt: (Int) -> T) = fromInt(value)
}

@Suppress("UNCHECKED_CAST")
@JvmInline
internal value class PackedObject(private val value: Any?) {
    inline fun <T> value() = value as T
}