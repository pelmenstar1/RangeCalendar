package com.github.pelmenstar1.rangecalendar;

import android.graphics.Paint;
import android.graphics.Rect;

import org.jetbrains.annotations.NotNull;

final class TextUtils {
    @NotNull
    private static final Rect textBoundsCache = new Rect();
    private static final Paint tempPaint = new Paint();

    private TextUtils() {
    }

    public static long getTextBounds(
            @NotNull String text,
            float textSize
    ) {
        tempPaint.setTextSize(textSize);

        return getTextBounds(text, tempPaint);
    }

    public static long getTextBounds(
            char @NotNull [] text,
            int offset, int length,
            float textSize
    ) {
        tempPaint.setTextSize(textSize);

        return getTextBounds(text, offset, length, tempPaint);
    }

    public static long getTextBounds(char @NotNull [] text, int index, int length, @NotNull Paint paint) {
        paint.getTextBounds(text, index, length, textBoundsCache);

        return packTextBoundsCache();
    }

    public static long getTextBounds(@NotNull String text, @NotNull Paint paint) {
        paint.getTextBounds(text, 0, text.length(), textBoundsCache);

        return packTextBoundsCache();
    }

    private static long packTextBoundsCache() {
        Rect bounds = textBoundsCache;

        return PackedSize.create(bounds.width(), bounds.height());
    }
}
