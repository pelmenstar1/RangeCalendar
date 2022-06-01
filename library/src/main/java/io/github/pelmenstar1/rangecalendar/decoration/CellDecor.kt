package io.github.pelmenstar1.rangecalendar.decoration

import android.content.Context
import android.graphics.Canvas
import io.github.pelmenstar1.rangecalendar.RangeCalendarView
import io.github.pelmenstar1.rangecalendar.PackedDate
import io.github.pelmenstar1.rangecalendar.selection.Cell

/**
 * Represents a cell decoration.
 * There are few limitations:
 * - Every decoration is assigned to particular date and thus cannot exist in more than one cell.
 * - Only **one** type of decoration can exist in **one** cell.
 */
abstract class CellDecor {
    /**
     * Responsible for saving information needed to draw all the decorations in the cell.
     */
    interface VisualState {
        /**
         * Determines whether the visual state is empty (no decorations in the cell).
         */
        val isEmpty: Boolean

        /**
         * Returns special [Visual] object which should be unique for any type of decoration.
         * Note that this method shouldn't allocate
         * and is expected to returns the same [Visual] object every time it's called.
         * [CellDecor.visual] and [CellDecor.VisualState.visual] should return the same [Visual] instance.
         *
         * In Kotlin, `object` declaration can be used. Example:
         * ```
         * class SomeDecor : CellDecor() {
         *     class SomeDecorVisualState : VisualState {
         *         ...
         *
         *         fun visual() = SomeDecorVisual()
         *     }
         *
         *     object SomeDecorVisual: Visual {
         *         ...
         *     }
         * }
         * ```
         */
        fun visual(): Visual

        /**
         * Responsible for smooth transition between two visual states.
         * Unlike common [VisualState], [VisualState.Transitive] is mutable.
         * This state is derived from common [VisualState] but, nevertheless,
         * class which implements [VisualState.Transitive] is expected to be derived
         * from your decor's common visual state.
         * That's due to the fact instance of the class which implements [VisualState.Transitive]
         * will be passed to [Renderer.renderState] and logic for rendering common [VisualState] and
         * [VisualState.Transitive] is expected to be the same.
         * Example:
         * ```
        *  class SomeDecor : CellDecor() {
         *     open class SomeDecorVisualState : VisualState {
         *         ...
         *
         *         fun visual() = SomeDecorVisual()
         *     }
         *
         *     class TransitiveDecorVisualState : SomeDecorVisualState(), VisualState.Transitive {
         *         ...
         *     }
         *
         *     object SomeDecorVisual: Visual {
         *         ...
         *     }
         * }
         * ```
         */
        interface Transitive: VisualState {
            /**
             * The state from which the transition is started
             */
            val start: VisualState

            /**
             * The end state of transition
             */
            val end: VisualState

            /**
             * Current fraction of transition, in range `[0; 1]`.
             */
            val animationFraction: Float

            /**
             * Mutates instance of [VisualState.Transitive] to represent transition in specified fraction.
             *
             * @param animationFraction fraction of completeness of animation, in range `[0; 1]`
             * @param fractionInterpolator is used to interpolate [animationFraction] on set of changed items.
             */
            fun handleAnimation(
                animationFraction: Float,
                fractionInterpolator: DecorAnimationFractionInterpolator
            )
        }
    }

    /**
     * Represents all supported changes that can happen with cell decorations and can be animated.
     */
    enum class VisualStateChange {
        ADD,
        REMOVE,
        CELL_INFO
    }

    /**
     * Responsible for simple returning static information of [CellDecor].
     * Common implementation:
     * ```
     * class SomeDecor : CellDecor() {
     *     object SomeDecorStateHandler : VisualStateHandler {
     *         ...
     *     }
     *
     *     object SomeDecorRenderer : Renderer {
     *         ...
     *     }
     *
     *     object SomeDecorVisual : Visual {
     *         override fun stateHandler(): VisualStateHandler = SomeDecorStateHandler
     *         override fun renderer(): Renderer = SomeDecorRenderer
     *     }
     * }
     * ```
     */
    interface Visual {
        /**
         * Returns static [VisualStateHandler] associated with particular type of decoration.
         * **This method should not allocate and should return the same instance every time it's called**
         */
        fun stateHandler(): VisualStateHandler

        /**
         * Returns static [Renderer] associated with particular type of decoration.
         * **This method should not allocate and should return the same instance every time it's called**
         */
        fun renderer(): Renderer
    }

    interface VisualStateHandler {
        /**
         * Returns empty [VisualState] which contains no decorations.
         */
        fun emptyState(): VisualState

        /**
         * Creates instance of [VisualState] using array of all decorations, start and end positions and [CellInfo] instance.
         *
         * @param context [Context] of [RangeCalendarView] instance
         * @param start start position of range of all decorations in the cell.
         * @param endInclusive end position (inclusive) of range of all decorations in the cell.
         * @param info describes some properties of the cell like round radius and size.
         * Note that [CellInfo] is internally mutable and valid only during invocation of [createState].
         */
        fun createState(
            context: Context,
            decorations: Array<out CellDecor>,
            start: Int, endInclusive: Int,
            info: CellInfo
        ): VisualState

        /**
         * Creates transitive state between [start] and [end] states.
         *
         * [affectedRangeStart] and [affectedRangeEnd] represent a range of items affected by [VisualStateChange].
         * When:
         * - change is [VisualStateChange.ADD], the range is range of added items.
         * Then only [end] state contains this range.
         * - change is [VisualStateChange.REMOVE], the range is range of removed items.
         * Then only [start] state contains this range.
         * - change is [VisualStateChange.CELL_INFO], the range is range of items which [CellInfo] was changed.
         * Both [start] and [end] state contain this range.
         */
        fun createTransitiveState(
            start: VisualState,
            end: VisualState,
            affectedRangeStart: Int, affectedRangeEnd: Int,
            change: VisualStateChange
        ): VisualState.Transitive
    }

    /**
     * Responsible for rendering [VisualState]
     */
    interface Renderer {
        /**
         * Renders [VisualState] on [Canvas]
         *
         * @param canvas [Canvas] on which to render state
         * @param state [VisualState] to be rendered
         */
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

    /**
     * Returns special [Visual] object which should be unique for any type of decoration.
     * Note that this method shouldn't allocate
     * and is expected to returns the same [Visual] object every time it's called.
     * [CellDecor.visual] and [CellDecor.VisualState.visual] should return the same [Visual] instance.
     *
     * In Kotlin, `object` declaration can be used. Example:
     * ```
     * class SomeDecor : CellDecor() {
     *     class SomeDecorVisualState : VisualState {
     *         ...
     *
     *         fun visual() = SomeDecorVisual()
     *     }
     *
     *     object SomeDecorVisual: Visual {
     *         ...
     *     }
     * }
     * ```
     */
    abstract fun visual(): Visual
}

/**
 * If the receiver is [CellDecor.VisualState.Transitive],
 * returns [CellDecor.VisualState.Transitive.animationFraction],
 * otherwise 1
 */
val CellDecor.VisualState.animationFraction: Float
    get() = if(this is CellDecor.VisualState.Transitive) animationFraction else 1f