package com.github.pelmenstar1.rangecalendar;

final class IntPair {
    public static long create(int first, int second) {
        return ((long) second << 32) | ((long) first & 0xffffffffL);
    }

    public static int getFirst(long pair) {
        return (int) (pair);
    }

    public static int getSecond(long pair) {
        return (int) (pair >> 32);
    }
}
