package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.Canvas

/**
 * Provides the methods to render selection states on a canvas.
 *
 * There's a connection between [SelectionManager] and [SelectionRenderer]. The [SelectionManager] should only
 * create the states and save them. The [SelectionRenderer] only renders those states. In other words, an implementation of [SelectionRenderer]
 * should recognize the types of selection states that are created by appropriate implementation of [SelectionManager]. It means
 * that an implementation of [SelectionRenderer] may not render all types of selection states.
 */
interface SelectionRenderer {
    /**
     * Draws specified [state] on the [canvas] using specified [options].
     *
     * @param canvas a canvas to draw current state on.
     * @param state a [SelectionState] instance to draw on [canvas].
     * @param options contains essential for drawing values.
     */
    fun draw(canvas: Canvas, state: SelectionState, options: SelectionRenderOptions)

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
        options: SelectionRenderOptions
    )
}