package io.github.pelmenstar1.rangecalendar

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
@IntDef(ClickOnCellSelectionBehavior.NONE, ClickOnCellSelectionBehavior.CLEAR)
annotation class ClickOnCellSelectionBehaviourInt

/**
 * Defines all possible behaviors when user clicks on already selected cell
 */
object ClickOnCellSelectionBehavior {
    /**
     * Nothing happens. The cell remains selected.
     */
    const val NONE = 0

    /**
     * Unselects the cell.
     */
    const val CLEAR = 1
}