package com.github.pelmenstar1.rangecalendar.selection

import com.github.pelmenstar1.rangecalendar.CellMeasureManager

/**
 * Manages selection state and provides a way to draw a state and transition between them.
 */
interface SelectionManager {
    /**
     * State which was selected before [currentState].
     * If there's no previous state, it should be instance of [SelectionState] whose selection range is empty,
     * i.e [SelectionState.rangeStart] is greater than [SelectionState.rangeEnd].
     */
    val previousState: SelectionState

    /**
     * Current state.
     */
    val currentState: SelectionState

    /**
     * Gets a selection renderer that is connected to the current [SelectionManager] in the way that the renderer
     * recognizes the types of selection states that are created by the selection manager.
     *
     * It's expected that the same [SelectionRenderer] instance is returned each time the property is accessed.
     */
    val renderer: SelectionRenderer

    /**
     * Gets a transition controller that is connected to the current [SelectionManager] in the way that the controller
     * recognizes the types of transitive selection states that are created by the selection manager.
     *
     * It's expected that the same [SelectionTransitionController] instance is returned each time the property is accessed.
     */
    val transitionController: SelectionTransitionController

    /**
     * Sets a current state. If the [rangeStart] is greater than [rangeEnd], the selection is empty.
     */
    fun setState(rangeStart: Int, rangeEnd: Int, measureManager: CellMeasureManager)

    /**
     * Updates previous and current state due to the configuration change ([CellMeasureManager] measurements are changed)
     */
    fun updateConfiguration(measureManager: CellMeasureManager)

    /**
     * Creates a transitive state between [previousState] and [currentState].
     * If there's no transition between states, returns `null`.
     */
    fun createTransition(
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions
    ): SelectionState.Transitive?

    /**
     * Transforms the [current] transition and [end] state in such way that the end state of [current] transition become given [end] state.
     * If there's no such transition, returns `null`.
     * Note that the resulting transition may be used in [joinTransition] again as a [current] transition.
     *
     * The implementation is allowed to mutate and reuse internal information of [current] and/or [end] states.
     * Thus, after creating the joined transition, the calling code can't use [current] and [end] states.
     */
    fun joinTransition(
        current: SelectionState.Transitive,
        end: SelectionState,
        measureManager: CellMeasureManager
    ): SelectionState.Transitive?
}

internal fun SelectionManager.setState(
    range: CellRange,
    measureManager: CellMeasureManager
) {
    setState(range.start, range.end, measureManager)
}

internal fun SelectionManager.setState(
    rangeStart: Cell,
    rangeEnd: Cell,
    measureManager: CellMeasureManager
) {
    setState(rangeStart.index, rangeEnd.index, measureManager)
}