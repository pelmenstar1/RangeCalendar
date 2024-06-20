package com.github.pelmenstar1.rangecalendar.complexRange.cell.transition

/**
 * A builder for [CellTransitionGroup].
 */
class CellTransitionBuilder {
    private val groups = HashSet<CellTransitionGroup>()

    /**
     * Adds a group built by given [block] lambda to the transition.
     */
    inline fun group(block: CellTransitionGroupBuilder.() -> Unit) {
        val g = CellTransitionGroupBuilder().also(block).build()

        group(g)
    }

    /**
     * Adds given group to the transition.
     */
    fun group(g: CellTransitionGroup) {
        groups.add(g)
    }

    /**
     * Builds a new [CellComplexRangeTransition] object using given transition groups.
     */
    fun build(): CellComplexRangeTransition {
        return CellComplexRangeTransition(groups)
    }
}