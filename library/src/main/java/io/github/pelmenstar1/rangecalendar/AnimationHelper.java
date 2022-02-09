package io.github.pelmenstar1.rangecalendar;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Build;
import android.util.FloatProperty;

import org.jetbrains.annotations.NotNull;

final class AnimationHelper {
    public interface FloatCallback {
        void run(float value);
    }

    private static final float[] VALUES_0_1 = { 0f, 1f };
    private static final Float BOXED_ZERO = 0f;

    private AnimationHelper() {}

    @NotNull
    public static ValueAnimator createFractionAnimator(@NotNull final FloatCallback callback) {
        if(Build.VERSION.SDK_INT >= 24) {
            ObjectAnimator animator = new ObjectAnimator();

            animator.setProperty(new FloatProperty<Object>("property") {
                @Override
                @NotNull
                public Float get(Object object) {
                    return BOXED_ZERO;
                }

                @Override
                public void setValue(Object object, float value) {
                    callback.run(value);
                }
            });

            animator.setFloatValues(VALUES_0_1);

            return animator;
        } else {
            ValueAnimator animator = new ValueAnimator();
            animator.setFloatValues(VALUES_0_1);
            animator.addUpdateListener(animation -> callback.run((float)animation.getAnimatedValue()));

            return animator;
        }
    }
}
