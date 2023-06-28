package com.github.pelmenstar1.rangecalendar.utils

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red

@ColorInt
internal fun Int.withAlpha(
    @IntRange(from = 0, to = 255) alpha: Int
): Int {
    return this and 0x00FFFFFF or (alpha shl 24)
}

@ColorInt
internal fun Int.withAlpha(alpha: Byte): Int {
    return withAlpha(alpha.toInt() and 0xFF)
}

@ColorInt
internal fun Int.withCombinedAlpha(
    @FloatRange(from = 0.0, to = 1.0) newAlpha: Float,
    @IntRange(from = 0, to = 255) originAlpha: Int = alpha
): Int {
    return withAlpha((originAlpha * newAlpha + 0.5f).toInt())
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
internal fun Int.darkerColor(@FloatRange(from = 0.0, to = 1.0) factor: Float): Int {
    val invFactor = 1f - factor

    return withRGB(
        (red * invFactor + 0.5f).toInt(),
        (green * invFactor + 0.5f).toInt(),
        (blue * invFactor + 0.5f).toInt()
    )
}