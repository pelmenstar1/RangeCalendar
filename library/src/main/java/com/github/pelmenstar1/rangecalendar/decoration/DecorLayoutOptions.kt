package com.github.pelmenstar1.rangecalendar.decoration

import com.github.pelmenstar1.rangecalendar.HorizontalAlignment
import com.github.pelmenstar1.rangecalendar.Padding
import com.github.pelmenstar1.rangecalendar.VerticalAlignment

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