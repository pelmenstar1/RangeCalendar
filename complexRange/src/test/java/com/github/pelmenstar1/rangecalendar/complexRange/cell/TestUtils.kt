package com.github.pelmenstar1.rangecalendar.complexRange.cell

import com.github.pelmenstar1.rangecalendar.complexRange.cell.transition.CellComplexRangeTransition
import com.github.pelmenstar1.rangecalendar.complexRange.cell.transition.CellComplexRangeTransitionManager
import com.github.pelmenstar1.rangecalendar.complexRange.cell.transition.CellTransitionGroup
import com.github.pelmenstar1.rangecalendar.complexRange.cell.transition.CellTransitionOperation
import java.util.HashSet

fun CellTransitionOperation.inverted(): CellTransitionOperation {
    return when(this) {
        is CellTransitionOperation.Insert -> CellTransitionOperation.Remove(fragment)
        is CellTransitionOperation.Remove -> CellTransitionOperation.Insert(fragment)
        is CellTransitionOperation.Transform -> CellTransitionOperation.Transform(destination, origin)
        is CellTransitionOperation.Split -> CellTransitionOperation.Join(destination, origin)
        is CellTransitionOperation.Join -> CellTransitionOperation.Split(destination, origin)
        is CellTransitionOperation.NoOp -> this
    }
}

fun CellComplexRangeTransition.inverted(): CellComplexRangeTransition {
    val newGroups = ArrayList<CellTransitionGroup>(groups.size)

    for (group in groups) {
        val newOps = ArrayList<CellTransitionOperation>(group.operations.size)

        for (op in group.operations) {
            newOps.add(op.inverted())
        }

        newGroups.add(CellTransitionGroup(newOps.reversed()))
    }

    return CellComplexRangeTransition(newGroups)
}