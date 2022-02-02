package com.github.pelmenstar1.rangecalendar;

import android.view.View;

import org.jetbrains.annotations.NotNull;

/**
 * Bridge interface that binds selection to internal {@link android.view.View}.
 */
public interface CalendarSelectionViewBridge {
    /**
     * Binds day selection to {@link android.view.View}.
     *
     * @param month month, 1-based
     * @param day day of month, 1-based
     */
    void bindOnDaySelection(int year, int month, int day);

    /**
     * Binds week selection to {@link android.view.View}.
     *
     * @param weekIndex index of week, 0-based
     * @param startYear year of range's start
     * @param startMonth month of range's start, 1-based
     * @param startDay day of month of range's start, 1-based
     *
     * @param endYear year of range's end
     * @param endMonth month of range's end, 1-based
     * @param endDay day of month of range's end, 1-based
     */
    void bindOnWeekSelection(
            int weekIndex,
            int startYear, int startMonth, int startDay,
            int endYear, int endMonth, int endDay
    );

    /**
     * Binds month selection to {@link android.view.View}.
     *
     * @param year year of selection
     * @param month month of selection, 1-based
     */
    void bindOnMonthSelection(int year, int month);
}
