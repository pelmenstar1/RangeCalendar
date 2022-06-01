package io.github.pelmenstar1.rangecalendar

import android.animation.ValueAnimator
import android.os.Build
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.util.FloatProperty
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