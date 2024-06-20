package com.github.pelmenstar1.rangecalendar.complexRange.cell.transition

data class CellTransitionGroup(val operations: Collection<CellTransitionOperation>) {
    override fun toString(): String {
        return buildString {
            append("CellTransitionGroup(")
            val opSize = operations.size
            operations.forEachIndexed { i, op ->
                append(op)

                if (i < opSize - 1) {
                    append(", ")
                }
            }
        }
    }

    companion object {
        fun create(op: CellTransitionOperation): CellTransitionGroup {
            return CellTransitionGroup(listOf(op))
        }
    }
}