package io.github.pelmenstar1.rangecalendar

import android.os.Build
import androidx.annotation.IntDef

@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.LOCAL_VARIABLE,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.VALUE_PARAMETER
)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
@IntDef(WeekdayType.SHORT, WeekdayType.NARROW)
annotation class WeekdayTypeInt

object WeekdayType {
    const val SHORT = 0
    const val NARROW = 1

    fun resolve(type: Int): Int {
        return if (Build.VERSION.SDK_INT < 24 && type == NARROW) {
            SHORT
        } else type
    }
}