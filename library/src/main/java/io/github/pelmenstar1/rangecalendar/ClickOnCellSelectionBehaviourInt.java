package io.github.pelmenstar1.rangecalendar;

import androidx.annotation.IntDef;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.FIELD, ElementType.LOCAL_VARIABLE, ElementType.METHOD, ElementType.PARAMETER })
@Retention(RetentionPolicy.SOURCE)
@Documented
@IntDef({ ClickOnCellSelectionBehavior.NONE, ClickOnCellSelectionBehavior.CLEAR })
public @interface ClickOnCellSelectionBehaviourInt {
}
