package io.github.pelmenstar1.rangecalendar.utils

import android.util.Log
import io.github.pelmenstar1.rangecalendar.packInts
import io.github.pelmenstar1.rangecalendar.unpackFirstInt
import io.github.pelmenstar1.rangecalendar.unpackSecondInt

internal fun InterpolateFractionOnItemsResult(
    itemIndex: Int,
    itemFraction: Float
): InterpolateFractionOnItemsResult {
    return InterpolateFractionOnItemsResult(packInts(itemIndex, itemFraction.toBits()))
}

@JvmInline
internal value class InterpolateFractionOnItemsResult(val packed: Long) {
    val itemIndex: Int
        get() = unpackFirstInt(packed)

    val itemFraction: Float
        get() = Float.fromBits(unpackSecondInt(packed))
}

internal fun interpolateFractionOnItems(
    fraction: Float,
    itemCount: Int
): InterpolateFractionOnItemsResult {
    if(fraction == 1f) {
        Log.i("InterFrOnItems", "fraction: 1f, itemIndex: ${itemCount  - 1}")
        return InterpolateFractionOnItemsResult(itemCount - 1, 1f)
    }

    val scaledFraction = fraction * itemCount

    val itemIndex = scaledFraction.toInt()
    val itemFraction = scaledFraction - itemIndex

    Log.i("InterFrOnItems", "fraction: $fraction, itemFraction: $itemFraction, itemIndex: $itemIndex")
    return InterpolateFractionOnItemsResult(itemIndex, itemFraction)
}