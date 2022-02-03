package io.github.pelmenstar1.rangecalendar;

final class PackedSize {
    public static long create(int width, int height) {
        return IntPair.create(width, height);
    }

    public static int getWidth(long size) {
        return IntPair.getFirst(size);
    }

    public static int getHeight(long size) {
        return IntPair.getSecond(size);
    }
}
