package com.github.pelmenstar1.rangecalendar

import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings

internal object AnimationHelper {
    private val VALUES = PropertyValuesHolder.ofFloat("", 0f, 1f)

    fun getAnimationScale(context: Context): Float {
        if (Build.VERSION.SDK_INT < 17) {
            return 1f
        }

        return Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
    }

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