package io.github.pelmenstar1.rangecalendar.selection

import android.graphics.Canvas
import io.github.pelmenstar1.rangecalendar.SelectionType

interface SelectionManager<out TState : SelectionState> {
    val previousState: TState
    val currentState: TState

    fun setNoneState()

    fun setState(
        type: SelectionType,
        rangeStart: Int,
        rangeEnd: Int,
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions
    )

    /**
     * Returns whether [start] state can gradually become [end] state.
     * In other words, it returns whether [start] state can be transitioned to [end].
     *
     * Note that, if [start] has transition with [end],
     * it doesn't necessarily means that [end] has transition with [start].
     */
    fun hasTransition(): Boolean

    /**
     * Draws specified [state] on [canvas].
     */
    fun draw(canvas: Canvas, measureManager: CellMeasureManager, options: SelectionRenderOptions, alpha: Float)

    /**
     * Draws transition between [start] and [end] on [canvas].
     *
     * @param canvas a [Canvas] instance to draw transition on.
     * @param start start state of the transition.
     * @param end end state of the transition.
     * @param fraction specifies what fraction of transition should be drawn.
     * If it's 0, then result on [canvas] should be as `draw(canvas, start)` is called.
     * If it's 1, then result on [canvas] should be as `draw(canvas, end)` is called.
     */
    fun drawTransition(
        canvas: Canvas,
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions,
        fraction: Float,
    )
}

internal fun <TState : SelectionState> SelectionManager<TState>.setState(
    type: SelectionType,
    range: CellRange,
    measureManager: CellMeasureManager,
    options: SelectionRenderOptions
) {
    setState(type, range.start, range.end, measureManager, options)
}

internal fun <TState : SelectionState> SelectionManager<TState>.setState(
    type: SelectionType,
    rangeStart: Cell,
    rangeEnd: Cell,
    measureManager: CellMeasureManager,
    options: SelectionRenderOptions
) {
    setState(type, rangeStart.index, rangeEnd.index, measureManager, options)
}