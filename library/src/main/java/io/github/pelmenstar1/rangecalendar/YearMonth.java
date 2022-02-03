package io.github.pelmenstar1.rangecalendar;

final class YearMonth {
    public static int create(int year, int month) {
        return (year * 12) + (month - 1);
    }

    public static int forDate(int date) {
        return create(DateInt.getYear(date), DateInt.getMonth(date));
    }

    public static int getYear(int ym) {
        return ym / 12;
    }

    public static int getMonth(int ym) {
        return (ym % 12) + 1;
    }
}
