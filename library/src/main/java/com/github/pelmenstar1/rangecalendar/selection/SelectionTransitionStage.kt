package com.github.pelmenstar1.rangecalendar.selection

import android.graphics.RectF

interface SelectionTransitionStage {
    fun overlaysRect(bounds: RectF): Boolean
}