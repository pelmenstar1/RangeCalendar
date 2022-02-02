package com.github.pelmenstar1.rangecalendar;

import androidx.annotation.RequiresApi;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.util.Calendar;

// Some code was taken from OpenJDK
// https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/time/LocalDate.java
final class DateInt {
    public static final long MILLIS_IN_DAY = 24 * 60 * 60000;

    private static final int DAYS_PER_CYCLE = 146097;
    private static final int DAYS_0000_TO_1970 = (DAYS_PER_CYCLE * 5) - (30 * 365 + 7);

    public static final int MAX_YEAR = Short.MAX_VALUE;

    private DateInt() {
    }

    public static int create(int year, int month, int dayOfMonth) {
        return (year << 16) | (month << 8) | dayOfMonth;
    }

    public static void toCalendar(int date, @NotNull Calendar calendar) {
        calendar.set(getYear(date), getMonth(date) - 1, getDayOfMonth(date));
    }

    public static int fromCalendar(@NotNull Calendar calendar) {
        return DateInt.create(
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1, // month in Calendar is in [0; 11], so +1
                calendar.get(Calendar.DAY_OF_MONTH)
        );
    }

    @NotNull
    @RequiresApi(26)
    public static LocalDate toLocaleDate(int date) {
        return LocalDate.of(getYear(date), getMonth(date), getDayOfMonth(date));
    }

    @RequiresApi(26)
    public static int fromLocalDate(@NotNull LocalDate date) {
        return DateInt.create(
                date.getYear(),
                date.getMonthValue(),
                date.getDayOfMonth()
        );
    }

    public static int getYear(int date) {
        return (date >> 16);
    }

    public static int getMonth(int date) {
        return (date >> 8) & 0xff;
    }

    public static int getDayOfMonth(int date) {
        return date & 0xff;
    }

    public static int getDayOfWeek(int date) {
        int dow0 = (int)floorMod(toEpochDay(date) + 3, 7);

        return dow0 + 1;
    }

    public static int today() {
        return fromEpochDay(todayEpochDay());
    }

    public static int todayEpochDay() {
        return (int) (TimeUtils.currentLocalTimeMillis() / MILLIS_IN_DAY);
    }

    public static int fromEpochDay(long epochDay) {
        long zeroDay = epochDay + DAYS_0000_TO_1970;
        zeroDay -= 60;

        long yearEst = (400 * zeroDay + 591) / DAYS_PER_CYCLE;
        long doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);

        if (doyEst < 0) {
            yearEst--;
            doyEst = zeroDay - (365 * yearEst + yearEst / 4 - yearEst / 100 + yearEst / 400);
        }

        int marchDoy0 = (int)doyEst;

        int marchMonth0 = (marchDoy0 * 5 + 2) / 153;
        int month = (marchMonth0 + 2) % 12 + 1;
        int dom = marchDoy0 - (marchMonth0 * 306 + 5) / 10 + 1;
        yearEst += marchMonth0 / 10;

        return create((int)yearEst, month, dom);
    }

    public static long toEpochDay(int date) {
        int year = getYear(date);
        int month = getMonth(date);
        int day = getDayOfMonth(date);

        return toEpochDay(year, month, day);
    }

    public static long toEpochDay(int year, int month, int day) {
        long total = 0;
        total += 365 * (long)year;
        total += (year + 3) / 4 - (year + 99) / 100 + (year + 399) / 400;

        total += (367 * (long)month - 362) / 12;
        total += day - 1;

        if (month > 2) {
            total--;
            if (!TimeUtils.isLeapYear(year)) {
                total--;
            }
        }

        return (total - DAYS_0000_TO_1970);
    }

    private static long floorMod(long x, long y) {
        long r = x / y;
        long aligned = r * y;

        if ((x ^ y) < 0 && aligned != x) {
            aligned -= y;
        }

        return x - aligned;
    }

    @NotNull
    public static String toString(int date) {
        return DateInt.getDayOfMonth(date) + "." + DateInt.getMonth(date) + "." + DateInt.getYear(date);
    }
}
