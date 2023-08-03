package com.github.pelmenstar1.rangecalendar.utils

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

internal fun Float.toIntAlpha(): Int {
    return (this * 255f + 0.5f).toInt()
}

internal fun Int.withoutAlpha(): Int {
    // clear alpha bits
    return this and 0x00FFFFFF
}

internal fun colorLerp(@ColorInt start: Int, @ColorInt end: Int, fraction: Float, alphaFraction: Float): Int {
    val startAlpha = (start ushr 24).toFloat()
    val endAlpha = (end ushr 24).toFloat()

    val a = (alphaFraction * lerp(startAlpha, endAlpha, fraction)).toInt()
    val r = lerp(start.red, end.red, fraction)
    val g = lerp(start.green, end.green, fraction)
    val b = lerp(start.blue, end.blue, fraction)

    return (a shl 24) or (r shl 16) or (g shl 8) or b
}