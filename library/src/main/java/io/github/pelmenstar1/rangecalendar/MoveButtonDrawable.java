package io.github.pelmenstar1.rangecalendar;

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

final class MoveButtonDrawable extends Drawable {
    public static final int DIRECTION_LEFT = 0;
    public static final int DIRECTION_RIGHT = 1;

    public static final int ANIM_TYPE_ARROW_TO_CLOSE = 0;
    public static final int ANIM_TYPE_VOID_TO_ARROW = 1;

    private static final int MIN_VISIBLE_ALPHA = 40;

    private static final int[] ENABLED_STATE = new int[] { android.R.attr.state_enabled };
    private static final Choreographer choreographer = Choreographer.getInstance();

    private final ColorStateList colorList;
    private final Paint paint;

    private int prevColor;
    private int color;

    private final int size;
    private final float strokeWidth;

    private float animationFraction;

    private final float[] linePoints = new float[8];
    private int linePointsLength = 8;

    private final int direction;
    private final int animationType;

    private float invStateChangeDuration;
    private long stateChangeStartTime;

    private final Choreographer.FrameCallback stateChangeAnimTickCb = this::onStateChangeAnimTick;
    private final Choreographer.FrameCallback startStateChangeAnimCb = time -> {
        stateChangeStartTime = time;
        onStateChangeAnimTickFraction(0f);

        choreographer.postFrameCallback(stateChangeAnimTickCb);
    };

    public MoveButtonDrawable(
            @NotNull Context context,
            @NotNull ColorStateList colorList,
            int direction,
            int animType
    ) {
        this.colorList = colorList;
        this.direction = direction;
        this.animationType = animType;

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

    public void setAnimationFraction(float fraction) {
        animationFraction = fraction;

        computeLinePoints();
        invalidateSelf();
    }

    public void setStateChangeDuration(long millis) {
        // convert to nanos and then, invert
        invStateChangeDuration = 1f / (1_000_000 * millis);
    }

    private void onStateChangeAnimTick(long time) {
        float fraction = (time - stateChangeStartTime) * invStateChangeDuration;

        if(fraction >= 1f) {
            onStateChangeAnimTickFraction(1f);
        } else {
            onStateChangeAnimTickFraction(fraction);

            choreographer.postFrameCallback(stateChangeAnimTickCb);
        }
    }

    private void onStateChangeAnimTickFraction(float fraction) {
        int c = MathUtils.colorLerp(prevColor, color, fraction);

        paint.setColor(c);
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

        float[] points = linePoints;

        if(animationType == ANIM_TYPE_ARROW_TO_CLOSE) {
            float line1EndY = MathUtils.lerp(midY, actualBottom, fraction);
            float line2EndY = MathUtils.lerp(midY, actualTop, fraction);

            float lineEndX;
            float anchorX;

            if (direction == DIRECTION_RIGHT) {
                lineEndX = MathUtils.lerp(midX, actualRight, fraction);
                anchorX = actualLeft;
            } else {
                lineEndX = MathUtils.lerp(midX, actualLeft, fraction);
                anchorX = actualRight;
            }

            points[0] = anchorX;
            points[1] = actualTop;
            points[2] = lineEndX;
            points[3] = line1EndY;

            points[4] = anchorX;
            points[5] = actualBottom;
            points[6] = lineEndX;
            points[7] = line2EndY;
        } else {
            float anchorX = direction == DIRECTION_LEFT ? actualRight : actualLeft;

            if(fraction <= 0.5f) {
                float sFr = fraction * 2f;

                points[0] = anchorX;
                points[1] = actualBottom;

                points[2] = MathUtils.lerp(anchorX, midX, sFr);
                points[3] = MathUtils.lerp(actualBottom, midY, sFr);

                linePointsLength = 4;
            } else {
                float sFr = fraction * 2f - 1f;

                points[0] = anchorX;
                points[1] = actualBottom;
                points[2] = midX;
                points[3] = midY;

                points[4] = midX;
                points[5] = midY;
                points[6] = MathUtils.lerp(midX, anchorX, sFr);
                points[7] = MathUtils.lerp(midY, actualTop, sFr);

                linePointsLength = 8;
            }
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
       computeLinePoints();
    }

    @Override
    protected boolean onStateChange(int[] state) {
        int oldColor = color;
        int newColor = colorList.getColorForState(state, colorList.getDefaultColor());

        if(oldColor != newColor) {
            prevColor = oldColor;
            color = newColor;
            choreographer.postFrameCallback(startStateChangeAnimCb);

            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    public void draw(@NonNull Canvas c) {
        c.drawLines(linePoints, 0, linePointsLength, paint);

        if(animationType == ANIM_TYPE_ARROW_TO_CLOSE ||
                (animationType == ANIM_TYPE_VOID_TO_ARROW && animationFraction >= 0.5f)) {
            Rect bounds = getBounds();

            float cx = bounds.centerX();
            float cy = bounds.centerY();
            float radius = strokeWidth * 0.5f;

            c.drawCircle(cx, cy, radius, paint);
        }
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
