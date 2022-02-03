package io.github.pelmenstar1.rangecalendar;

final class PositiveIntRange {
    public static final long NO_INTERSECTION = IntPair.create(-1, -1);

    public static long create(int start, int end) {
        return IntPair.create(start, end);
    }

    public static int getStart(long range) {
        return IntPair.getFirst(range);
    }

    public static int getEnd(long range) {
        return IntPair.getSecond(range);
    }

    public static long findIntersection(long a, long b) {
        int aStart = getStart(a);
        int aEnd = getEnd(a);

        int bStart = getStart(b);
        int bEnd = getEnd(b);

        if (bStart > aEnd || aStart > bEnd) {
            return NO_INTERSECTION;
        }

        int start = Math.max(aStart, bStart);
        int end = Math.min(aEnd, bEnd);

        return IntPair.create(start, end);
    }

    public static boolean contains(long range, int value) {
        int start = getStart(range);
        int end = getEnd(range);

        return value >= start && value <= end;
    }
}
