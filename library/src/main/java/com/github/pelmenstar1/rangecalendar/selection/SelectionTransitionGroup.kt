package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.RectF

class SelectionTransitionGroup(val stages: List<SelectionTransitionStage>) {
    fun overlaysRect(bounds: RectF): Boolean {
        return stages.any { it.overlaysRect(bounds) }
    }

    override fun equals(other: Any?): Boolean {
        return other is SelectionTransitionGroup && stages == other.stages
    }

    override fun hashCode(): Int {
        return stages.hashCode()
    }

    override fun toString(): String {
        return "SelectionTransitionGroup(stages=$stages)"
    }
}