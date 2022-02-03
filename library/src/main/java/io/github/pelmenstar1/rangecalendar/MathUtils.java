package io.github.pelmenstar1.rangecalendar;

final class MathUtils {
    public static float fraction(float start, float end, float fraction) {
        return start + (end - start) * fraction;
    }
}
