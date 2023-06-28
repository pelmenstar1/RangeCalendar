@file:Suppress("NOTHING_TO_INLINE")

package com.github.pelmenstar1.rangecalendar

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

internal inline fun PackedInt(value: Float) = PackedInt(value.toBits())
internal inline fun PackedInt(value: Boolean) = PackedInt(if (value) 1 else 0)
internal inline fun PackedInt(value: Enum<*>) = PackedInt(value.ordinal)

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