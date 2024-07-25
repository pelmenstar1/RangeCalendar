package com.github.pelmenstar1.rangecalendar.complexRange.cell.transition

import com.github.pelmenstar1.rangecalendar.complexRange.cell.CellComplexRange
import com.github.pelmenstar1.rangecalendar.complexRange.cell.CellFragment

/**
 * Represents a builder for [CellTransitionGroup] object.
 */
class CellTransitionGroupBuilder {
    private val ops = ArrayList<CellTransitionOperation>()

    /**
     * Adds [CellTransitionOperation.Insert] operation to the group.
     *
     * @param fragment a fragment to insert
     */
    fun insert(fragment: CellFragment) {
        ops.add(CellTransitionOperation.Insert(fragment))
    }

    /**
     * Adds [CellTransitionOperation.Remove] operation to the group.
     *
     * @param fragment a fragment to remove
     */
    fun remove(fragment: CellFragment) {
        ops.add(CellTransitionOperation.Remove(fragment))
    }

    /**
     * Adds [CellTransitionOperation.Transform] operation to the group.
     *
     * [origin] and [destination] should not be equal, and they should overlap.
     *
     * @param origin a fragment to begin transition with
     * @param destination a fragment to end transition with
     */
    fun transform(origin: CellFragment, destination: CellFragment) {
        ops.add(CellTransitionOperation.Transform(origin, destination))
    }

    /**
     * Adds [CellTransitionOperation.Split] operation to the group.
     *
     * @param origin a fragment to split
     * @param destination a sequence of fragments to end up with after the transition. This sequence should be completely in the [origin] fragment
     */
    fun split(origin: CellFragment, destination: CellComplexRange) {
        ops.add(CellTransitionOperation.Split(origin, destination))
    }

    /**
     * Adds [CellTransitionOperation.Join] operation to the group.
     *
     * @param origin an initial sequence of fragments to join
     * @param destination a final fragment after joining fragments of [origin]
     */
    fun join(origin: CellComplexRange, destination: CellFragment) {
        ops.add(CellTransitionOperation.Join(origin, destination))
    }

    /**
     * Returns a new instance of [CellTransitionGroup].
     */
    fun build(): CellTransitionGroup = CellTransitionGroup(ops)
}

fun CellTransitionGroupBuilder.insert(range: IntRange) {
    insert(CellFragment(range))
}

fun CellTransitionGroupBuilder.remove(range: IntRange) {
    remove(CellFragment(range))
}

fun CellTransitionGroupBuilder.transform(origin: IntRange, dest: IntRange) {
    transform(CellFragment(origin), CellFragment(dest))
}

fun CellTransitionGroupBuilder.join(originRanges: Array<IntRange>, destRange: IntRange) {
    join(CellComplexRange(originRanges), CellFragment(destRange))
}

fun CellTransitionGroupBuilder.split(originRange: IntRange, destRanges: Array<IntRange>) {
    split(CellFragment(originRange), CellComplexRange(destRanges))
}