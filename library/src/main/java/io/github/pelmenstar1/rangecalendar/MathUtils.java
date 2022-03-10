package io.github.pelmenstar1.rangecalendar;

import android.graphics.Color;

import androidx.annotation.ColorInt;

final class MathUtils {
    public static float lerp(float start, float end, float fraction) {
        return start + (end - start) * fraction;
    }

    public static int colorLerp(@ColorInt int start, @ColorInt int end, float fraction) {
        return Color.argb(
                (int)lerp(Color.alpha(start), Color.alpha(end), fraction),
                (int)lerp(Color.red(start), Color.red(end), fraction),
                (int)lerp(Color.green(start), Color.green(end), fraction),
                (int)lerp(Color.blue(start), Color.blue(end), fraction)
        );
    }
}
