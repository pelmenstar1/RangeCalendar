package io.github.pelmenstar1.rangecalendar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Choreographer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

final class MoveButtonDrawable extends Drawable {
    private static final class AnimationState {
        private int startColor;
        private int endColor;

        private boolean isRunning;
        private boolean isCancelledBecauseOfQueue;

        public void set(@NotNull AnimationState other) {
            set(other.startColor, other.endColor);
        }

        public void set(int startColor, int endColor) {
            this.startColor = startColor;
            this.endColor = endColor;
        }
    }

    public static final int DIRECTION_LEFT = 0;
    public static final int DIRECTION_RIGHT = 1;

    public static final int ANIM_TYPE_ARROW_TO_CLOSE = 0;
    public static final int ANIM_TYPE_VOID_TO_ARROW = 1;

    private static final int MIN_VISIBLE_ALPHA = 40;

    private static final int[] ENABLED_STATE = new int[]{android.R.attr.state_enabled};
    private static final Choreographer choreographer = Choreographer.getInstance();

    private final ColorStateList colorList;
    private final Paint paint;

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

    private AnimationState currentAnimationState;
    private AnimationState queuedAnimationState;

    private final Choreographer.FrameCallback stateChangeAnimTickCb = this::onStateChangeAnimTick;
    private final Choreographer.FrameCallback startStateChangeAnimCb = time -> {
        stateChangeStartTime = time;
        setPaintColor(currentAnimationState.startColor);

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

        color = colorList.getColorForState(ENABLED_STATE, 0);

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
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
        invStateChangeDuration = 1f / (float)(1_000_000 * millis);
    }

    private void onStateChangeAnimTick(long time) {
        // currentAnimationState can't be null, because the animation has started
        if (!currentAnimationState.isRunning && currentAnimationState.isCancelledBecauseOfQueue) {
            currentAnimationState.isRunning = true;
            currentAnimationState.isCancelledBecauseOfQueue = false;

            // if current animation state is cancelled because of another state staying in a queue, that
            // means queuedAnimationState can't be null
            currentAnimationState.set(queuedAnimationState);
            stateChangeStartTime = time;

            setPaintColor(currentAnimationState.startColor);

            choreographer.postFrameCallback(stateChangeAnimTickCb);
        } else {
            float fraction = (float) (time - stateChangeStartTime) * invStateChangeDuration;

            if (fraction >= 1f) {
                onStateChangeAnimTickFraction(1f);

                currentAnimationState.isRunning = false;
            } else {
                onStateChangeAnimTickFraction(fraction);

                choreographer.postFrameCallback(stateChangeAnimTickCb);
            }
        }
    }

    private void startStateChangeAnimation(int startColor, int endColor) {
        if (currentAnimationState != null && currentAnimationState.isRunning) {
            currentAnimationState.isRunning = false;
            currentAnimationState.isCancelledBecauseOfQueue = true;

            if (queuedAnimationState == null) {
                queuedAnimationState = new AnimationState();
            }

            queuedAnimationState.set(startColor, endColor);
        } else {
            if(currentAnimationState == null) {
                currentAnimationState = new AnimationState();
            }

            currentAnimationState.set(startColor, endColor);
            choreographer.postFrameCallback(startStateChangeAnimCb);
        }
    }

    private void onStateChangeAnimTickFraction(float fraction) {
        int c = MathUtils.colorLerp(currentAnimationState.startColor, currentAnimationState.endColor, fraction);

        setPaintColor(c);
    }

    private void setPaintColor(int color) {
        paint.setColor(color);
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

        if (animationType == ANIM_TYPE_ARROW_TO_CLOSE) {
            float line1EndY = MathUtils.lerp(midY - halfStrokeWidth + 0.5f, actualBottom, fraction);
            float line2EndY = MathUtils.lerp(midY - halfStrokeWidth, actualTop, fraction);

            float line1EndX;
            float line2EndX;
            float anchorX;

            if (direction == DIRECTION_RIGHT) {
                line1EndX = MathUtils.lerp(midX - halfStrokeWidth + 1, actualRight, fraction);
                line2EndX = MathUtils.lerp(midX + halfStrokeWidth, actualRight, fraction);
                anchorX = actualLeft;
            } else {
                line1EndX = MathUtils.lerp(midX - halfStrokeWidth, actualLeft, fraction);
                line2EndX = MathUtils.lerp(midX - halfStrokeWidth, actualLeft, fraction);
                anchorX = actualRight;
            }

            points[0] = anchorX;
            points[1] = actualTop;
            points[2] = line1EndX;
            points[3] = line1EndY;

            points[4] = anchorX;
            points[5] = actualBottom;
            points[6] = line2EndX;
            points[7] = line2EndY;
        } else {
            float anchorX = direction == DIRECTION_LEFT ? actualRight : actualLeft;

            if (fraction <= 0.5f) {
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
                points[2] = midX - halfStrokeWidth;
                points[3] = midY - halfStrokeWidth;

                points[4] = midX + halfStrokeWidth - 2.5f;
                points[5] = midY - halfStrokeWidth - 0.5f;
                points[6] = MathUtils.lerp(points[4], anchorX, sFr);
                points[7] = MathUtils.lerp(points[5], actualTop, sFr);

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

        if (oldColor != newColor) {
            color = newColor;

            startStateChangeAnimation(oldColor, newColor);

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
        float[] points = linePoints;
        if (linePointsLength == 4) {
            c.drawLine(points[0], points[1], points[2], points[3], paint);
        } else {
            c.drawLines(points, 0, linePointsLength, paint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        setPaintColor(ColorHelper.withAlpha(color, alpha));
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
