package io.github.pelmenstar1.rangecalendar;

import android.graphics.Color;

import androidx.annotation.ColorInt;

final class ColorHelper {
    @ColorInt
    public static int withAlpha(@ColorInt int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    @ColorInt
    public static int makeDarkerColor(@ColorInt int color, float factor) {
        float invFactor = 1f - factor;

        int a = Color.alpha(color);
        int r = Math.round(Color.red(color) * invFactor);
        int g = Math.round(Color.green(color) * invFactor);
        int b = Math.round(Color.blue(color) * invFactor);

        return Color.argb(a,
                Math.min(r, 255),
                Math.min(g, 255),
                Math.min(b, 255)
        );
    }
}
