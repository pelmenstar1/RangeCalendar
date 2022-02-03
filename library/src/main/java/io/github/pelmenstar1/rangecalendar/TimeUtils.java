package io.github.pelmenstar1.rangecalendar;

import java.util.TimeZone;

final class TimeUtils {
    public static int getDaysInMonth(int year, int month) {
        switch (month) {
            case 2:
                return (isLeapYear(year) ? 29 : 28);
            case 4:
            case 6:
            case 9:
            case 11:
                return 30;
            default:
                return 31;
        }
    }

    public static boolean isLeapYear(int year) {
        return (year & 3) == 0 && (year % 100 != 0 || year % 400 == 0);
    }

    public static long currentLocalTimeMillis() {
        // by default System.currentTimeMillis() returns time in UTC

        long millis = System.currentTimeMillis();
        int offset = TimeZone.getDefault().getOffset(millis);

        return millis + offset;
    }
}
