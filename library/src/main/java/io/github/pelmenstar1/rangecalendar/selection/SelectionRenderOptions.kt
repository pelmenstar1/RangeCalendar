package io.github.pelmenstar1.rangecalendar.selection

import io.github.pelmenstar1.rangecalendar.Fill
import io.github.pelmenstar1.rangecalendar.SelectionFillGradientBoundsType

data class SelectionRenderOptions(
    val fill: Fill,
    val fillGradientBoundsType: SelectionFillGradientBoundsType,
    val roundRadius: Float,
    val cellSize: Float,
)