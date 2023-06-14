package com.github.pelmenstar1.rangecalendar.selection

/**
 * Responsible for mutating [SelectionState.Transitive] internal data in order to make a transition based on the animation fraction.
 */
interface SelectionTransitionController {
    /**
     * Changes given [state] based on specified animation [fraction].
     */
    fun handleTransition(state: SelectionState.Transitive, fraction: Float)
}