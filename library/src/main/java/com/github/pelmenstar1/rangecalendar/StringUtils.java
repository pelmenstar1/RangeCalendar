package com.github.pelmenstar1.rangecalendar;

import org.jetbrains.annotations.NotNull;

final class StringUtils {
    public static void writeTwoDigits(char @NotNull [] buffer, int offset, int number) {
        int ten = number / 10;
        int one = number - ten * 10;

        buffer[offset] = (char)('0' + ten);
        buffer[offset + 1] = (char)('0' + one);
    }
}
