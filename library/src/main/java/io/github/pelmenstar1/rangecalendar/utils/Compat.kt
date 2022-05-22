package io.github.pelmenstar1.rangecalendar.utils

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import io.github.pelmenstar1.rangecalendar.PackedRectF

private val tempRect: RectF? = if(Build.VERSION.SDK_INT < 21) RectF() else null

internal fun Canvas.drawRoundRectCompat(
    left: Float, top: Float, right: Float, bottom: Float,
    radius: Float,
    paint: Paint
) {
    if(Build.VERSION.SDK_INT >= 21) {
        drawRoundRect(left, top, right, bottom, radius, radius, paint)
    } else {
        val rect = tempRect!!

        rect.set(left, top, right, bottom)

        drawRoundRect(rect, radius, radius, paint)
    }
}

internal fun Canvas.drawRoundRectCompat(
    rect: PackedRectF,
    radius: Float,
    paint: Paint
) {
    drawRoundRectCompat(rect.left, rect.top, rect.right, rect.bottom, radius, paint)
}

internal fun Path.addRoundRectCompat(
    left: Float, top: Float, right: Float, bottom: Float,
    radii: FloatArray
) {
    if(Build.VERSION.SDK_INT >= 21) {
        addRoundRect(left, top, right, bottom, radii, Path.Direction.CW)
    } else {
        val rect = tempRect!!

        rect.set(left, top, right, bottom)
        addRoundRect(rect, radii, Path.Direction.CW)
    }
}

internal fun Path.addRoundRectCompat(
    left: Float, top: Float, right: Float, bottom: Float,
    radius: Float
) {
    if(Build.VERSION.SDK_INT >= 21) {
        addRoundRect(left, top, right, bottom, radius, radius, Path.Direction.CW)
    } else {
        val rect = tempRect!!

        rect.set(left, top, right, bottom)
        addRoundRect(rect, radius, radius, Path.Direction.CW)
    }
}

internal fun Path.addRectCompat(
    left: Float, top: Float, right: Float, bottom: Float,
) {
    if(Build.VERSION.SDK_INT >= 21) {
        addRect(left, top, right, bottom, Path.Direction.CW)
    } else {
        val rect = tempRect!!

        rect.set(left, top, right, bottom)
        addRect(rect, Path.Direction.CW)
    }
}

internal fun Path.addRoundRectCompat(
    rect: PackedRectF,
    radii: FloatArray
) {
    addRoundRectCompat(rect.left, rect.top, rect.right, rect.bottom, radii)
}