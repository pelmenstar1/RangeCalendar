package com.github.pelmenstar1.rangecalendar;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.TypedValue;

import androidx.annotation.AttrRes;

import org.jetbrains.annotations.NotNull;

import java.text.DateFormatSymbols;
import java.util.Locale;

final class CalendarResources {
    private static final int[] SINGLE_INT_ARRAY = new int[1];
    private static final int[] HOVER_STATE = new int[] { android.R.attr.state_hovered, android.R.attr.state_enabled };
    private static final int[] ENABLED_STATE = new int[] { android.R.attr.state_enabled };
    private static final int[] EMPTY_STATE = new int[0];

    public static final int SHORT_WEEKDAYS_OFFSET = 0;
    public static final int NARROW_WEEKDAYS_OFFSET = 7;

    public final long[] dayNumberSizes;
    public final int[] weekdayWidths;

    public final float dayNumberTextSize;
    public final float hPadding;

    public final int colorPrimary;
    public final int colorPrimaryDark;

    public final float cellSize;
    public final float yCellMargin;

    public final int hoverColor;

    public final int textColor;
    public final int textColorNotCurrentMonth;
    public final int textColorDisabled;

    public final float weekdayTextSize;

    public final @NotNull String @NotNull [] weekdays;

    public final float shortWeekdayRowHeight;
    public final float narrowWeekdayRowHeight;

    public final float weekdayRowMarginBottom;

    @NotNull
    public final ColorStateList colorControlNormal;

    public CalendarResources(@NotNull Context context) {
        Resources res = context.getResources();
        Resources.Theme theme = context.getTheme();

        TypedValue cachedValue = new TypedValue();

        colorPrimary = getColorPrimary(context, cachedValue);
        colorPrimaryDark = ColorHelper.makeDarkerColor(colorPrimary, 0.4f);

        textColor = getTextColor(context);

        colorControlNormal = getColorStateListFromAttribute(context, R.attr.colorControlNormal);

        textColorNotCurrentMonth = colorControlNormal.getColorForState(ENABLED_STATE, 0);
        textColorDisabled = colorControlNormal.getColorForState(EMPTY_STATE, 0);

        hoverColor = getHoverColor(context);

        hPadding = res.getDimension(R.dimen.rangeCalendar_paddingH);
        weekdayTextSize = res.getDimension(R.dimen.rangeCalendar_weekdayTextSize);
        dayNumberTextSize = res.getDimension(R.dimen.rangeCalendar_dayNumberTextSize);
        cellSize = res.getDimension(R.dimen.rangeCalendar_cellSize);
        yCellMargin = res.getDimension(R.dimen.rangeCalendar_yCellMargin);
        weekdayRowMarginBottom = res.getDimension(R.dimen.rangeCalendar_weekdayRowMarginBottom);

        // Compute text size of numbers in [0; 31]
        dayNumberSizes = new long[31];
        char[] buffer = new char[2];

        for (int i = 0; i < 31; i++) {
            int textLength;
            int day = i + 1;

            if (day < 10) {
                textLength = 1;
                buffer[0] = (char) ('0' + day);
            } else {
                textLength = 2;

                StringUtils.writeTwoDigits(buffer, 0, day);
            }

            dayNumberSizes[i] = TextUtils.getTextBounds(buffer, 0, textLength, weekdayTextSize);
        }

        // First element in getShortWeekDays() is empty and actual items start from 1
        // It's better to copy them to another array where elements start from 0
        Locale locale = LocaleUtils.getLocale(context);

        String[] shortWeekdays;
        String[] narrowWeekdays = null;

        if(Build.VERSION.SDK_INT >= 24) {
            android.icu.text.DateFormatSymbols symbols = android.icu.text.DateFormatSymbols.getInstance(locale);

            shortWeekdays = symbols.getShortWeekdays();
            narrowWeekdays = symbols.getWeekdays(
                    android.icu.text.DateFormatSymbols.FORMAT,
                    android.icu.text.DateFormatSymbols.NARROW
            );
        } else {
            shortWeekdays = DateFormatSymbols.getInstance(locale).getShortWeekdays();
        }

        int weekdaysLength;
        if(Build.VERSION.SDK_INT >= 24) {
            weekdaysLength = 14;
        } else {
            weekdaysLength = 7;
        }

        weekdays = new String[weekdaysLength];
        System.arraycopy(shortWeekdays, 1, weekdays, 0, 7);
        if(Build.VERSION.SDK_INT >= 24) {
            System.arraycopy(narrowWeekdays, 1, weekdays, 7, 7);
        }

        weekdayWidths = new int[weekdaysLength];

        shortWeekdayRowHeight = computeWeekdayWidthAndMaxHeight(SHORT_WEEKDAYS_OFFSET);
        narrowWeekdayRowHeight = computeWeekdayWidthAndMaxHeight(NARROW_WEEKDAYS_OFFSET);
    }

    private float computeWeekdayWidthAndMaxHeight(int offset) {
        int maxHeight = -1;
        for(int i = offset; i < offset + 7; i++) {
            String name = weekdays[i];

            long size = TextUtils.getTextBounds(name, weekdayTextSize);
            int height = PackedSize.getHeight(size);

            if (height > maxHeight) {
                maxHeight = height;
            }

            weekdayWidths[i] = PackedSize.getWidth(size);
        }

        return (float)maxHeight;
    }

    private static int getColorPrimary(@NotNull Context context, @NotNull TypedValue cachedValue) {
        return getColorFromAttribute(context, R.attr.colorPrimary, cachedValue);
    }

    private static int getTextColor(@NotNull Context context) {
        Resources.Theme theme = context.getTheme();

        TypedArray array = theme.obtainStyledAttributes(R.style.TextAppearance_AppCompat, R.styleable.TextAppearance);
        ColorStateList colorList = array.getColorStateList(R.styleable.TextAppearance_android_textColor);
        array.recycle();

        return colorList.getColorForState(ENABLED_STATE, 0);
    }

    private static int getHoverColor(@NotNull Context context) {
        ColorStateList hoverList = getColorStateListFromAttribute(context, R.attr.colorControlHighlight);

        return hoverList.getColorForState(HOVER_STATE, 0);
    }

    private static int getColorFromAttribute(
            @NotNull Context context,
            @AttrRes int resId,
            @NotNull TypedValue cachedValue
    ) {
        Resources.Theme theme = context.getTheme();

        if(theme.resolveAttribute(resId, cachedValue, true)) {
            int type = cachedValue.type;
            if (type >= TypedValue.TYPE_FIRST_COLOR_INT && type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return cachedValue.data;
            } else {
                return context.getResources().getColor(cachedValue.resourceId);
            }
        }

        throw new IllegalArgumentException("Attribute " + resId + " isn't defined");
    }

    @NotNull
    private static ColorStateList getColorStateListFromAttribute(
            @NotNull Context context,
            @AttrRes int resId
    ) {
        SINGLE_INT_ARRAY[0] = resId;
        TypedArray array = context.obtainStyledAttributes(SINGLE_INT_ARRAY);

        ColorStateList color = array.getColorStateList(0);
        array.recycle();

        return color;
    }
}
