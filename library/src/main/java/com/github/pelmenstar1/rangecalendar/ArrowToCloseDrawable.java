package com.github.pelmenstar1.rangecalendar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Choreographer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

final class ArrowToCloseDrawable extends Drawable {
    public static final int DIRECTION_LEFT = 0;
    public static final int DIRECTION_RIGHT = 1;

    private static final int MIN_VISIBLE_ALPHA = 40;

    private static final int[] ENABLED_STATE = new int[] { android.R.attr.state_enabled };

    private static final Choreographer choreographer = Choreographer.getInstance();

    private final ColorStateList colorList;
    private final Paint paint;
    private int color;

    private final int size;
    private final float strokeWidth;

    private float animationFraction;

    private final float[] linePoints = new float[8];

    private final int direction;

    public ArrowToCloseDrawable(
            @NotNull Context context,
            @NotNull ColorStateList colorList,
            int direction
    ) {
        this.colorList = colorList;
        this.direction = direction;

        Resources res = context.getResources();

        strokeWidth = res.getDimension(R.dimen.rangeCalendar_arrowStrokeWidth);
        size = res.getDimensionPixelSize(R.dimen.rangeCalendar_arrowSize);

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        color = colorList.getColorForState(ENABLED_STATE, 0);

        // Lines have intersection point in the center. But if stroke color has alpha,
        // then pixels of the intersection point will be brighter than other pixels.
        // But this behavior can be controlled by Paint.setXfermode
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        paint.setColor(color);
        paint.setStrokeWidth(strokeWidth);
    }

    public void onAnimationFraction(float fraction) {
        animationFraction = fraction;

        computeLinePoints();
        invalidateSelf();
    }

    private void computeLinePoints() {
        Rect bounds = getBounds();
        int boundsLeft = bounds.left;
        int boundsTop = bounds.top;
        int boundsWidth = bounds.width();
        int boundsHeight = bounds.height();

        int actualLeft = boundsLeft + (boundsWidth - size) / 2;
        int actualTop = boundsTop + (boundsHeight - size) / 2;
        int actualRight = actualLeft + size;
        int actualBottom = actualTop + size;

        int midX = boundsLeft + boundsWidth / 2;
        int midY = boundsTop + boundsHeight / 2;

        float fraction = animationFraction;
        float halfStrokeWidth = strokeWidth * 0.5f;

        float adjustment = fraction == 0f ? halfStrokeWidth : 0f;

        float line1EndTop = MathUtils.fraction(midY, actualBottom, fraction);
        float line2EndTop = MathUtils.fraction(midY - adjustment, actualTop, fraction);

        float[] points = linePoints;

        float lineLeftEnd;
        if(direction == DIRECTION_RIGHT) {
            lineLeftEnd = MathUtils.fraction(midX + adjustment, actualRight, fraction);

            points[0] = actualLeft;
            points[4] = actualLeft;
        } else {
            lineLeftEnd = MathUtils.fraction(midX - adjustment, actualLeft, fraction);

            points[0] = actualRight;
            points[4] = actualRight;
        }

        //
        points[1] = actualTop;
        points[2] = lineLeftEnd;
        points[3] = line1EndTop;
        //
        points[5] = actualBottom;
        points[6] = lineLeftEnd;
        points[7] = line2EndTop;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
       computeLinePoints();
    }

    @Override
    protected boolean onStateChange(int[] state) {
        int oldColor = color;
        color = colorList.getColorForState(state, 0);
        paint.setColor(color);

        return oldColor != color;
    }

    @Override
    public void draw(@NonNull Canvas c) {
        c.drawLines(linePoints, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        // With PorterDuff.Mode.SRC, Stranger Things is happening when alpha is less than about 40.
        // Color become black, despite the documentation of PorterDuff.Mode.SRC.
        // So, constrain alpha to be from 40 to original alpha of color
        int constrained = MIN_VISIBLE_ALPHA + ((alpha * (Color.alpha(color) - MIN_VISIBLE_ALPHA)) / 255);

        paint.setColor(ColorHelper.withAlpha(color, constrained));
        invalidateSelf();
    }

    @Nullable
    @Override
    public ColorFilter getColorFilter() {
        return paint.getColorFilter();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        switch (Color.alpha(color)) {
            case 0:
                return PixelFormat.TRANSPARENT;
            case 255:
                return PixelFormat.OPAQUE;
            default:
                return PixelFormat.TRANSLUCENT;
        }
    }
}
