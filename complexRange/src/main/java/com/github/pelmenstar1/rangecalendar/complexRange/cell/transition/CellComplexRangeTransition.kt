package com.github.pelmenstar1.rangecalendar.complexRange.cell.transition

data class CellComplexRangeTransition(val groups: Set<CellTransitionGroup>) {
    override fun toString(): String {
        return buildString {
            append("CellComplexRangeTransition(")
            val groupSize = groups.size

            groups.forEachIndexed { i, group ->
                append(group)

                if (i < groupSize - 1) {
                    append(", ")
                }
            }
        }
    }
}

inline fun CellComplexRangeTransition(block: CellTransitionBuilder.() -> Unit): CellComplexRangeTransition {
    return CellTransitionBuilder().also(block).build()
}