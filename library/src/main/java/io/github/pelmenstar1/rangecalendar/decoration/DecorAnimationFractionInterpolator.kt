package io.github.pelmenstar1.rangecalendar.decoration

interface DecorAnimationFractionInterpolator {
    fun getItemFraction(index: Int, itemCount: Int, totalFraction: Float): Float

    companion object {
        val Simultaneous = object: DecorAnimationFractionInterpolator {
            override fun getItemFraction(index: Int, itemCount: Int, totalFraction: Float): Float {
                return totalFraction
            }
        }

        val Sequential = object: DecorAnimationFractionInterpolator {
            override fun getItemFraction(index: Int, itemCount: Int, totalFraction: Float): Float {
                if(totalFraction == 1f) {
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