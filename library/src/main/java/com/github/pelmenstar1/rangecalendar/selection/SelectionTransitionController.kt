package com.github.pelmenstar1.rangecalendar.selection

import com.github.pelmenstar1.rangecalendar.CellMeasureManager

/**
 * Responsible for mutating [SelectionState.Transitive] internal data in order to make a transition based on the animation fraction.
 */
interface SelectionTransitionController {
    /**
     * Changes given [state] based on specified animation [fraction].
     */
    fun handleTransition(
        state: SelectionState.Transitive,
        measureManager: CellMeasureManager,
        fraction: Float
    )
}