@file:Suppress("NOTHING_TO_INLINE")

package io.github.pelmenstar1.rangecalendar.utils

import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.ColorLong
import androidx.annotation.RequiresApi
import androidx.core.graphics.*

@ColorInt
internal fun Int.withAlpha(alpha: Int): Int {
    return this and 0x00FFFFFF or (alpha shl 24)
}

@ColorInt
internal fun Int.withAlpha(alpha: Byte): Int {
    return withAlpha(alpha.toInt() and 0xFF)
}

@ColorInt
internal fun Int.withAlpha(alpha: Float): Int {
    return withAlpha((alpha * 255f + 0.5f).toInt())
}

@ColorLong
internal fun Long.withAlpha(alpha: Float): Long {
    return if ((this and 0x3fL) == 0L) {
        (this and 0x00FFFFFF_FFFFFFFF) or ((alpha * 255f + 0.5f).toLong() shl 56)
    } else {
        (this and (-65473L)) or ((alpha * 1023f + 0.5f).toLong() shl 6)
    }
}

internal inline val Long.colorSpaceIdRaw: Long
    get() = this and 0x3fL

/**
 * Compat version of [Color.pack]
 */
internal fun Int.packColorCompat(): Long {
    return (this.toLong() and 0xFFFFFFFFL) shl 32
}

/**
 * It's unsafe because color long must be sRGB already, otherwise it returns wrong result.
 */
internal inline fun Long.asSrgbUnsafe(): Int {
    return (this shr 32).toInt()
}

@ColorInt
internal fun Int.withRGB(r: Int, g: Int, b: Int): Int {
    return this and 0xFF000000L.toInt() or (r shl 16) or (g shl 8) or b
}

internal fun colorLerp(@ColorInt start: Int, @ColorInt end: Int, fraction: Float): Int {
    return Color.argb(
        lerp(start.alpha, end.alpha, fraction),
        lerp(start.red, end.red, fraction),
        lerp(start.green, end.green, fraction),
        lerp(start.blue, end.blue, fraction)
    )
}

@ColorInt
internal fun Int.darkerColor(factor: Float): Int {
    val invFactor = 1f - factor

    return withRGB(
        (red * invFactor + 0.5f).toInt(),
        (green * invFactor + 0.5f).toInt(),
        (blue * invFactor + 0.5f).toInt()
    )
}

@RequiresApi(26)
internal fun Paint.setColorLongFast(@ColorLong color: Long) {
    if(Build.VERSION.SDK_INT >= 29) {
        if(color.isSrgb) {
            setColor(color.asSrgbUnsafe())
        } else {
            setColor(color)
        }
    } else {
        setColor(Color.toArgb(color))
    }
}