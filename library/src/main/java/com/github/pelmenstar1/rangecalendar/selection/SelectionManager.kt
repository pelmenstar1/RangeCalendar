package com.github.pelmenstar1.rangecalendar.selection

import com.github.pelmenstar1.rangecalendar.CellMeasureManager

/**
 * Manages selection state and provides a way to draw a state and transition between them.
 */
interface SelectionManager {
    /**
     * State which was selected before [currentState].
     * If there's no previous state, it should be instance of [SelectionState] whose selection range is empty.
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
     * Sets a state with an empty selection range.
     */
    fun setNoneState()

    /**
     * Sets a current state.
     */
    fun setState(rangeStart: Int, rangeEnd: Int, measureManager: CellMeasureManager)

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
    fun createTransition(
        measureManager: CellMeasureManager,
        options: SelectionRenderOptions
    ): SelectionState.Transitive

    fun canJoinTransitions(current: SelectionState.Transitive, end: SelectionState.Transitive): Boolean

    fun joinTransitions(current: SelectionState.Transitive, end: SelectionState.Transitive, measureManager: CellMeasureManager): SelectionState.Transitive
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