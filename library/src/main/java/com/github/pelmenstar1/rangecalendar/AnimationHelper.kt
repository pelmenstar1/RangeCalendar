package com.github.pelmenstar1.rangecalendar

import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint

internal object AnimationHelper {
    private val VALUES = PropertyValuesHolder.ofFloat("", 0f, 1f)

    @SuppressLint("Recycle")
    inline fun createFractionAnimator(crossinline callback: (Float) -> Unit): ValueAnimator {
        return ValueAnimator().apply {
            setValues(VALUES)

            addUpdateListener {
                callback(it.animatedFraction)
            }
        }
    }
}