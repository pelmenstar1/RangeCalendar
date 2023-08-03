package com.github.pelmenstar1.rangecalendar.selection

import com.github.pelmenstar1.rangecalendar.CalendarGridInfo
import com.github.pelmenstar1.rangecalendar.CellMeasureManager

/**
 * Responsible for providing [SelectionRenderer], [SelectionTransitionController], creating selection states and transitions between them.
 * The manager is expected to be stateless, except caching in [renderer], [transitionController], as the same instance is used
 * among different calendars.
 */
interface SelectionManager {
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
     * Creates selection state using range of selected cells (`[rangeStart; rangeEnd]`) and [measureManager].
     *
     * @param rangeStart index of a start of the range, inclusive
     * @param rangeEnd index of an end of the range, **inclusive**
     * @param measureManager [CellMeasureManager] instance that provides a way to get information about measurements
     */
    fun createState(
        rangeStart: Int,
        rangeEnd: Int,
        measureManager: CellMeasureManager,
        gridInfo: CalendarGridInfo
    ): SelectionState

    /**
     * Updates given [state] due to the configuration change (some measurements may be changed)
     */
    fun updateConfiguration(state: SelectionState, measureManager: CellMeasureManager, gridInfo: CalendarGridInfo)

    /**
     * Creates a transitive state between [previousState] and [currentState].
     *
     * If [previousState] and/or [currentState] are `null`, it means that the respective state doesn't exist.
     * For example, if a range is selected but there was no selection before, [previousState] is `null`.
     *
     * If there's no transition between states, returns `null`.
     */
    fun createTransition(
        previousState: SelectionState?,
        currentState: SelectionState?,
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
        end: SelectionState?,
        measureManager: CellMeasureManager
    ): SelectionState.Transitive?
}

internal fun SelectionManager.createState(
    range: CellRange,
    measureManager: CellMeasureManager,
    gridInfo: CalendarGridInfo
): SelectionState {
    return createState(range.start.index, range.end.index, measureManager, gridInfo)
}