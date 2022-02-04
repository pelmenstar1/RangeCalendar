package io.github.pelmenstar1.rangecalendar;

import android.content.Context;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.NotNull;

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
    public static final int STYLE_CURRENT_MONTH_DAY_NUMBER_COLOR = 2;
    public static final int STYLE_NOT_CURRENT_MONTH_DAY_NUMBER_COLOR = 3;
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

    private static final class Payload {
        private final int type;
        private final long data;

        public Payload(int type) {
            this.type = type;
            data = 0;
        }

        public Payload(int type, long data) {
            this.type = type;
            this.data = data;
        }

        public Payload(int type, int data) {
            this.type = type;
            this.data = data;
        }

        public Payload(int type, boolean data) {
            this.type = type;
            this.data = data ? 1 : 0;
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

    // if selectionType = CELL, it stores date of selection
    // if selectionType = WEEK, it stores pair of year & month and week index
    // if selectionType = MONTH, it stores year & month
    public long selectionData;

    private final boolean isFirstDaySunday;
    private int today;

    private final CalendarInfo calendarInfo = new CalendarInfo();

    private final int[] styleData = new int[14];

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

        styleData[STYLE_SELECTION_COLOR] = cr.colorPrimary;
        styleData[STYLE_DAY_NUMBER_TEXT_SIZE] = Float.floatToIntBits(cr.dayNumberTextSize);
        styleData[STYLE_CURRENT_MONTH_DAY_NUMBER_COLOR] = cr.textColor;
        styleData[STYLE_NOT_CURRENT_MONTH_DAY_NUMBER_COLOR] = cr.textColorNotCurrentMonth;
        styleData[STYLE_DISABLED_DAY_NUMBER_COLOR] = cr.textColorDisabled;
        styleData[STYLE_TODAY_COLOR] = cr.colorPrimary;
        styleData[STYLE_WEEKDAY_COLOR] = cr.textColor;
        styleData[STYLE_WEEKDAY_TEXT_SIZE] = Float.floatToIntBits(cr.weekdayTextSize);
        styleData[STYLE_HOVER_COLOR] = cr.hoverColor;
        styleData[STYLE_HOVER_ON_SELECTION_COLOR] = cr.colorPrimaryDark;
        styleData[STYLE_RR_RADIUS_RATIO] = Float.floatToIntBits(RangeCalendarGridView.RR_RADIUS_RATIO);
        styleData[STYLE_CELL_SIZE] = Float.floatToIntBits(cr.cellSize);
        styleData[STYLE_WEEKDAY_TYPE] = WeekdayType.SHORT;
        styleData[STYLE_CLICK_ON_CELL_SELECTION_BEHAVIOR] = ClickOnCellSelectionBehavior.NONE;
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

    public float getStyleFloat(int type) {
        return Float.floatToIntBits(styleData[type]);
    }

    public void setStyleInt(int type, int value) {
        setStyleInternal(type, value);
    }

    public void setStyleFloat(int type, float value) {
        setStyleInternal(type, Float.floatToIntBits(value));
    }

    private void setStyleInternal(int type, int data) {
        styleData[type] = data;

        notifyItemRangeChanged(
                0,
                count,
                new Payload(PAYLOAD_UPDATE_STYLE, IntPair.create(type, data))
        );
    }

    private void updateStyle(
            @NotNull RangeCalendarGridView gridView,
            int type, int data
    ) {
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
            case STYLE_CURRENT_MONTH_DAY_NUMBER_COLOR:
                gridView.setCurrentMonthDayNumberColor(data);
                break;
            case STYLE_NOT_CURRENT_MONTH_DAY_NUMBER_COLOR:
                gridView.setNotCurrentMonthDayNumberColor(data);
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
                gridView.clickOnCellSelectionBehaviour = data;
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

        if(minDateEpoch > startDateEpoch) {
            startIndex = indexOfDate(minDate, 0, info);
        } else {
            startIndex = 0;
        }

        if(maxDateEpoch < endDateEpoch) {
            endIndex = indexOfDate(maxDate, startIndex, info);
        } else {
            endIndex = 42;
        }

        gridView.setEnabledCellRange(PositiveIntRange.create(startIndex, endIndex));
    }

    private void updateCurrentMonthRange(@NotNull RangeCalendarGridView gridView, @NotNull CalendarInfo info) {
        int s = info.start;
        if (isFirstDaySunday) {
            s++;
        }

        gridView.setCurrentMonthRange(IntPair.create(s, s + info.daysInMonth - 1));
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

        if(isFirstDaySunday) {
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
                RangeCalendarView.OnSelectionListener listener = onSelectionListener;
                if(listener != null) {
                    listener.onSelectionCleared();
                }
            }

            @Override
            public void onCellSelected(int index) {
                clearSelectionOnAnotherPages();

                calendarInfo.set(year, month);
                int date = getDateAtIndex(index, calendarInfo);

                selectionType = SelectionType.CELL;
                selectionData = date;

                RangeCalendarView.OnSelectionListener listener = onSelectionListener;
                if (listener != null) {
                    listener.onDaySelected(DateInt.getYear(date), DateInt.getMonth(date), DateInt.getDayOfMonth(date));
                }
            }

            @Override
            public void onWeekSelected(int weekIndex, int startIndex, int endIndex) {
                clearSelectionOnAnotherPages();

                selectionType = SelectionType.WEEK;
                selectionData = IntPair.create(ym, weekIndex);

                RangeCalendarView.OnSelectionListener listener = onSelectionListener;
                if (listener != null) {
                    calendarInfo.set(year, month);

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
            public void onMonthSelected() {
                clearSelectionOnAnotherPages();

                selectionType = SelectionType.MONTH;
                selectionData = ym;

                RangeCalendarView.OnSelectionListener listener = onSelectionListener;
                if (listener != null) {
                    listener.onMonthSelected(year, month);
                }
            }

            private void clearSelectionOnAnotherPages() {
                int selectionYm = getYearMonthForSelection(selectionType, selectionData);

                if(selectionYm != ym) {
                    int pos = getItemPositionForYearMonth(selectionYm);

                    notifyItemChanged(pos, CLEAR_SELECTION);
                }
            }
        };
    }

    public void clearSelection() {
        if(selectionType != SelectionType.NONE) {
            int pos = getItemPositionForSelection(selectionType, selectionData);

            notifyItemChanged(pos, CLEAR_SELECTION);
        }
    }

    private void updateSelection(
            @NotNull RangeCalendarGridView gridView,
            @NotNull CalendarInfo info,
            int position
    ) {
        if (getYearMonthForSelection(selectionType, selectionData) == info.ym) {
            gridView.select(transformToGridSelection(position, selectionType, selectionData, true));
        }
    }

    public int getYearMonthForSelection(int type, long data) {
        switch (type) {
            case SelectionType.CELL:
                int date = (int) data;

                return YearMonth.forDate(date);
            case SelectionType.WEEK:
            case SelectionType.MONTH:
                return (int)data;
        }

        return -1;
    }

    public int getItemPositionForSelection(int type, long data) {
        switch (type) {
            case SelectionType.CELL:
                return getItemPositionForDate((int) data);
            case SelectionType.WEEK:
            case SelectionType.MONTH:
                return getItemPositionForYearMonth((int) data);
        }

        return -1;
    }

    private int transformToGridSelection(int position, int type, long data, boolean withAnimation) {
        switch (type) {
            case SelectionType.CELL:
                calendarInfo.set(getYearMonthForCalendar(position));
                int index = indexOfDate((int) data, 0, calendarInfo);

                return RangeCalendarGridView.PackedSelectionInfo.create(
                        type, index, withAnimation
                );
            case SelectionType.WEEK:
                return RangeCalendarGridView.PackedSelectionInfo.create(
                        type, IntPair.getSecond(data), withAnimation
                );
            case SelectionType.MONTH:
                return RangeCalendarGridView.PackedSelectionInfo.create(type, 0, withAnimation);
        }
        return -1;
    }

    public void select(int type, long data, boolean withAnimation) {
        int position = getItemPositionForSelection(type, data);

        // position can be negative if selection is out of min-max range
        if(position >= 0) {
            int selectionInfo = transformToGridSelection(position, type, data, withAnimation);

            selectionType = type;
            selectionData = data;

            notifyItemChanged(position, new Payload(PAYLOAD_SELECT, selectionInfo));
        }
    }

    private void updateTodayIndex(@NotNull RangeCalendarGridView gridView, @NotNull CalendarInfo info) {
        int index = indexOfDate(today, 0, info);
        if(index >= 0) {
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
        updateSelection(gridView, calendarInfo, position);
        updateCurrentMonthRange(gridView, calendarInfo);

        for(int type = 0; type < styleData.length; type++) {
            updateStyle(gridView, type, styleData[type]);
        }

        if (getItemPositionForDate(today) == position) {
            updateTodayIndex(gridView, calendarInfo);
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
                    gridView.select((int)payload.data);

                    break;
                case PAYLOAD_UPDATE_TODAY_INDEX:
                    calendarInfo.set(getYearMonthForCalendar(position));
                    updateTodayIndex(gridView, calendarInfo);

                    break;
                case PAYLOAD_UPDATE_STYLE:
                    long packed = payload.data;
                    updateStyle(gridView, IntPair.getFirst(packed), IntPair.getSecond(packed));

                    break;
                case PAYLOAD_CLEAR_HOVER:
                    gridView.clearHoverIndex();

                    break;
                case PAYLOAD_CLEAR_SELECTION:
                    gridView.clearSelection();

                    break;
            }
        }
    }
}
