package io.github.pelmenstar1.rangecalendar;

final class IntRange {
    public static long create(int start, int end) {
        return IntPair.create(start, end);
    }

    public static int getStart(long range) {
        return IntPair.getFirst(range);
    }

    public static int getEnd(long range) {
        return IntPair.getSecond(range);
    }

    public static boolean contains(long range, int value) {
        int start = getStart(range);
        int end = getEnd(range);

        return value >= start && value <= end;
    }
}
