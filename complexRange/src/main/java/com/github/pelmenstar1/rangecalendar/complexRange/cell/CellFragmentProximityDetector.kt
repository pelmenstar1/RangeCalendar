package com.github.pelmenstar1.rangecalendar.complexRange.cell

interface CellFragmentProximityDetector {
    fun canMove(origin: CellFragment, destination: CellFragment): Boolean

    companion object {
        private val NEVER_MOVE = createConditionalMove(false)
        private val ALWAYS_MOVE = createConditionalMove(true)

        private fun createConditionalMove(state: Boolean): CellFragmentProximityDetector {
            return object : CellFragmentProximityDetector {
                override fun canMove(origin: CellFragment, destination: CellFragment) = state
            }
        }

        fun neverMove(): CellFragmentProximityDetector = NEVER_MOVE
        fun alwaysMove(): CellFragmentProximityDetector = ALWAYS_MOVE

        fun withMoveDistance(maxDistance: Int): CellFragmentProximityDetector {
            return object: CellFragmentProximityDetector {
                override fun canMove(origin: CellFragment, destination: CellFragment): Boolean {
                    return origin.getDistanceTo(destination) <= maxDistance
                }
            }
        }
    }
}