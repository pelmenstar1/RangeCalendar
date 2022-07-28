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
     * Creates a transitive state between [previousState] and [currentState].
     *
     * It should only be called if [hasTransition] returns true.
     */
    fun createTransition(measureManager: CellMeasureManager, options: SelectionRenderOptions): SelectionState.Transitive

    /**
     * Draws current state on canvas using specified options.
     *
     * @param canvas canvas to draw current state on.
     * @param options contains essential for drawing values.
     */
    fun draw(canvas: Canvas, options: SelectionRenderOptions)

    /**
     * Draws specified transitive state on canvas using specified options.
     *
     * @param canvas a [Canvas] instance to draw transition on.
     * @param state a [SelectionState.Transitive] instance to draw on [canvas].
     * @param options contains essential for drawing values.
     */
    fun drawTransition(
        canvas: Canvas,
        state: SelectionState.Transitive,
        options: SelectionRenderOptions,
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