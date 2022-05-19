package io.github.pelmenstar1.rangecalendar.decoration

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
@IntDef(
    DecorAnimationMethod.NONE,
    DecorAnimationMethod.SEQUENTIAL_ONE_WAY_FROM_START,
    DecorAnimationMethod.SEQUENTIAL_ONE_WAY_FROM_END,
    DecorAnimationMethod.SIMULTANEOUSLY
)
annotation class DecorAnimationMethodInt

object DecorAnimationMethod {
    const val NONE = 0
    const val SIMULTANEOUSLY = 1
    const val SEQUENTIAL_ONE_WAY_FROM_START = 2
    const val SEQUENTIAL_ONE_WAY_FROM_END = 3
}