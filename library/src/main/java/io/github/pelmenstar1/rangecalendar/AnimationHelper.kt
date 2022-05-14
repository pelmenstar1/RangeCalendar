package io.github.pelmenstar1.rangecalendar

import android.animation.ValueAnimator
import android.os.Build
import android.animation.ObjectAnimator
import android.util.FloatProperty
import android.annotation.SuppressLint

internal object AnimationHelper {
    private val BOXED_ZERO = java.lang.Float.valueOf(0f)

    @SuppressLint("Recycle")
    inline fun createFractionAnimator(crossinline callback: (Float) -> Unit): ValueAnimator {
        return if (Build.VERSION.SDK_INT >= 24) {
            ObjectAnimator().apply {
                setProperty(object : FloatProperty<Any?>("property") {
                    override fun get(o: Any?): Float? = BOXED_ZERO
                    override fun setValue(o: Any?, value: Float) {
                        callback(value)
                    }
                })
                setFloatValues(0f, 1f)
            }
        } else {
            ValueAnimator().apply {
                setFloatValues(0f, 1f)
                addUpdateListener { animation: ValueAnimator ->
                    callback(animation.animatedValue as Float)
                }
            }
        }
    }
}