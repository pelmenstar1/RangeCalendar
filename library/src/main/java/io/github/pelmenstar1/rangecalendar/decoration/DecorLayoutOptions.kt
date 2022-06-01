package io.github.pelmenstar1.rangecalendar.decoration

import io.github.pelmenstar1.rangecalendar.HorizontalAlignment
import io.github.pelmenstar1.rangecalendar.PackedDate
import io.github.pelmenstar1.rangecalendar.Padding
import io.github.pelmenstar1.rangecalendar.VerticalAlignment
import io.github.pelmenstar1.rangecalendar.selection.Cell

/**
 * Contains some options that can be applied to the cell for customization.
 */
data class DecorLayoutOptions(
    /**
     * All the decorations in the cell create imaginary area, called decoration block.
     * That's the padding of such block.
     */
    val padding: Padding?,

    /**
     * Horizontal alignment of all the decoration in the cell.
     */
    val horizontalAlignment: HorizontalAlignment,

    /**
     * Vertical alignment of all the decorations in the cell.
     */
    val verticalAlignment: VerticalAlignment
)