package com.github.pelmenstar1.rangecalendar.utils

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.os.Build
import androidx.core.graphics.withSave

private val tempRect: RectF? = if (Build.VERSION.SDK_INT < 21) RectF() else null

internal fun Canvas.drawRoundRectCompat(
    left: Float, top: Float, right: Float, bottom: Float,
    radius: Float,
    paint: Paint
) {
    if (Build.VERSION.SDK_INT >= 21) {
        drawRoundRect(left, top, right, bottom, radius, radius, paint)
    } else {
        val rect = tempRect!!

        rect.set(left, top, right, bottom)
        drawRoundRect(rect, radius, radius, paint)
    }
}

@Suppress("DEPRECATION")
internal fun Canvas.clipOutPathCompat(path: Path) {
    clipPath(path, Region.Op.DIFFERENCE)
}

internal inline fun Canvas.withClipOut(path: Path, block: Canvas.() -> Unit) {
    withSave {
        clipOutPathCompat(path)
        block()
    }
}

internal fun Path.addRoundRectCompat(
    left: Float, top: Float, right: Float, bottom: Float,
    roundRadius: Float
) {
    if (Build.VERSION.SDK_INT >= 21) {
        addRoundRect(left, top, right, bottom, roundRadius, roundRadius, Path.Direction.CW)
    } else {
        val rect = tempRect!!

        rect.set(left, top, right, bottom)
        addRoundRect(rect, roundRadius, roundRadius, Path.Direction.CW)
    }
}

internal fun Path.addRoundRectCompat(
    left: Float, top: Float, right: Float, bottom: Float,
    radii: FloatArray
) {
    if (Build.VERSION.SDK_INT >= 21) {
        addRoundRect(left, top, right, bottom, radii, Path.Direction.CW)
    } else {
        val rect = tempRect!!

        rect.set(left, top, right, bottom)
        addRoundRect(rect, radii, Path.Direction.CW)
    }
}