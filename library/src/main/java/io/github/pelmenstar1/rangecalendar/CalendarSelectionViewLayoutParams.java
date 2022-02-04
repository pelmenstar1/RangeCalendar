package io.github.pelmenstar1.rangecalendar;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ViewGroup;

import androidx.annotation.GravityInt;

import org.jetbrains.annotations.NotNull;

/**
 * LayoutParams for calendar's selection view.
 * Besides of default width and height properties, there is also gravity.
 */
public final class CalendarSelectionViewLayoutParams extends ViewGroup.LayoutParams {
    static final CalendarSelectionViewLayoutParams DEFAULT = new CalendarSelectionViewLayoutParams(
            WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER
    );

    @GravityInt
    public int gravity = Gravity.CENTER;

    public CalendarSelectionViewLayoutParams(@NotNull Context c, @NotNull AttributeSet attrs) {
        super(c, attrs);
    }

    public CalendarSelectionViewLayoutParams(int width, int height) {
        this(width, height, Gravity.CENTER);
    }

    public CalendarSelectionViewLayoutParams(int width, int height, @GravityInt int gravity) {
        super(width, height);

        this.gravity = gravity;
    }

    public CalendarSelectionViewLayoutParams(@NotNull ViewGroup.LayoutParams source) {
        super(source);
    }
}
