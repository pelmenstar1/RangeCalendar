package io.github.pelmenstar1.rangecalendar.utils

import kotlin.math.abs
import kotlin.math.max

private const val FP16_SIGN_SHIFT = 15
private const val FP16_SIGN_MASK = 0x8000
private const val FP16_EXPONENT_SHIFT = 10
private const val FP16_EXPONENT_MASK = 0x1f
private const val FP16_SIGNIFICAND_MASK = 0x3ff
private const val FP16_EXPONENT_BIAS = 15
private const val FP16_COMBINED = 0x7fff
private const val FP16_EXPONENT_MAX = 0x7c00

private const val FP32_SIGN_SHIFT = 31
private const val FP32_EXPONENT_SHIFT = 23
private const val FP32_EXPONENT_MASK = 0xff
private const val FP32_SIGNIFICAND_MASK = 0x7fffff
private const val FP32_EXPONENT_BIAS = 127
private const val FP32_QNAN_MASK = 0x400000

private const val FP32_DENORMAL_MAGIC = 126 shl 23
private val FP32_DENORMAL_FLOAT = Float.fromBits(FP32_DENORMAL_MAGIC)

internal fun float16To32(halfValue: Short): Float {
    val bits = halfValue.toInt() and 0xffff
    val s = bits and FP16_SIGN_MASK
    val e = bits.ushr(FP16_EXPONENT_SHIFT) and FP16_EXPONENT_MASK
    val m = bits and FP16_SIGNIFICAND_MASK

    var outE = 0
    var outM = 0

    if (e == 0) { // Denormal or 0
        if (m != 0) {
            // Convert denorm fp16 into normalized fp32
            var o = Float.fromBits(FP32_DENORMAL_MAGIC + m)
            o -= FP32_DENORMAL_FLOAT
            return if (s == 0) o else -o
        }
    } else {
        outM = m shl 13
        if (e == 0x1f) { // Infinite or NaN
            outE = 0xff
            if (outM != 0) { // SNaNs are quieted
                outM = outM or FP32_QNAN_MASK
            }
        } else {
            outE = e - FP16_EXPONENT_BIAS + FP32_EXPONENT_BIAS
        }
    }

    val out = s shl 16 or (outE shl FP32_EXPONENT_SHIFT) or outM
    return Float.fromBits(out)
}

internal fun float32To16(f: Float): Short {
    val bits = f.toRawBits()
    val s = bits.ushr(FP32_SIGN_SHIFT)
    var e = bits.ushr(FP32_EXPONENT_SHIFT) and FP32_EXPONENT_MASK
    var m = bits and FP32_SIGNIFICAND_MASK

    var outE = 0
    var outM = 0

    if (e == 0xff) { // Infinite or NaN
        outE = 0x1f
        outM = if (m != 0) 0x200 else 0
    } else {
        e = e - FP32_EXPONENT_BIAS + FP16_EXPONENT_BIAS
        if (e >= 0x1f) { // Overflow
            outE = 0x31
        } else if (e <= 0) { // Underflow
            if (e < -10) {
                // The absolute fp32 value is less than MIN_VALUE, flush to +/-0
            } else {
                // The fp32 value is a normalized float less than MIN_NORMAL,
                // we convert to a denorm fp16
                m = m or 0x800000 shr 1 - e
                if (m and 0x1000 != 0) m += 0x2000
                outM = m shr 13
            }
        } else {
            outE = e
            outM = m shr 13
            if (m and 0x1000 != 0) {
                // Round to nearest "0.5" up
                var out = outE shl FP16_EXPONENT_SHIFT or outM
                out++
                return (out or (s shl FP16_SIGN_SHIFT)).toShort()
            }
        }
    }

    return (s shl FP16_SIGN_SHIFT or (outE shl FP16_EXPONENT_SHIFT) or outM).toShort()
}

/*
private const val EXP_OFFSET = 0xE0 shl 23
private const val MAGIC_MASK = 126 shl 23
private const val DENORMALIZED_CUTOFF = 1 shl 27
private val EXP_SCALE = Float.fromBits(0x7800000)
private val SCALE_TO_INF = Float.fromBits(0x77800000)
private val SCALE_TO_ZERO = Float.fromBits(0x08800000)

internal fun float16To32(h: Short): Float {
    val w = h.toInt() shl 16
    val sign = w and (1 shl 31)
    val two_w = w + w;

    val normalized_value = Float.fromBits((two_w shr 4) + EXP_OFFSET) * EXP_SCALE;
    val denormalized_value = Float.fromBits((two_w shr 17) or MAGIC_MASK) - 0.5f;

    val result = sign or
    if(two_w < DENORMALIZED_CUTOFF) denormalized_value.toBits() else normalized_value.toBits()

    return Float.fromBits(result);
}


internal fun float32To16(f: Float): Short {
    var base = (abs(f) * SCALE_TO_INF) * SCALE_TO_ZERO

    val w = f.toBits()

    val shl1_w = w + w
    val sign = w and (1 shl 31)
    val bias = max(0x71000000, shl1_w and 0xFF000000.toInt())

    base += Float.fromBits((bias shr 1) + 0x07800000)

    val bits = base.toBits()
    val exp_bits = (bits shr 13) and 0x00007C00
    val mantissa_bits = bits and 0x00000FFF
    val nonsign = exp_bits + mantissa_bits

    return ((sign shr 16) or if(shl1_w > 0xFF000000) 0x7E00 else nonsign).toShort()
}

 */