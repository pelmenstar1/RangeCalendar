package com.github.pelmenstar1.rangecalendar.utils

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Region
import androidx.core.graphics.withSave

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