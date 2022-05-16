package io.github.pelmenstar1.rangecalendar.utils

import android.graphics.Color
import androidx.annotation.ColorInt
import kotlin.math.roundToInt

@ColorInt
internal fun Int.withAlpha(alpha: Int): Int {
    return this and 0x00FFFFFF or (alpha shl 24)
}

internal fun colorLerp(@ColorInt start: Int, @ColorInt end: Int, fraction: Float): Int {
    return Color.argb(
        lerp(Color.alpha(start), Color.alpha(end), fraction),
        lerp(Color.red(start), Color.red(end), fraction),
        lerp(Color.green(start), Color.green(end), fraction),
        lerp(Color.blue(start), Color.blue(end), fraction)
    )
}

@ColorInt
internal fun Int.darkerColor(factor: Float): Int {
    val invFactor = 1f - factor

    return Color.argb(
        Color.alpha(this),
        (Color.red(this) * invFactor).roundToInt(),
        (Color.green(this) * invFactor).roundToInt(),
        (Color.blue(this) * invFactor).roundToInt(),
    )
}