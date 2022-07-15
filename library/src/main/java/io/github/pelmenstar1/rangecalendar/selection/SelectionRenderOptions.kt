package io.github.pelmenstar1.rangecalendar.selection

import io.github.pelmenstar1.rangecalendar.Fill
import io.github.pelmenstar1.rangecalendar.SelectionFillGradientBoundsType

/**
 * Contains a set of properties that might be needed to draw a selection.
 */
data class SelectionRenderOptions(
    /**
     * Fill of the selection.
     */
    val fill: Fill,

    /**
     * Specifies a way how to determine gradient bounds of the selection.
     */
    val fillGradientBoundsType: SelectionFillGradientBoundsType,

    /**
     * Round radius of the selection shape, measured in pixels.
     */
    val roundRadius: Float,

    /**
     * Specifies type of cell animation.
     */
    val cellAnimationType: CellAnimationType
)