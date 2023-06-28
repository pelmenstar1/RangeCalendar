package com.github.pelmenstar1.rangecalendar.decoration

/**
 * Responsible for interpolating single fraction to a range of items.
 */
interface DecorAnimationFractionInterpolator {
    /**
     * Returns interpolated fraction for the item
     *
     * @param index index of the item
     * @param itemCount count of items in the set
     * @param totalFraction fraction to be interpolated
     */
    fun getItemFraction(index: Int, itemCount: Int, totalFraction: Float): Float

    companion object {
        /**
         * The interpolator is such that all items has the same fraction as total fraction.
         */
        val Simultaneous = object : DecorAnimationFractionInterpolator {
            override fun getItemFraction(index: Int, itemCount: Int, totalFraction: Float): Float {
                return totalFraction
            }
        }

        /**
         * The interpolator is such next item gets its share of fraction only when previous item's fraction is 1
         */
        val Sequential = object : DecorAnimationFractionInterpolator {
            override fun getItemFraction(index: Int, itemCount: Int, totalFraction: Float): Float {
                if (totalFraction == 1f) {
                    return 1f
                }

                val scaledFraction = totalFraction * itemCount
                val animatedIndex = scaledFraction.toInt()

                val outFraction = when {
                    index < animatedIndex -> 1f
                    animatedIndex == index -> scaledFraction - animatedIndex
                    else -> 0f
                }

                return outFraction
            }
        }
    }
}