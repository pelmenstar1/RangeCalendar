package io.github.pelmenstar1.rangecalendar.selection

import android.graphics.Canvas
import io.github.pelmenstar1.rangecalendar.SelectionType

/**
 * Manages selection state and provides a way to draw a state and transition between them.
 */
interface SelectionManager {
    /**
     * State which was selected before [currentState].
     * If there's no previous state, it should be instance of [SelectionState] whose type is [SelectionType.NONE].
     */
    val previousState: SelectionState

    /**
     * Current state.
     */
    val currentState: SelectionState

    /**
     * Sets state whose type is [SelectionType.NONE].
     * It should be used instead of calling [setState] with [SelectionType.NONE] type because 'none' selection can't have selected range.
     */
    fun setNoneState()

    /**
     * Sets a current state.
     */
    fun setState(
        type: SelectionType,
        rangeStart: Int,
        rangeEnd: Int,
        measureManager: CellMeasureManager
    )

    /**
     * Updates previous and current state due to the configuration change ([CellMeasureManager] measurements are changed)
     */
    fun updateConfiguration(measureManager: CellMeasureManager)

    /**
     * Returns whether there is a transition between [previousState] and [currentState].
     */
    fun hasTransition(): Boolean

    /**
     * Draws current state on canvas using specified options.
     *
     * @param canvas canvas to draw current state on.
     * @param options contains essential for drawing values.
     */
    fun draw(canvas: Canvas, options: SelectionRenderOptions)

    /**
     * Draws transition between [previousState] and [currentState] on [canvas].
     * It is called only if [hasTransition] returns true.
     *
     * @param canvas a [Canvas] instance to draw transition on.
     * @param measureManager used to determine position of cells.
     * @param options contains essential for drawing values.
     * @param fraction specifies what fraction of transition should be drawn.
     * If it's 0, then result on [canvas] should be as `draw(canvas, start)` is called.
     * If it's 1, then result on [canvas] should be as `draw(canvas, end)` is called.
     */
    fun drawTransition(
        canvas: Canvas,
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions,
        fraction: Float
    )
}

internal fun SelectionManager.setState(
    type: SelectionType,
    range: CellRange,
    measureManager: CellMeasureManager
) {
    setState(type, range.start, range.end, measureManager)
}

internal fun SelectionManager.setState(
    type: SelectionType,
    rangeStart: Cell,
    rangeEnd: Cell,
    measureManager: CellMeasureManager
) {
    setState(type, rangeStart.index, rangeEnd.index, measureManager)
}