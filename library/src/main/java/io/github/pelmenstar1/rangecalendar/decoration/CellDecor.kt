package io.github.pelmenstar1.rangecalendar.decoration

import android.content.Context
import android.graphics.Canvas
import io.github.pelmenstar1.rangecalendar.PackedDate
import io.github.pelmenstar1.rangecalendar.selection.Cell

abstract class CellDecor {
    interface VisualState {
        val isEmpty: Boolean

        fun visual(): Visual

        interface Transitive: VisualState {
            val end: VisualState

            fun handleAnimation(
                animationFraction: Float,
                fractionInterpolator: DecorAnimationFractionInterpolator
            )
        }
    }

    enum class VisualStateChange {
        ADD,
        REMOVE,
        CELL_INFO
    }

    interface Visual {
        fun stateHandler(): VisualStateHandler
        fun renderer(): Renderer
    }

    interface VisualStateHandler {
        fun emptyState(): VisualState

        fun createState(
            context: Context,
            decorations: Array<out CellDecor>,
            start: Int, endInclusive: Int,
            info: CellInfo
        ): VisualState

        fun createTransitiveState(
            start: VisualState,
            end: VisualState,
            affectedRangeStart: Int, affectedRangeEnd: Int,
            change: VisualStateChange
        ): VisualState.Transitive
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

    abstract fun visual(): Visual
}