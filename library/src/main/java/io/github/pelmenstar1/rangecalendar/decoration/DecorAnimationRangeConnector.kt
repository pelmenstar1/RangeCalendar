package io.github.pelmenstar1.rangecalendar.decoration

import io.github.pelmenstar1.rangecalendar.PackedIntRange

internal interface DecorAnimationRangeConnector {
    fun length(): Int
    fun setAnimationFraction(decors: DecorSortedList, index: Int, fraction: Float)
}

internal fun DecorAnimationRangeConnector.setAnimationFractionToOneOnRange(
    decors: DecorSortedList,
    start: Int,
    endInclusive: Int
) {
    for(i in start..endInclusive) {
        setAnimationFraction(decors, i, 1f)
    }
}

internal fun DecorAnimationRangeConnector(range: PackedIntRange): DecorAnimationRangeConnector {
    return StandardRangeDecorAnimationRangeConnector(range)
}

internal fun DecorAnimationRangeConnector(indices: IntArray): DecorAnimationRangeConnector {
    return ScatteredRangeDecorAnimationRangeConnector(indices)
}

private class StandardRangeDecorAnimationRangeConnector(val range: PackedIntRange) :
    DecorAnimationRangeConnector {
    override fun length(): Int {
        return range.endInclusive - range.start + 1
    }

    override fun setAnimationFraction(
        decors: DecorSortedList,
        index: Int,
        fraction: Float
    ) {
        decors[range.start + index].animationFraction = fraction
    }
}

private class ScatteredRangeDecorAnimationRangeConnector(val indices: IntArray) :
    DecorAnimationRangeConnector {
    override fun length(): Int {
        return indices.size
    }

    override fun setAnimationFraction(
        decors: DecorSortedList,
        index: Int,
        fraction: Float
    ) {
        decors[indices[index]].animationFraction = fraction
    }
}
