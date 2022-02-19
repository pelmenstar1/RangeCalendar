package io.github.pelmenstar1.rangecalendar;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class RangeCalendarPagerAdapter extends RecyclerView.Adapter<RangeCalendarPagerAdapter.ViewHolder> {
    public static final class ViewHolder extends RecyclerView.ViewHolder {
        public final RangeCalendarGridView calendar;

        public ViewHolder(@NotNull RangeCalendarGridView calendar) {
            super(calendar);

            this.calendar = calendar;
        }
    }

    private static final class CalendarInfo {
        public int ym;
        public int year;
        public int month;
        public int start;
        public int daysInMonth;

        public void set(int ym) {
            this.ym = ym;

            int year = ym / 12;
            int month = ym - (year * 12) + 1;

            setInternal(year, month);
        }

        public void set(int year, int month) {
            this.ym = YearMonth.create(year, month);

            setInternal(year, month);
        }

        private void setInternal(int year, int month) {
            this.year = year;
            this.month = month;

            daysInMonth = TimeUtils.getDaysInMonth(year, month);
            start = DateInt.getDayOfWeek(DateInt.create(year, month, 1)) - 1;
        }
    }

    private static final int PAYLOAD_UPDATE_ENABLED_RANGE = 0;
    private static final int PAYLOAD_SELECT = 1;
    private static final int PAYLOAD_UPDATE_TODAY_INDEX = 2;
    private static final int PAYLOAD_UPDATE_STYLE = 3;
    private static final int PAYLOAD_CLEAR_HOVER = 4;
    private static final int PAYLOAD_CLEAR_SELECTION = 5;

    public static final int STYLE_SELECTION_COLOR = 0;
    public static final int STYLE_DAY_NUMBER_TEXT_SIZE = 1;
    public static final int STYLE_IN_MONTH_DAY_NUMBER_COLOR = 2;
    public static final int STYLE_OUT_MONTH_DAY_NUMBER_COLOR = 3;
    public static final int STYLE_DISABLED_DAY_NUMBER_COLOR = 4;
    public static final int STYLE_TODAY_COLOR = 5;
    public static final int STYLE_WEEKDAY_COLOR = 6;
    public static final int STYLE_WEEKDAY_TEXT_SIZE = 7;
    public static final int STYLE_HOVER_COLOR = 8;
    public static final int STYLE_HOVER_ON_SELECTION_COLOR = 9;
    public static final int STYLE_RR_RADIUS_RATIO = 10;
    public static final int STYLE_CELL_SIZE = 11;
    public static final int STYLE_WEEKDAY_TYPE = 12;
    public static final int STYLE_CLICK_ON_CELL_SELECTION_BEHAVIOR = 13;
    public static final int STYLE_COMMON_ANIMATION_DURATION = 14;
    public static final int STYLE_HOVER_ANIMATION_DURATION = 15;
    public static final int STYLE_ALLOW_CUSTOM_RANGES = 16;
    public static final int STYLE_VIBRATE_ON_SELECTING_CUSTOM_RANGE = 17;

    private static final int STYLE_OBJ_START = 32;
    public static final int STYLE_COMMON_ANIMATION_INTERPOLATOR = 32;
    public static final int STYLE_HOVER_ANIMATION_INTERPOLATOR = 33;

    private static final class Payload {
        private final int type;
        private final long data;

        private final Object dataObj;

        public Payload(int type) {
            this(type, 0L);
        }

        public Payload(int type, int data) {
            this(type, (long) data);
        }

        public Payload(int type, boolean data) {
            this(type, data ? 1 : 0);
        }

        public Payload(int type, @Nullable Object dataObj) {
            this(type, 0, dataObj);
        }

        public Payload(int type, long data) {
            this.type = type;
            this.data = data;
            dataObj = null;
        }

        public Payload(int type, long data, @Nullable Object dataObj) {
            this.type = type;
            this.data = data;
            this.dataObj = dataObj;
        }

        public boolean bool() {
            return data == 1;
        }
    }

    private static final Payload CLEAR_HOVER = new Payload(PAYLOAD_CLEAR_HOVER);
    private static final Payload UPDATE_ENABLED_RANGE = new Payload(PAYLOAD_UPDATE_ENABLED_RANGE);
    private static final Payload CLEAR_SELECTION = new Payload(PAYLOAD_CLEAR_SELECTION);

    private static final int PAGES_BETWEEN_ABS_MIN_MAX;

    private int count = PAGES_BETWEEN_ABS_MIN_MAX;
    private final CalendarResources cr;

    private int minDateYm;

    private int minDate = RangeCalendarView.MIN_DATE;
    private int maxDate = RangeCalendarView.MAX_DATE;

    private long minDateEpoch = RangeCalendarView.MIN_DATE_EPOCH;
    private long maxDateEpoch = RangeCalendarView.MAX_DATE_EPOCH;

    public int selectionType;

    // if selectionType = CELL, index of selected date is stored
    // if selectionType = WEEK, week index is stored
    // if selectionType = MONTH, nothing is stored
    // if selectionType = CUSTOM, start and end index of range are stored
    public int selectionData;

    // We need to save year-month of selection despite the fact we can compute it from selectionData
    // because computed position will point to the page where the position is in currentMonthRange
    // and this can lead to bugs when we mutate the wrong page.
    public int selectionYm;

    private final boolean isFirstDaySunday;
    private int today;

    private final CalendarInfo calendarInfo = new CalendarInfo();

    private final int[] styleData = new int[18];
    private final Object[] styleObjData = new Object[2];

    private RangeCalendarView.OnSelectionListener onSelectionListener;

    static {
        int absMin = RangeCalendarView.MIN_DATE;
        int absMax = RangeCalendarView.MAX_DATE;

        PAGES_BETWEEN_ABS_MIN_MAX = YearMonth.forDate(absMax) - YearMonth.forDate(absMin) + 1;
    }

    public RangeCalendarPagerAdapter(
            @NotNull Context context,
            @NotNull CalendarResources cr,
            boolean isFirstDaySunday
    ) {
        this.cr = cr;
        this.isFirstDaySunday = isFirstDaySunday;

        initStyle(STYLE_SELECTION_COLOR, cr.colorPrimary);
        initStyle(STYLE_DAY_NUMBER_TEXT_SIZE, cr.dayNumberTextSize);
        initStyle(STYLE_IN_MONTH_DAY_NUMBER_COLOR, cr.textColor);
        initStyle(STYLE_OUT_MONTH_DAY_NUMBER_COLOR, cr.outMonthTextColor);
        initStyle(STYLE_DISABLED_DAY_NUMBER_COLOR, cr.disabledTextColor);
        initStyle(STYLE_TODAY_COLOR, cr.colorPrimary);

        initStyle(STYLE_WEEKDAY_COLOR, cr.textColor);
        initStyle(STYLE_WEEKDAY_TEXT_SIZE, cr.weekdayTextSize);
        initStyle(STYLE_WEEKDAY_TYPE, WeekdayType.SHORT);

        initStyle(STYLE_HOVER_COLOR, cr.hoverColor);
        initStyle(STYLE_HOVER_ON_SELECTION_COLOR, cr.colorPrimaryDark);

        initStyle(STYLE_RR_RADIUS_RATIO, RangeCalendarGridView.DEFAULT_RR_RADIUS_RATIO);
        initStyle(STYLE_CELL_SIZE, cr.cellSize);
        initStyle(STYLE_CLICK_ON_CELL_SELECTION_BEHAVIOR, ClickOnCellSelectionBehavior.NONE);

        initStyle(STYLE_COMMON_ANIMATION_DURATION, RangeCalendarGridView.DEFAULT_COMMON_ANIM_DURATION);
        initStyle(STYLE_COMMON_ANIMATION_INTERPOLATOR, TimeInterpolators.LINEAR);

        initStyle(STYLE_HOVER_ANIMATION_DURATION, RangeCalendarGridView.DEFAULT_HOVER_ANIM_DURATION);
        initStyle(STYLE_HOVER_ANIMATION_INTERPOLATOR, TimeInterpolators.LINEAR);

        initStyle(STYLE_ALLOW_CUSTOM_RANGES, true);
        initStyle(STYLE_VIBRATE_ON_SELECTING_CUSTOM_RANGE, true);
    }

    private void initStyle(int type, boolean data) {
        styleData[type] = data ? 1 : 0;
    }

    private void initStyle(int type, int data) {
        styleData[type] = data;
    }

    private void initStyle(int type, float data) {
        initStyle(type, Float.floatToIntBits(data));
    }

    private void initStyle(int type, Object data) {
        styleObjData[type - STYLE_OBJ_START] = data;
    }

    public void setOnSelectionListener(@NotNull RangeCalendarView.OnSelectionListener value) {
        this.onSelectionListener = value;
    }

    public void setToday(int date) {
        today = date;

        int position = getItemPositionForDate(date);

        notifyItemChanged(position, new Payload(PAYLOAD_UPDATE_TODAY_INDEX, date));
    }

    public int getStyleInt(int type) {
        return styleData[type];
    }

    public boolean getStyleBool(int type) {
        return styleData[type] == 1;
    }

    public float getStyleFloat(int type) {
        return Float.floatToIntBits(styleData[type]);
    }

    @SuppressWarnings("unchecked")
    public <T> T getStyleObject(int type) {
        return (T) styleObjData[type - STYLE_OBJ_START];
    }

    public void setStyleInt(int type, int value) {
        setStyleInt(type, value, true);
    }

    public void setStyleInt(int type, int value, boolean notify) {
        styleData[type] = value;

        if (notify) {
            notifyItemRangeChanged(
                    0,
                    count,
                    new Payload(PAYLOAD_UPDATE_STYLE, IntPair.create(type, value))
            );
        }
    }

    public void setStyleBool(int type, boolean value) {
        setStyleBool(type, value, true);
    }

    public void setStyleBool(int type, boolean value, boolean notify) {
        setStyleInt(type, value ? 1 : 0, notify);
    }

    public void setStyleFloat(int type, float value) {
        setStyleFloat(type, value, true);
    }

    public void setStyleFloat(int type, float value, boolean notify) {
        setStyleInt(type, Float.floatToIntBits(value), notify);
    }

    public void setStyleObject(int type, Object data) {
        styleObjData[type - STYLE_OBJ_START] = data;

        notifyItemRangeChanged(
                0,
                count,
                new Payload(PAYLOAD_UPDATE_STYLE, type, data)
        );
    }

    private void updateStyle(
            @NotNull RangeCalendarGridView gridView,
            int type, int data
    ) {
        boolean b = data == 1;
        float f = Float.intBitsToFloat(data);

        switch (type) {
            case STYLE_DAY_NUMBER_TEXT_SIZE:
                gridView.setDayNumberTextSize(f);
                break;
            case STYLE_WEEKDAY_TEXT_SIZE:
                gridView.setDayNameTextSize(f);
                break;

            case STYLE_SELECTION_COLOR:
                gridView.setSelectionColor(data);
                break;
            case STYLE_IN_MONTH_DAY_NUMBER_COLOR:
                gridView.setInMonthDayNumberColor(data);
                break;
            case STYLE_OUT_MONTH_DAY_NUMBER_COLOR:
                gridView.setOutMonthDayNumberColor(data);
                break;
            case STYLE_DISABLED_DAY_NUMBER_COLOR:
                gridView.setDisabledDayNumberColor(data);
                break;
            case STYLE_TODAY_COLOR:
                gridView.setTodayColor(data);
                break;
            case STYLE_WEEKDAY_COLOR:
                gridView.setDayNameColor(data);
                break;
            case STYLE_HOVER_COLOR:
                gridView.setHoverColor(data);
                break;
            case STYLE_HOVER_ON_SELECTION_COLOR:
                gridView.setHoverOnSelectionColor(data);
                break;

            case STYLE_RR_RADIUS_RATIO:
                gridView.setRoundRectRadiusRatio(f);
                break;
            case STYLE_CELL_SIZE:
                gridView.setCellSize(f);
                break;
            case STYLE_WEEKDAY_TYPE:
                gridView.setWeekdayType(data);
                break;
            case STYLE_CLICK_ON_CELL_SELECTION_BEHAVIOR:
                gridView.clickOnCellSelectionBehavior = data;
                break;
            case STYLE_COMMON_ANIMATION_DURATION:
                gridView.commonAnimationDuration = data;
                break;
            case STYLE_HOVER_ANIMATION_DURATION:
                gridView.hoverAnimationDuration = data;
                break;

            case STYLE_ALLOW_CUSTOM_RANGES:
                gridView.setAllowCustomRanges(b);
                break;
            case STYLE_VIBRATE_ON_SELECTING_CUSTOM_RANGE:
                gridView.vibrateOnSelectingCustomRange = b;
                break;
        }
    }

    private void updateStyle(
            @NotNull RangeCalendarGridView gridView,
            int type, Object data
    ) {
        switch (type) {
            case STYLE_COMMON_ANIMATION_INTERPOLATOR:
                gridView.commonAnimationInterpolator = (TimeInterpolator) data;
                break;
            case STYLE_HOVER_ANIMATION_INTERPOLATOR:
                gridView.hoverAnimationInterpolator = (TimeInterpolator) data;
                break;
        }
    }

    public void setRange(int minDate, long minDateEpoch, int maxDate, long maxDateEpoch) {
        this.minDate = minDate;
        this.maxDate = maxDate;

        this.minDateEpoch = minDateEpoch;
        this.maxDateEpoch = maxDateEpoch;

        int oldCount = count;

        minDateYm = YearMonth.forDate(minDate);
        count = YearMonth.forDate(maxDate) - minDateYm + 1;

        if (oldCount == count) {
            notifyItemRangeChanged(0, count, UPDATE_ENABLED_RANGE);
        } else {
            notifyDataSetChanged();
        }
    }

    public int getYearMonthForCalendar(int position) {
        return minDateYm + position;
    }

    public void clearHoverAt(int position) {
        notifyItemChanged(position, CLEAR_HOVER);
    }

    private void updateEnabledRange(@NotNull RangeCalendarGridView gridView, @NotNull CalendarInfo info) {
        int year = info.year;
        int month = info.month;

        int start = info.start;

        int prevYear = year;
        int prevMonth = month - 1;
        if (prevMonth == 0) {
            prevMonth = 12;
            prevYear--;
        }

        int daysInPrevMonth = TimeUtils.getDaysInMonth(prevYear, prevMonth);

        long startDateEpoch = DateInt.toEpochDay(prevYear, prevMonth, daysInPrevMonth - start - 1);
        long endDateEpoch = startDateEpoch + 42;

        int startIndex;
        int endIndex;

        if (minDateEpoch > startDateEpoch) {
            startIndex = indexOfDate(minDate, 0, info);
        } else {
            startIndex = 0;
        }

        if (maxDateEpoch < endDateEpoch) {
            endIndex = indexOfDate(maxDate, startIndex, info);
        } else {
            endIndex = 42;
        }

        gridView.setEnabledCellRange(ShortRange.create(startIndex, endIndex));
    }

    private void updateInMonthRange(@NotNull RangeCalendarGridView gridView, @NotNull CalendarInfo info) {
        int s = info.start;
        if (isFirstDaySunday) {
            s++;
        }

        gridView.setInMonthRange(ShortRange.create(s, s + info.daysInMonth - 1));
    }

    public int indexOfDate(int date, int offset, @NotNull CalendarInfo info) {
        for (int i = offset; i < 42; i++) {
            if (getDateAtIndex(i, info) == date) {
                return i;
            }
        }

        return -1;
    }

    public int getDateAtIndex(int index, @NotNull CalendarInfo info) {
        int year = info.year;
        int month = info.month;
        int start = info.start;
        int daysInMonth = info.daysInMonth;

        int monthEnd = start + daysInMonth - 1;

        if (isFirstDaySunday) {
            start++;
        }

        if (index < start) {
            int y = year;
            int m = month - 1;

            if (m == 0) {
                m = 12;
                y--;
            }

            int day = TimeUtils.getDaysInMonth(y, m) - start + index + 1;

            return DateInt.create(y, m, day);
        } else if (index <= monthEnd) {
            int day = index - start + 1;

            return DateInt.create(year, month, day);
        } else {
            int day = index - monthEnd;
            int y = year;
            int m = month + 1;

            if (m == 13) {
                m = 1;
                y++;
            }

            return DateInt.create(y, m, day);
        }
    }

    public int getItemPositionForDate(int date) {
        return getItemPositionForYearMonth(YearMonth.forDate(date));
    }

    public int getItemPositionForYearMonth(int ym) {
        return ym - minDateYm;
    }

    private void updateGrid(@NotNull RangeCalendarGridView gridView, @NotNull CalendarInfo info) {
        int year = info.year;
        int month = info.month;
        int start = info.start;
        int daysInMonth = info.daysInMonth;

        int prevYear = year;
        int prevMonth = month - 1;
        if (prevMonth == 0) {
            prevYear--;
            prevMonth = 12;
        }

        int daysInPrevMonth = TimeUtils.getDaysInMonth(prevYear, prevMonth);

        if (isFirstDaySunday) {
            start++;
        }

        byte[] cells = gridView.cells;

        for (int i = 0; i < start; i++) {
            int day = daysInPrevMonth - i;
            int index = start - i - 1;

            cells[index] = (byte) day;
        }

        for (int i = 0; i < daysInMonth; i++) {
            int index = start + i;
            int day = i + 1;

            cells[index] = (byte) day;
        }

        int thisMonthEnd = start + daysInMonth;
        for (int i = 0; i < 42 - thisMonthEnd; i++) {
            int index = thisMonthEnd + i;
            int day = i + 1;

            cells[index] = (byte) day;
        }

        gridView.onGridChanged();
    }

    @NotNull
    private RangeCalendarGridView.OnSelectionListener createRedirectSelectionListener(
            int position,
            int year, int month
    ) {
        int ym = YearMonth.create(year, month);

        return new RangeCalendarGridView.OnSelectionListener() {
            @Override
            public void onSelectionCleared() {
                discardSelectionValues();

                RangeCalendarView.OnSelectionListener listener = onSelectionListener;
                if (listener != null) {
                    listener.onSelectionCleared();
                }
            }

            @Override
            public void onCellSelected(int index) {
                clearSelectionOnAnotherPages();

                setSelectionValues(SelectionType.CELL, index, ym);

                RangeCalendarView.OnSelectionListener listener = onSelectionListener;
                if (listener != null) {
                    calendarInfo.set(ym);
                    int date = getDateAtIndex(index, calendarInfo);

                    listener.onDaySelected(DateInt.getYear(date), DateInt.getMonth(date), DateInt.getDayOfMonth(date));
                }
            }

            @Override
            public void onWeekSelected(int weekIndex, int startIndex, int endIndex) {
                clearSelectionOnAnotherPages();

                setSelectionValues(SelectionType.WEEK, weekIndex, ym);

                RangeCalendarView.OnSelectionListener listener = onSelectionListener;
                if (listener != null) {
                    calendarInfo.set(ym);

                    int startDate = getDateAtIndex(startIndex, calendarInfo);
                    int endDate = getDateAtIndex(endIndex, calendarInfo);

                    listener.onWeekSelected(
                            weekIndex,
                            DateInt.getYear(startDate), DateInt.getMonth(startDate), DateInt.getDayOfMonth(startDate),
                            DateInt.getYear(endDate), DateInt.getMonth(endDate), DateInt.getDayOfMonth(endDate)
                    );
                }
            }

            @Override
            public void onCustomRangeSelected(int startIndex, int endIndex) {
                clearSelectionOnAnotherPages();

                setSelectionValues(SelectionType.CUSTOM, ShortRange.create(startIndex, endIndex), ym);

                RangeCalendarView.OnSelectionListener listener = onSelectionListener;
                if (listener != null) {
                    calendarInfo.set(ym);

                    int startDate = getDateAtIndex(startIndex, calendarInfo);
                    int endDate = getDateAtIndex(endIndex, calendarInfo);

                    listener.onCustomRangeSelected(
                            DateInt.getYear(startDate), DateInt.getMonth(startDate), DateInt.getDayOfMonth(startDate),
                            DateInt.getYear(endDate), DateInt.getMonth(endDate), DateInt.getDayOfMonth(endDate)
                    );
                }
            }

            // should be called before changing selection values
            private void clearSelectionOnAnotherPages() {
                if (selectionYm != ym) {
                    clearSelection(false);
                }
            }
        };
    }

    private void setSelectionValues(int type, int data, int ym) {
        selectionType = type;
        selectionData = data;
        selectionYm = ym;
    }

    private void discardSelectionValues() {
        setSelectionValues(SelectionType.NONE, 0, 0);
    }

    public void clearSelection() {
        clearSelection(true);
    }

    private void clearSelection(boolean fireEvent) {
        if (selectionType != SelectionType.NONE) {
            int position = getItemPositionForYearMonth(selectionYm);

            discardSelectionValues();
            notifyItemChanged(position, CLEAR_SELECTION);

            if (fireEvent) {
                RangeCalendarView.OnSelectionListener listener = onSelectionListener;
                if (listener != null) {
                    listener.onSelectionCleared();
                }
            }
        }
    }

    // if type = CELL, data is date,
    // if type = WEEK, data is pair of year-month and weekIndex,
    // if type = MONTH, data is year-month.
    // NOTE, that this year-month will point to the page where selection is (partially) in currentMonthRange
    public int getYearMonthForSelection(int type, long data) {
        switch (type) {
            case SelectionType.CELL:
                int date = (int) data;

                return YearMonth.forDate(date);
            // in both cases year-month is stored in low 32-bits
            case SelectionType.WEEK:
            case SelectionType.MONTH:
                return (int) data;
            case SelectionType.CUSTOM:
                int startDate = IntRange.getStart(data);

                return YearMonth.forDate(startDate);
        }

        return -1;
    }

    // if type = CELL, data is date,
    // if type = WEEK, data is week index,
    // if type = MONTH, data is unused.
    // if type = CUSTOM, data is date int range
    private long transformToGridSelection(int position, int type, long data, boolean withAnimation) {
        switch (type) {
            case SelectionType.CELL:
                calendarInfo.set(getYearMonthForCalendar(position));
                int index = indexOfDate((int) data, 0, calendarInfo);

                return RangeCalendarGridView.PackedSelectionInfo.create(
                        type, index, withAnimation
                );
            case SelectionType.WEEK:
                return RangeCalendarGridView.PackedSelectionInfo.create(type, IntPair.getSecond(data), withAnimation);
            case SelectionType.MONTH:
                return RangeCalendarGridView.PackedSelectionInfo.create(type, 0, withAnimation);
            case SelectionType.CUSTOM:
                calendarInfo.set(getYearMonthForCalendar(position));

                int startIndex = indexOfDate(IntRange.getStart(data), 0, calendarInfo);
                int endIndex = indexOfDate(IntRange.getEnd(data), startIndex, calendarInfo);

                return RangeCalendarGridView.PackedSelectionInfo.create(
                        type, ShortRange.create(startIndex, endIndex), withAnimation
                );
        }

        return 0;
    }

    // if type = CELL, data is date
    // if type = WEEK, data is pair of year-month and weekIndex
    // if type = MONTH, data is year-month
    // if type = CUSTOM, data is date int range
    public void select(int type, long data, boolean withAnimation) {
        int ym = getYearMonthForSelection(type, data);
        int position = getItemPositionForYearMonth(ym);

        // position can be negative if selection is out of min-max range
        if (position >= 0) {
            long gridSelectionInfo = transformToGridSelection(position, type, data, withAnimation);

            if (type == SelectionType.MONTH) {
                onMonthSelected(selectionYm, ym);
            } else if(type == SelectionType.CUSTOM) {
                verifyCustomRange(data);
            }

            setSelectionValues(type, RangeCalendarGridView.PackedSelectionInfo.getData(gridSelectionInfo), ym);

            notifyItemChanged(position, new Payload(PAYLOAD_SELECT, gridSelectionInfo));
        }
    }

    // if type = CELL, data is index of the cell
    // if type = WEEK, data is week index
    // if type = MONTH, data is unused
    // if type = CUSTOM, data is start and end indices of the range
    // special case for RangeCalendarView.onRestoreInstanceState
    public void select(int ym, int type, int data, boolean withAnimation) {
        int position = getItemPositionForYearMonth(ym);

        if (type == SelectionType.MONTH) {
            onMonthSelected(selectionYm, ym);
        }

        setSelectionValues(type, data, ym);

        notifyItemChanged(position, new Payload(
                PAYLOAD_SELECT,
                RangeCalendarGridView.PackedSelectionInfo.create(type, data, withAnimation)
        ));
    }

    private void verifyCustomRange(long range) {
        int start = IntRange.getStart(range);
        int end = IntRange.getEnd(range);

        if(YearMonth.forDate(start) != YearMonth.forDate(end)) {
            throw new IllegalArgumentException("Calendar page position for start date of the range differ from calendar page position for the end");
        }
    }

    private void onMonthSelected(int prevYm, int ym) {
        if (prevYm != ym) {
            clearSelection();
        }

        RangeCalendarView.OnSelectionListener listener = onSelectionListener;
        if (listener != null) {
            listener.onMonthSelected(
                    YearMonth.getYear(ym),
                    YearMonth.getMonth(ym)
            );
        }
    }

    private void updateTodayIndex(@NotNull RangeCalendarGridView gridView, @NotNull CalendarInfo info) {
        int index = indexOfDate(today, 0, info);
        if (index >= 0) {
            gridView.setTodayIndex(index);
        }
    }

    @Override
    public int getItemCount() {
        return count;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        RangeCalendarGridView gridView = new RangeCalendarGridView(parent.getContext(), cr);
        gridView.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.MATCH_PARENT
        ));

        return new ViewHolder(gridView);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int ym = getYearMonthForCalendar(position);
        int year = YearMonth.getYear(ym);
        int month = YearMonth.getMonth(ym);

        calendarInfo.set(year, month);

        RangeCalendarGridView gridView = holder.calendar;
        gridView.isFirstDaySunday = isFirstDaySunday;
        gridView.setOnSelectionListener(createRedirectSelectionListener(position, year, month));
        updateGrid(gridView, calendarInfo);
        updateEnabledRange(gridView, calendarInfo);
        updateInMonthRange(gridView, calendarInfo);

        for (int type = 0; type < styleData.length; type++) {
            updateStyle(gridView, type, styleData[type]);
        }

        for (int type = 0; type < styleObjData.length; type++) {
            updateStyle(gridView, type + STYLE_OBJ_START, styleObjData[type]);
        }

        if (getItemPositionForDate(today) == position) {
            updateTodayIndex(gridView, calendarInfo);
        }

        if (selectionYm == ym) {
            gridView.select(selectionType, selectionData, true);
        }

        gridView.year = year;
        gridView.month = month;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        RangeCalendarGridView gridView = holder.calendar;

        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
        } else {
            Payload payload = (Payload) payloads.get(0);

            switch (payload.type) {
                case PAYLOAD_UPDATE_ENABLED_RANGE:
                    calendarInfo.set(getYearMonthForCalendar(position));
                    updateEnabledRange(gridView, calendarInfo);

                    break;
                case PAYLOAD_SELECT:
                    gridView.select(payload.data);

                    break;
                case PAYLOAD_UPDATE_TODAY_INDEX:
                    calendarInfo.set(getYearMonthForCalendar(position));
                    updateTodayIndex(gridView, calendarInfo);

                    break;
                case PAYLOAD_UPDATE_STYLE:
                    long packed = payload.data;
                    int type = IntPair.getFirst(packed);
                    int value = IntPair.getSecond(packed);

                    if (type >= STYLE_OBJ_START) {
                        updateStyle(gridView, type, payload.dataObj);
                    } else {
                        updateStyle(gridView, type, value);
                    }

                    break;
                case PAYLOAD_CLEAR_HOVER:
                    gridView.clearHoverIndex();

                    break;
                case PAYLOAD_CLEAR_SELECTION:
                    // Don't fire event here. If it's needed, it will be fired in clearSelection()
                    gridView.clearSelection(false, true);

                    break;
            }
        }
    }
}
