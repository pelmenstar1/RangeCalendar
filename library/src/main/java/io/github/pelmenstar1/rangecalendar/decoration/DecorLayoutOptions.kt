package io.github.pelmenstar1.rangecalendar.decoration

import io.github.pelmenstar1.rangecalendar.HorizontalAlignment
import io.github.pelmenstar1.rangecalendar.PackedDate
import io.github.pelmenstar1.rangecalendar.Padding
import io.github.pelmenstar1.rangecalendar.VerticalAlignment
import io.github.pelmenstar1.rangecalendar.selection.Cell

data class DecorLayoutOptions(
    val padding: Padding?,
    val horizontalAlignment: HorizontalAlignment,
    val verticalAlignment: VerticalAlignment
)