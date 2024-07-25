package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.RectF

class SelectionTransition(val groups: List<SelectionTransitionGroup>) {
    private val currentStageTable = IntArray(groups.size)

    fun setCurrentStageIndex(groupIndex: Int, stageIndex: Int) {
        currentStageTable[groupIndex] = stageIndex
    }

    fun getCurrentStage(groupIndex: Int): SelectionTransitionStage {
        val stageIndex = currentStageTable[groupIndex]

        return groups[groupIndex].stages[stageIndex]
    }

    fun overlaysRect(bounds: RectF): Boolean {
        for (i in groups.indices) {
            val stage = getCurrentStage(i)

            if (stage.overlaysRect(bounds)) {
                return true
            }
        }

        return false
    }

    override fun equals(other: Any?): Boolean {
        return other is SelectionTransition && groups == other.groups
    }

    override fun hashCode(): Int {
        return groups.hashCode()
    }

    override fun toString(): String {
        return "SelectionTransition(groups=$groups)"
    }
}