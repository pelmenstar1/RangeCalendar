package com.github.pelmenstar1.rangecalendar.selection

interface SelectionTransitionGroup {
    fun stages(): Collection<SelectionTransitionStage>
}