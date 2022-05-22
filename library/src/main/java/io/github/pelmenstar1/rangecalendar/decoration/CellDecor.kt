package io.github.pelmenstar1.rangecalendar.decoration

import android.content.Context
import android.graphics.Canvas
import android.os.Parcelable
import io.github.pelmenstar1.rangecalendar.PackedDate
import io.github.pelmenstar1.rangecalendar.selection.Cell

abstract class CellDecor {
    interface VisualState {
        fun handleAnimation(
            start: Int,
            endInclusive: Int,
            animationFraction: Float,
            fractionInterpolator: DecorAnimationFractionInterpolator,
        )
    }

    interface StateHandler {
        fun createState(
            context: Context,
            decorations: Array<out CellDecor>,
            start: Int, endInclusive: Int,
            info: CellInfo
        ): VisualState
    }

    interface Renderer {
        fun renderState(canvas: Canvas, state: VisualState)
    }

    // Index of decor in grid.
    @get:JvmSynthetic
    @set:JvmSynthetic
    internal var cell = Cell.Undefined

    // Exact date of decoration
    @get:JvmSynthetic
    @set:JvmSynthetic
    internal var date = PackedDate(0)

    val year: Int
        get() = date.year

    val month: Int
        get() = date.month

    val dayOfMonth: Int
        get() = date.dayOfMonth

    abstract fun stateHandler(): StateHandler
    abstract fun renderer(): Renderer
}