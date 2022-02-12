package io.github.pelmenstar1.rangecalendar;

final class ShortRange {
    public static int create(int start, int end) {
        return (end << 16) | start;
    }

    public static int getStart(int range) {
        return range & 0xFFFF;
    }

    public static int getEnd(int range) {
        return range >> 16;
    }

    public static int findIntersection(int a, int b, int invalidRange) {
        int aStart = getStart(a);
        int aEnd = getEnd(a);

        int bStart = getStart(b);
        int bEnd = getEnd(b);

        if (bStart > aEnd || aStart > bEnd) {
            return invalidRange;
        }

        int start = Math.max(aStart, bStart);
        int end = Math.min(aEnd, bEnd);

        return create(start, end);
    }

    public static boolean contains(int range, int value) {
        int start = getStart(range);
        int end = getEnd(range);

        return value >= start && value <= end;
    }
}
