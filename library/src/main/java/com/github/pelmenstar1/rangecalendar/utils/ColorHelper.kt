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
internal fun Int.withAlpha(@IntRange(from = 0, to = 255) alpha: Int): Int {
    return (this and 0x00FFFFFF) or (alpha shl 24)
}

@ColorInt
internal fun Int.withCombinedAlpha(@FloatRange(from = 0.0, to = 1.0) newAlpha: Float): Int {
    return withAlpha((alpha * newAlpha + 0.5f).toInt())
}

internal fun Int.withoutAlpha(): Int {
    // clear alpha bits
    return this and 0x00FFFFFF
}

internal fun colorLerp(@ColorInt start: Int, @ColorInt end: Int, fraction: Float): Int {
    return Color.argb(
        lerp(start.alpha, end.alpha, fraction),
        lerp(start.red, end.red, fraction),
        lerp(start.green, end.green, fraction),
        lerp(start.blue, end.blue, fraction)
    )
}