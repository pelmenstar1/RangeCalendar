package io.github.pelmenstar1.rangecalendar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.format.DateFormat;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

@SuppressLint("ViewConstructor")
final class RangeCalendarGridView extends View {
    public interface OnSelectionListener {
        void onSelectionCleared();

        void onCellSelected(int index);

        void onWeekSelected(int weekIndex, int startIndex, int endIndex);

        void onCustomRangeSelected(int startIndex, int endIndex);
    }

    private static final class Radii {
        private static final float[] value = new float[8];
        private static int flags = 0;

        private static final int LT_SHIFT = 0;
        private static final int RT_SHIFT = 1;
        private static final int RB_SHIFT = 2;
        private static final int LB_SHIFT = 3;

        public static void clear() {
            flags = 0;
        }

        private static void setFlag(int shift) {
            flags |= (1 << shift);
        }

        private static void setFlag(int shift, boolean condition) {
            flags |= ((condition ? 1 : 0) << shift);
        }

        // left top
        public static void lt() {
            setFlag(LT_SHIFT);
        }

        public static void lt(boolean condition) {
            setFlag(LT_SHIFT, condition);
        }

        // right top
        public static void rt() {
            setFlag(RT_SHIFT);
        }

        public static void rt(boolean condition) {
            setFlag(RT_SHIFT, condition);
        }

        // right bottom
        public static void rb() {
            setFlag(RB_SHIFT);
        }

        public static void rb(boolean condition) {
            setFlag(RB_SHIFT, condition);
        }

        // left bottom
        public static void lb() {
            setFlag(LB_SHIFT);
        }

        public static void lb(boolean condition) {
            setFlag(LB_SHIFT, condition);
        }

        private static int createMask(int shift) {
            // The idea: to create mask which is 0xFFFFFFFF if bit at specified position (shift) is set, otherwise mask is 0
            return 0xFFFFFFFF * ((flags >> shift) & 1);
        }

        public static float @NotNull [] result(float radius) {
            int radiusBits = Float.floatToRawIntBits(radius);

            value[0] = value[1] = Float.intBitsToFloat(radiusBits & createMask(LT_SHIFT));
            value[2] = value[3] = Float.intBitsToFloat(radiusBits & createMask(RT_SHIFT));
            value[4] = value[5] = Float.intBitsToFloat(radiusBits & createMask(RB_SHIFT));
            value[6] = value[7] = Float.intBitsToFloat(radiusBits & createMask(LB_SHIFT));

            return value;
        }
    }

    public static final class PackedSelectionInfo {
        public static long create(int type, int data, boolean withAnimation) {
            return (long) data << 32 | (long) type << 24 | ((withAnimation ? 1 : 0) << 16);
        }

        public static int getType(long packed) {
            return (int) (packed >> 24) & 0xFF;
        }

        public static int getData(long packed) {
            return (int) (packed >> 32);
        }

        public static boolean getWithAnimation(long packed) {
            return ((packed >> 16) & 0xFF) == 1;
        }
    }

    private static final class TouchHelper extends ExploreByTouchHelper {
        private static final String DATE_FORMAT = "dd MMMM yyyy";

        private final RangeCalendarGridView grid;
        private final Calendar tempCalendar = Calendar.getInstance();
        private final Rect tempRect = new Rect();

        private static final ArrayList<Integer> INDCIES;

        static {
            INDCIES = new ArrayList<>(42);
            for (int i = 0; i < 42; i++) {
                INDCIES.add(i);
            }
        }

        public TouchHelper(@NotNull RangeCalendarGridView grid) {
            super(grid);
            this.grid = grid;
        }

        @Override
        protected int getVirtualViewAt(float x, float y) {
            if (y > grid.gridTop()) {
                return grid.getGridIndexByPointOnScreen(x, y);
            }

            return ExploreByTouchHelper.INVALID_ID;
        }

        @Override
        protected void getVisibleVirtualViews(@NotNull List<Integer> virtualViewIds) {
            virtualViewIds.addAll(INDCIES);
        }

        @Override
        protected void onPopulateNodeForVirtualView(
                int virtualViewId,
                @NotNull AccessibilityNodeInfoCompat node
        ) {
            int gridY = virtualViewId / 7;
            int gridX = virtualViewId - gridY * 7;

            int x = (int) grid.getCellLeft(gridX);
            int y = (int) grid.getCellTop(gridY);
            int cellSize = (int) grid.cellSize;

            tempRect.set(x, y, x + cellSize, y + cellSize);

            //noinspection deprecation
            node.setBoundsInParent(tempRect);

            node.setContentDescription(getDayDescriptionForIndex(virtualViewId));
            node.setText(RangeCalendarGridView.DAYS[grid.cells[virtualViewId] - 1]);
            node.setSelected(grid.selectionType == SelectionType.CELL && virtualViewId == grid.selectedCell);
            node.setClickable(true);

            if (ShortRange.contains(grid.enabledCellRange, virtualViewId)) {
                node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK);
                node.setEnabled(true);
            } else {
                node.setEnabled(false);
            }
        }

        @Override
        protected boolean onPerformActionForVirtualView(int virtualViewId, int action, @Nullable Bundle arguments) {
            if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
                grid.selectCell(virtualViewId, false, true);
                return true;
            }

            return false;
        }

        @NotNull
        private CharSequence getDayDescriptionForIndex(int index) {
            int currentMonthStart = ShortRange.getStart(grid.inMonthRange);
            int currentMonthEnd = ShortRange.getEnd(grid.inMonthRange);

            int year = grid.year;
            int month = grid.month;
            int day = grid.cells[index];

            if (index < currentMonthStart) {
                month--;

                if (month == 0) {
                    year--;
                    month = 12;
                }
            } else if (index > currentMonthEnd) {
                month++;

                if (month == 13) {
                    year++;
                    month = 1;
                }
            }

            tempCalendar.set(year, month - 1, day);
            return DateFormat.format(DATE_FORMAT, tempCalendar);
        }
    }

    private static final class LongPressHandler extends Handler {
        private final WeakReference<RangeCalendarGridView> ref;

        public LongPressHandler(@NotNull RangeCalendarGridView gridView) {
            super(Looper.getMainLooper());

            ref = new WeakReference<>(gridView);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            RangeCalendarGridView obj = ref.get();
            if(obj == null) {
                return;
            }

            obj.onCellLongPress(msg.arg1);
        }
    }

    public static final float DEFAULT_RR_RADIUS_RATIO = 0.5f;

    private static final int CELL_IN_MONTH = 0;
    private static final int CELL_OUT_MONTH = 1;
    private static final int CELL_SELECTED = 2;
    private static final int CELL_DISABLED = 3;
    private static final int CELL_TODAY = 4;

    private static final int CELL_HOVER_BIT = 1 << 31;
    private static final int CELL_DATA_MASK = ~CELL_HOVER_BIT;

    private static final int ALL_SELECTED = ShortRange.create(0, 42);
    private static final int INVALID_RANGE = ShortRange.create(-1, -1);

    public static final int DEFAULT_COMMON_ANIM_DURATION = 250;
    public static final int DEFAULT_HOVER_ANIM_DURATION = 100;
    private static final long DOUBLE_TOUCH_MAX_MILLIS = 500;
    private static final int VIBRATE_DURATION = 50;

    private static final String TAG = "RangeCalendarGridView";

    private static final int ANIMATION_REVERSE_BIT = 1 << 31;
    private static final int ANIMATION_DATA_MASK = ~ANIMATION_REVERSE_BIT;

    private static final int NO_ANIMATION = 0;
    private static final int CELL_TO_WEEK_ON_ROW_ANIMATION = 1;
    private static final int MOVE_CELL_ON_ROW_ANIMATION = 2;
    private static final int MOVE_CELL_ON_COLUMN_ANIMATION = 3;
    private static final int HOVER_ANIMATION = 4;
    private static final int DUAL_CELL_ALPHA_ANIMATION = 5;
    private static final int CELL_ALPHA_ANIMATION = 6;
    private static final int DUAL_WEEK_ANIMATION = 7;
    private static final int WEEK_TO_CELL_ANIMATION = 8;
    private static final int CLEAR_SELECTION_ANIMATION = 9;
    private static final int MONTH_ALPHA_ANIMATION = 10;
    private static final int CELL_TO_MONTH_ANIMATION = 11;
    private static final int WEEK_TO_MONTH_ANIMATION = 12;

    static final String[] DAYS;

    final byte[] cells = new byte[42];

    private final RectF tempRect = new RectF();

    private float cellSize;
    private float columnWidth;

    private float rrRadiusRatio = DEFAULT_RR_RADIUS_RATIO;

    private final Paint dayNumberPaint;
    private final Paint weekdayPaint;

    private final Paint selectionPaint;

    private final Paint cellHoverPaint;
    private final Paint cellHoverOnSelectionPaint;

    private int inMonthColor;
    private int outMonthColor;
    private int disabledDayNumberColor;
    private int todayColor;

    private long lastTouchTime = -1;
    private int lastTouchCell = -1;

    @Nullable
    private OnSelectionListener onSelectionListener;

    private int inMonthRange;
    private int enabledCellRange = ALL_SELECTED;

    private int todayIndex = -1;
    private int hoverIndex = -1;

    int year;
    int month;

    private int prevSelectionType;
    private int selectionType;

    private int prevSelectedCell;
    int selectedCell;

    private int prevSelectedRange;
    private int selectedRange;

    private int selectedWeekIndex;

    @Nullable
    private Path customRangePath;
    private int customPathRange;

    private int customRangeStartCell;
    private boolean isSelectingCustomRange;

    private int animation = 0;
    private float animFraction = 0f;

    @Nullable
    private ValueAnimator animator;

    @Nullable
    private Runnable onAnimationEnd;

    boolean isFirstDaySunday;

    final CalendarResources cr;
    final TouchHelper touchHelper;

    private int weekdayType = WeekdayType.SHORT;

    int clickOnCellSelectionBehavior;

    int commonAnimationDuration = DEFAULT_COMMON_ANIM_DURATION;
    int hoverAnimationDuration = DEFAULT_HOVER_ANIM_DURATION;

    @NotNull
    TimeInterpolator commonAnimationInterpolator = TimeInterpolators.LINEAR;

    @NotNull
    TimeInterpolator hoverAnimationInterpolator = TimeInterpolators.LINEAR;

    private final Vibrator vibrator;
    private boolean allowCustomRanges = true;
    boolean vibrateOnSelectingCustomRange = true;

    private final Runnable clearHoverIndexCallback = () -> hoverIndex = -1;

    private final LongPressHandler longPressHandler = new LongPressHandler(this);

    static {
        DAYS = new String[31];
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

            DAYS[i] = new String(buffer, 0, textLength);
        }
    }

    public RangeCalendarGridView(
            @NotNull Context context,
            @NotNull CalendarResources cr
    ) {
        super(context);

        this.cr = cr;

        int defTextColor = cr.textColor;
        int colorPrimary = cr.colorPrimary;
        float dayNumberTextSize = cr.dayNumberTextSize;

        touchHelper = new TouchHelper(this);
        ViewCompat.setAccessibilityDelegate(this, touchHelper);

        cellSize = cr.cellSize;

        inMonthColor = defTextColor;
        outMonthColor = cr.outMonthTextColor;
        disabledDayNumberColor = cr.disabledTextColor;
        todayColor = colorPrimary;

        selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectionPaint.setColor(colorPrimary);
        selectionPaint.setStyle(Paint.Style.FILL);

        dayNumberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dayNumberPaint.setTextSize(dayNumberTextSize);
        dayNumberPaint.setColor(defTextColor);

        weekdayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        weekdayPaint.setTextSize(cr.weekdayTextSize);
        weekdayPaint.setColor(defTextColor);
        weekdayPaint.setTypeface(Typeface.DEFAULT_BOLD);

        cellHoverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cellHoverPaint.setStyle(Paint.Style.FILL);
        cellHoverPaint.setColor(cr.hoverColor);

        cellHoverOnSelectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cellHoverOnSelectionPaint.setStyle(Paint.Style.FILL);
        cellHoverOnSelectionPaint.setColor(cr.colorPrimaryDark);

        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    public void setSelectionColor(@ColorInt int color) {
        selectionPaint.setColor(color);
        invalidate();
    }

    public void setDayNumberTextSize(float size) {
        dayNumberPaint.setTextSize(size);
        invalidate();
    }

    public void setInMonthDayNumberColor(@ColorInt int color) {
        inMonthColor = color;
        invalidate();
    }

    public void setOutMonthDayNumberColor(@ColorInt int color) {
        outMonthColor = color;
        invalidate();
    }

    public void setDisabledDayNumberColor(@ColorInt int color) {
        disabledDayNumberColor = color;
        invalidate();
    }

    public void setTodayColor(@ColorInt int color) {
        todayColor = color;
        invalidate();
    }

    public void setDayNameColor(@ColorInt int color) {
        weekdayPaint.setColor(color);
        invalidate();
    }

    public void setDayNameTextSize(float size) {
        weekdayPaint.setTextSize(size);
        invalidate();
    }

    public void setHoverColor(@ColorInt int color) {
        cellHoverPaint.setColor(color);
        invalidate();
    }

    public void setHoverOnSelectionColor(@ColorInt int color) {
        cellHoverOnSelectionPaint.setColor(color);
        invalidate();
    }

    @Nullable
    public OnSelectionListener getOnSelectionListener() {
        return onSelectionListener;
    }

    public void setOnSelectionListener(@Nullable OnSelectionListener value) {
        this.onSelectionListener = value;
    }

    public float getCellSize() {
        return cellSize;
    }

    public void setCellSize(float size) {
        if (size <= 0f) {
            throw new IllegalArgumentException("size <= 0");
        }

        cellSize = size;

        refreshColumnWidth();

        requestLayout();
    }

    public float getRoundRectRadiusRatio() {
        return rrRadiusRatio;
    }

    public void setRoundRectRadiusRatio(float ratio) {
        if (ratio < 0f) {
            throw new IllegalArgumentException("ratio < 0");
        }

        rrRadiusRatio = Math.min(ratio, 0.5f);

        if (selectionType == SelectionType.MONTH) {
            forceResetCustomPath();
        }

        invalidate();
    }

    public void setInMonthRange(int range) {
        this.inMonthRange = range;

        reselect();
    }

    public void setEnabledCellRange(int range) {
        this.enabledCellRange = range;

        reselect();
    }

    public void setWeekdayType(@WeekdayTypeInt int type) {
        // There is no narrow weekdays before API < 24
        if (Build.VERSION.SDK_INT < 24 && type == WeekdayType.NARROW) {
            type = WeekdayType.SHORT;
        }

        if (!(type == WeekdayType.SHORT || type == WeekdayType.NARROW)) {
            throw new IllegalArgumentException("Invalid weekday type. Use constants from WeekdayType");
        }

        this.weekdayType = type;

        if (selectionType == SelectionType.MONTH) {
            forceResetCustomPath();
        }

        invalidate();
        touchHelper.invalidateRoot();
    }

    public void setTodayIndex(int index) {
        todayIndex = index;

        invalidate();
    }

    public void setAllowCustomRanges(boolean state) {
        allowCustomRanges = state;

        if(selectionType == SelectionType.CUSTOM) {
            clearSelection(true, true);
        }
    }

    private void reselect() {
        int savedSelectionType = selectionType;
        int savedSelectedCell = selectedCell;
        int savedWeekIndex = selectedWeekIndex;
        int savedRange = selectedRange;

        clearSelection(false, true);

        switch (savedSelectionType) {
            case SelectionType.CELL:
                selectCell(savedSelectedCell, true);
                break;
            case SelectionType.WEEK:
                selectWeek(savedWeekIndex, true);
                break;
            case SelectionType.MONTH:
                selectMonth(true, true);
                break;
            case SelectionType.CUSTOM:
                selectCustom(savedRange);
        }
    }

    private float rrRadius() {
        return cellSize * rrRadiusRatio;
    }

    private void refreshColumnWidth() {
        float width = getWidth();
        float height = getHeight();

        columnWidth = (width - cr.hPadding * 2) * (1f / 7);

        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int preferredWidth = (int) (cellSize * 7);
        int preferredHeight = (int) (gridTop() + (cellSize + cr.yCellMargin) * 6);

        setMeasuredDimension(
                resolveSize(preferredWidth, widthMeasureSpec),
                resolveSize(preferredHeight, heightMeasureSpec)
        );
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        refreshColumnWidth();

        if (selectionType == SelectionType.MONTH) {
            forceResetCustomPath();
        }
    }

    @Override
    protected boolean dispatchHoverEvent(@NotNull MotionEvent event) {
        return touchHelper.dispatchHoverEvent(event) && super.dispatchHoverEvent(event);
    }

    @Override
    public boolean onTouchEvent(@NotNull MotionEvent e) {
        int action = e.getActionMasked();

        float x = e.getX();
        float y = e.getY();

        if (y > gridTop()) {
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    if (isXInActiveZone(x)) {
                        int index = getGridIndexByPointOnScreen(x, y);

                        if (ShortRange.contains(enabledCellRange, index)) {
                            setHoverIndex(index);

                            if(allowCustomRanges) {
                                long time = e.getEventTime() + ViewConfiguration.getLongPressTimeout() +
                                        ViewConfiguration.getTapTimeout();

                                Message msg = Message.obtain();
                                msg.arg1 = index;

                                longPressHandler.sendMessageAtTime(msg, time);
                            }
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    performClick();

                    if (!isSelectingCustomRange && isXInActiveZone(x)) {
                        int index = getGridIndexByPointOnScreen(x, y);
                        long touchTime = e.getDownTime();

                        if (index >= 0) {
                            if (lastTouchTime > 0 &&
                                    touchTime - lastTouchTime < DOUBLE_TOUCH_MAX_MILLIS &&
                                    lastTouchCell == index) {
                                selectWeek(index / 7, true);
                            } else {
                                selectCell(index, true, true);
                                sendClickEventToAccessibility(index);

                                lastTouchCell = index;
                                lastTouchTime = touchTime;
                            }
                        }
                    }

                    hoverIndex = -1;
                    customRangeStartCell = -1;
                    isSelectingCustomRange = false;

                    longPressHandler.removeCallbacksAndMessages(null);

                    break;
                case MotionEvent.ACTION_CANCEL:
                    clearHoverIndex();
                    longPressHandler.removeCallbacksAndMessages(null);
                    customRangeStartCell = -1;
                    isSelectingCustomRange = false;

                    break;
                case MotionEvent.ACTION_MOVE:
                    if (isSelectingCustomRange && isXInActiveZone(x) && y > gridTop()) {
                        ViewParent parent = getParent();
                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }

                        int index = getGridIndexByPointOnScreen(x, y);
                        int newRange;

                        if (index > customRangeStartCell) {
                            newRange = ShortRange.create(customRangeStartCell, index);
                        } else {
                            newRange = ShortRange.create(index, customRangeStartCell);
                        }

                        if (selectedRange != newRange) {
                            selectCustom(newRange);
                        }
                    }

                    break;
            }
        }

        invalidate();

        return true;
    }

    private void onCellLongPress(int cell) {
        if(vibrateOnSelectingCustomRange) {
            if(Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createOneShot(VIBRATE_DURATION,  VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(VIBRATE_DURATION);
            }
        }

        customRangeStartCell = cell;
        isSelectingCustomRange = true;
        clearHoverIndex();
        selectCustom(ShortRange.create(cell, cell));
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    public void getFocusedRect(@NotNull Rect r) {
        int size = (int) cellSize;

        switch (selectionType) {
            case SelectionType.CELL: {
                int left = (int) getCellLeft(selectedCell);
                int top = (int) getCellTop(selectedCell);


                r.set(left, top, left + size, top + size);
                break;
            }
            case SelectionType.WEEK: {
                int startIndex = ShortRange.getStart(selectedRange);
                int left = (int) getCellLeft(startIndex);
                int right = (int) getCellRight(ShortRange.getEnd(selectedRange));
                int top = (int) getCellTop(startIndex);

                r.set(left, top, right, top + size);
                break;
            }
            default:
                super.getFocusedRect(r);
        }


    }

    private void sendClickEventToAccessibility(int index) {
        touchHelper.sendEventForVirtualView(index, AccessibilityEvent.TYPE_VIEW_CLICKED);
    }

    public void select(long packed) {
        select(
                PackedSelectionInfo.getType(packed),
                PackedSelectionInfo.getData(packed),
                PackedSelectionInfo.getWithAnimation(packed)
        );
    }

    public void select(int type, int data, boolean doAnimation) {
        switch (type) {
            case SelectionType.CELL:
                selectCell(data, doAnimation);
                break;
            case SelectionType.WEEK:
                selectWeek(data, doAnimation);
                break;
            case SelectionType.MONTH:
                selectMonth(doAnimation);
                break;
            case SelectionType.CUSTOM:
                selectCustom(data);
                break;
        }
    }

    private void selectCell(int index, boolean doAnimation) {
        selectCell(index, doAnimation, false);
    }

    private void selectCell(int index, boolean doAnimation, boolean isUser) {
        if (selectionType == SelectionType.CELL && selectedCell == index) {
            if (isUser && clickOnCellSelectionBehavior == ClickOnCellSelectionBehavior.CLEAR) {
                clearSelection(true, true);
            }

            return;
        }

        if (hoverIndex >= 0) {
            clearHoverIndex();
        }

        prevSelectionType = selectionType;
        prevSelectedCell = selectedCell;

        selectionType = SelectionType.CELL;
        selectedCell = index;

        OnSelectionListener listener = onSelectionListener;
        if (listener != null) {
            listener.onCellSelected(index);
        }

        if (doAnimation) {
            int animType = CELL_ALPHA_ANIMATION;

            switch (prevSelectionType) {
                case SelectionType.CELL: {
                    int gridY = index / 7;
                    int prevGridY = prevSelectedCell / 7;

                    if (prevGridY == gridY) {
                        animType = MOVE_CELL_ON_ROW_ANIMATION;
                    } else {
                        int gridX = index - gridY * 7;
                        int prevGridX = prevSelectedCell - prevGridY * 7;

                        if (gridX == prevGridX) {
                            animType = MOVE_CELL_ON_COLUMN_ANIMATION;
                        } else {
                            animType = DUAL_CELL_ALPHA_ANIMATION;
                        }
                    }

                    break;
                }
                case SelectionType.WEEK: {
                    int weekStart = ShortRange.getStart(selectedRange);
                    int weekEnd = ShortRange.getEnd(selectedRange);

                    if (index >= weekStart && index <= weekEnd) {
                        animType = CELL_TO_WEEK_ON_ROW_ANIMATION | ANIMATION_REVERSE_BIT;
                    } else {
                        animType = WEEK_TO_CELL_ANIMATION;
                    }

                    break;
                }
            }

            startAnimation(animType);
        } else {
            invalidate();
        }
    }

    private void selectWeek(int weekIndex, boolean doAnimation) {
        int start = weekIndex * 7;
        int end = start + 6;

        int weekRange = ShortRange.findIntersection(ShortRange.create(start, end), enabledCellRange, INVALID_RANGE);

        if (weekRange == INVALID_RANGE) {
            return;
        }

        start = ShortRange.getStart(weekRange);
        end = ShortRange.getEnd(weekRange);

        if (selectionType == SelectionType.WEEK && selectedRange == weekRange) {
            return;
        } else if (end == start) {
            selectCell(start, doAnimation);

            return;
        }

        prevSelectionType = selectionType;

        selectionType = SelectionType.WEEK;
        selectedWeekIndex = weekIndex;

        prevSelectedRange = selectedRange;
        selectedRange = ShortRange.create(start, end);

        OnSelectionListener listener = onSelectionListener;
        if (listener != null) {
            listener.onWeekSelected(weekIndex, start, end);
        }

        if (doAnimation) {
            if (prevSelectionType == SelectionType.CELL && selectedCell >= start && selectedCell <= end) {
                startAnimation(CELL_TO_WEEK_ON_ROW_ANIMATION);
            } else if (prevSelectionType == SelectionType.WEEK) {
                startAnimation(DUAL_WEEK_ANIMATION);
            }
        } else {
            invalidate();
        }
    }

    public void selectMonth(boolean doAnimation) {
        selectMonth(true, false);
    }

    public void selectMonth(boolean doAnimation, boolean reselect) {
        if (!reselect && selectionType == SelectionType.MONTH) {
            return;
        }

        prevSelectionType = selectionType;
        selectionType = SelectionType.MONTH;

        prevSelectedRange = selectedRange;
        selectedRange = ShortRange.findIntersection(inMonthRange, enabledCellRange, INVALID_RANGE);

        if (selectedRange == INVALID_RANGE) {
            if (customRangePath != null) {
                customRangePath.rewind();
            }
        } else if (prevSelectedRange == selectedRange) {
            return;
        }

        if (doAnimation) {
            startAnimation(MONTH_ALPHA_ANIMATION);
        } else {
            invalidate();
        }
    }

    public void selectCustom(int range) {
        prevSelectionType = selectionType;
        selectionType = SelectionType.CUSTOM;

        prevSelectedRange = selectedRange;
        selectedRange = ShortRange.findIntersection(range, enabledCellRange, INVALID_RANGE);

        if (selectedRange == INVALID_RANGE) {
            if (customRangePath != null) {
                customRangePath.rewind();
            }
        } else if (prevSelectedRange == selectedRange) {
            return;
        }

        OnSelectionListener listener = onSelectionListener;
        if (listener != null) {
            listener.onCustomRangeSelected(ShortRange.getStart(range), ShortRange.getEnd(range));
        }

        invalidate();
    }

    private void setHoverIndex(int index) {
        if ((selectionType == SelectionType.CELL && selectedCell == index) || hoverIndex == index) {
            return;
        }

        hoverIndex = index;

        startAnimation(HOVER_ANIMATION);
    }

    void clearHoverIndex() {
        startAnimation(HOVER_ANIMATION | ANIMATION_REVERSE_BIT, clearHoverIndexCallback);
    }

    public void clearSelection(boolean fireEvent, boolean doAnimation) {
        if (selectionType == SelectionType.NONE) {
            return;
        }

        prevSelectionType = selectionType;
        prevSelectedCell = selectedCell;
        prevSelectedRange = selectedRange;

        selectionType = SelectionType.NONE;
        selectedCell = -1;
        selectedWeekIndex = -1;
        selectedRange = 0;

        OnSelectionListener listener = onSelectionListener;
        if (fireEvent && listener != null) {
            listener.onSelectionCleared();
        }

        if (doAnimation) {
            startAnimation(CLEAR_SELECTION_ANIMATION | ANIMATION_REVERSE_BIT);
        } else {
            invalidate();
        }
    }

    private void startAnimation(int type) {
        startAnimation(type, null);
    }

    private void startAnimation(int type, @Nullable Runnable onEnd) {
        if (animator != null && animator.isRunning()) {
            animator.end();
        }

        animation = type;
        onAnimationEnd = onEnd;

        if (animator == null) {
            animator = AnimationHelper.createFractionAnimator(value -> {
                animFraction = value;

                invalidate();
            });

            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    Runnable runnable = onAnimationEnd;
                    if (runnable != null) {
                        runnable.run();
                    }

                    RangeCalendarGridView.this.animation = NO_ANIMATION;
                    invalidate();
                }
            });
        }

        if ((type & ANIMATION_DATA_MASK) == HOVER_ANIMATION) {
            animator.setDuration(hoverAnimationDuration);
            animator.setInterpolator(hoverAnimationInterpolator);
        } else {
            animator.setDuration(commonAnimationDuration);
            animator.setInterpolator(commonAnimationInterpolator);
        }

        if ((type & ANIMATION_REVERSE_BIT) != 0) {
            animator.reverse();
        } else {
            animator.start();
        }
    }

    public void onGridChanged() {
        invalidate();
        touchHelper.invalidateRoot();
    }

    @Override
    public void onDraw(@NotNull Canvas c) {
        drawSelection(c);
        drawWeekdayRow(c);
        drawHover(c);
        drawCells(c);
    }

    private void drawSelection(@NotNull Canvas c) {
        switch (animation & ANIMATION_DATA_MASK) {
            case CELL_TO_WEEK_ON_ROW_ANIMATION: {
                drawWeekCellSelection(c, animFraction);

                break;
            }
            case MOVE_CELL_ON_ROW_ANIMATION: {
                drawCellSelection(c, selectedCell, animFraction, 1f);

                break;
            }
            case MOVE_CELL_ON_COLUMN_ANIMATION: {
                drawCellSelection(c, selectedCell, 1f, animFraction);

                break;
            }
            case DUAL_CELL_ALPHA_ANIMATION: {
                int alpha = (int) (animFraction * 255f);

                selectionPaint.setAlpha(alpha);
                drawCellSelection(c, selectedCell, 1f, 1f);

                selectionPaint.setAlpha(255 - alpha);
                drawCellSelection(c, prevSelectedCell, 1f, 1f);

                selectionPaint.setAlpha(255);

                break;
            }
            case CELL_ALPHA_ANIMATION: {
                int alpha = (int) (animFraction * 255f);

                selectionPaint.setAlpha(alpha);
                drawCellSelection(c, selectedCell, 1f, 1f);
                selectionPaint.setAlpha(255);

                break;
            }
            case DUAL_WEEK_ANIMATION: {
                drawWeekFromCenterSelection(c, animFraction, selectedRange);

                selectionPaint.setAlpha(255 - (int) (animFraction * 255));
                drawWeekFromCenterSelection(c, 1f, prevSelectedRange);

                selectionPaint.setAlpha(255);

                break;
            }
            case WEEK_TO_CELL_ANIMATION: {
                int alpha = (int) (animFraction * 255);
                selectionPaint.setAlpha(255 - alpha);
                drawWeekFromCenterSelection(c, 1f, selectedRange);

                selectionPaint.setAlpha(alpha);
                drawCellSelection(c, selectedCell, 1f, 1f);

                selectionPaint.setAlpha(255);

                break;
            }
            case MONTH_ALPHA_ANIMATION: {
                selectionPaint.setAlpha((int) (animFraction * 255f));
                drawCustomRange(c, selectedRange);

                selectionPaint.setAlpha(255);

                break;
            }
            case CLEAR_SELECTION_ANIMATION: {
                selectionPaint.setAlpha((int) (animFraction * 255));
                drawSelectionNoAnimation(c, prevSelectionType, prevSelectedCell, prevSelectedRange);

                selectionPaint.setAlpha(255);

                break;
            }
            default:
                drawSelectionNoAnimation(c, selectionType, selectedCell, selectedRange);
        }
    }

    private void drawSelectionNoAnimation(@NotNull Canvas c, int sType, int cell, int range) {
        switch (sType) {
            case SelectionType.CELL:
                drawCellSelection(c, cell, 1f, 1f);

                break;
            case SelectionType.WEEK:
                drawWeekFromCenterSelection(c, 1f, range);

                break;
            case SelectionType.MONTH:
            case SelectionType.CUSTOM:
                drawCustomRange(c, range);

                break;
        }
    }

    private void drawWeekSelection(
            @NotNull Canvas c,
            float leftAnchor,
            float rightAnchor,
            float fraction,
            int weekRange
    ) {
        int selStart = ShortRange.getStart(weekRange);
        int selEnd = ShortRange.getEnd(weekRange);

        int selY = selStart / 7;
        int alignedSelX = selY * 7;
        int selStartX = selStart - alignedSelX;
        int selEndX = selEnd - alignedSelX;

        float rectLeft = getCellLeft(selStartX);
        float rectTop = getCellTop(selY);
        float rectRight = getCellRight(selEndX);
        float rectBottom = rectTop + cellSize;

        float left = leftAnchor - (leftAnchor - rectLeft) * fraction;
        float right = rightAnchor + (rectRight - rightAnchor) * fraction;

        float radius = rrRadius();

        tempRect.set(left, rectTop, right, rectBottom);

        c.drawRoundRect(tempRect, radius, radius, selectionPaint);
    }

    private void drawWeekFromCenterSelection(@NotNull Canvas c, float fraction, int range) {
        int selStart = ShortRange.getStart(range);
        int selEnd = ShortRange.getEnd(range);

        float midX = cr.hPadding +
                (float) (selStart + selEnd - 14 * (selStart / 7) + 1) * cellSize * 0.5f;

        drawWeekSelection(c, midX, midX, fraction, range);
    }

    private void drawWeekCellSelection(@NotNull Canvas c, float fraction) {
        float cellLeft = getCellLeft(selectedCell % 7);
        float cellRight = cellLeft + cellSize;

        drawWeekSelection(c, cellLeft, cellRight, fraction, selectedRange);
    }

    private void drawCustomRange(@NotNull Canvas c, int range) {
        updateCustomRangePath(range);

        if(customRangePath != null) {
            c.drawPath(customRangePath, selectionPaint);
        }
    }

    private void drawCellSelection(@NotNull Canvas c, int cell, float xFraction, float yFraction) {
        int prevGridY = prevSelectedCell / 7;
        int prevGridX = prevSelectedCell - prevGridY * 7;

        int gridY = cell / 7;
        int gridX = cell - gridY * 7;

        float left;
        float top;

        float endLeft = getCellLeft(gridX);
        float endTop = getCellTop(gridY);

        if (xFraction != 1f) {
            float startLeft = getCellLeft(prevGridX);

            left = startLeft + (endLeft - startLeft) * xFraction;
        } else {
            left = endLeft;
        }

        if (yFraction != 1f) {
            float startTop = getCellTop(prevGridY);

            top = startTop + (endTop - startTop) * yFraction;
        } else {
            top = endTop;
        }

        float right = left + cellSize;
        float bottom = top + cellSize;

        float radius = rrRadius();

        tempRect.set(left, top, right, bottom);

        c.drawRoundRect(tempRect, radius, radius, selectionPaint);
    }

    private void drawWeekdayRow(@NotNull Canvas c) {
        float x = cr.hPadding;
        int offset = weekdayType == WeekdayType.SHORT ?
                CalendarResources.SHORT_WEEKDAYS_OFFSET :
                CalendarResources.NARROW_WEEKDAYS_OFFSET;

        int startIndex = isFirstDaySunday ? 0 : 1;

        float halfColumnWidth = columnWidth * 0.5f;

        for (int i = offset + startIndex; i < offset + 7; i++) {
            drawWeekday(c, i, x + halfColumnWidth);

            x += columnWidth;
        }

        if (!isFirstDaySunday) {
            drawWeekday(c, offset, x + halfColumnWidth);
        }
    }

    private void drawWeekday(@NotNull Canvas c, int index, float midX) {
        float textX = midX - (float) (cr.weekdayWidths[index] / 2);

        c.drawText(cr.weekdays[index], textX, cr.shortWeekdayRowHeight, weekdayPaint);
    }

    private void drawHover(@NotNull Canvas c) {
        int index = hoverIndex;
        if (index >= 0) {
            int gridY = index / 7;
            int gridX = index - gridY * 7;

            float left = getCellLeft(gridX);
            float top = getCellTop(gridY);

            float radius = rrRadius();

            Paint paint;
            if (isSelectionRangeContainsIndex(index)) {
                paint = cellHoverOnSelectionPaint;
            } else {
                paint = cellHoverPaint;
            }

            int paintAlpha = paint.getAlpha();

            int resultAlpha = paintAlpha;
            if ((animation & ANIMATION_DATA_MASK) == HOVER_ANIMATION) {
                resultAlpha = (int) (paintAlpha * animFraction);
            }

            paint.setAlpha(resultAlpha);

            tempRect.set(left, top, left + cellSize, top + cellSize);
            c.drawRoundRect(tempRect, radius, radius, paint);

            paint.setAlpha(paintAlpha);
        }
    }

    private void drawCells(@NotNull Canvas c) {
        for (int i = 0; i < 42; i++) {
            int gridY = i / 7;
            int gridX = i - gridY * 7;

            float x = getCellLeft(gridX);
            float y = getCellTop(gridY);

            drawCell(c, x, y, cells[i], resolveCellType(i));
        }
    }

    private int resolveCellType(int i) {
        int cellType;

        if (selectionType == SelectionType.CELL && selectedCell == i) {
            cellType = CELL_SELECTED;
        } else if (ShortRange.contains(enabledCellRange, i)) {
            if (ShortRange.contains(inMonthRange, i)) {
                if (i == todayIndex) {
                    if (isSelectionRangeContainsIndex(i)) {
                        cellType = CELL_IN_MONTH;
                    } else {
                        cellType = CELL_TODAY;
                    }
                } else {
                    cellType = CELL_IN_MONTH;
                }
            } else {
                cellType = CELL_OUT_MONTH;
            }
        } else {
            cellType = CELL_DISABLED;
        }

        if (i == hoverIndex) {
            cellType |= CELL_HOVER_BIT;
        }

        return cellType;
    }

    private void drawCell(@NotNull Canvas c, float x, float y, int day, int cellType) {
        int color = 0;

        switch (cellType & CELL_DATA_MASK) {
            case CELL_SELECTED:
            case CELL_IN_MONTH:
                color = inMonthColor;
                break;
            case CELL_OUT_MONTH:
                color = outMonthColor;
                break;
            case CELL_DISABLED:
                color = disabledDayNumberColor;
                break;
            case CELL_TODAY:
                color = (cellType & CELL_HOVER_BIT) != 0 ? inMonthColor : todayColor;
                break;
        }

        if (day > 0) {
            long size = cr.dayNumberSizes[day - 1];

            float halfCellSize = cellSize * 0.5f;

            float textX = x + halfCellSize - (float) (PackedSize.getWidth(size) / 2);
            float textY = y + halfCellSize + (float) (PackedSize.getHeight(size) / 2);

            dayNumberPaint.setColor(color);
            c.drawText(DAYS[day - 1], textX, textY, dayNumberPaint);
        }
    }

    private void updateCustomRangePath(int range) {
        if(customPathRange == range) {
            return;
        }

        customPathRange = range;

        if (customRangePath == null) {
            customRangePath = new Path();
        } else {
            customRangePath.rewind();
        }

        Path path = customRangePath;

        int start = ShortRange.getStart(range);
        int end = ShortRange.getEnd(range);

        int startGridY = start / 7;
        int startGridX = start - startGridY * 7;

        int endGridY = end / 7;
        int endGridX = end - endGridY * 7;

        float radius = rrRadius();

        if (startGridY == endGridY) {
            float left = getCellLeft(startGridX);
            float right = getCellRight(endGridX);

            float top = getCellTop(startGridY);
            float bottom = top + cellSize;

            tempRect.set(left, top, right, bottom);

            path.addRoundRect(tempRect, radius, radius, Path.Direction.CW);
        } else {
            float firstCellOnRowX = cr.hPadding + (columnWidth - cellSize) * 0.5f;
            float lastCellOnRowRight = cr.hPadding + columnWidth * 6.5f + cellSize * 0.5f;

            float startLeft = getCellLeft(startGridX);
            float startTop = getCellTop(startGridY);
            float startBottom = startTop + cellSize;

            float endRight = getCellRight(endGridX);
            float endTop = getCellTop(endGridY);
            float endBottom = endTop + cellSize;

            int gridYDiff = endGridY - startGridY;

            Radii.clear();
            Radii.lt();
            Radii.rt();
            Radii.lb(startGridX != 0);
            Radii.rb(gridYDiff == 1 && endGridX != 6);

            tempRect.set(startLeft, startTop, lastCellOnRowRight, startBottom);

            path.addRoundRect(tempRect, Radii.result(radius), Path.Direction.CW);

            Radii.clear();
            Radii.rb();
            Radii.lb();
            Radii.rt(endGridX != 6);
            Radii.lt(gridYDiff == 1 && startGridX != 0);

            tempRect.set(firstCellOnRowX, gridYDiff == 1 ? startBottom : endTop, endRight, endBottom);
            path.addRoundRect(tempRect, Radii.result(radius), Path.Direction.CW);

            if (gridYDiff > 1f) {
                Radii.clear();
                Radii.lt(startGridX != 0);
                Radii.rb(endGridX != 6);

                tempRect.set(firstCellOnRowX, startBottom, lastCellOnRowRight, endTop);
                if (startGridX == 0 && endGridX == 6) {
                    path.addRect(tempRect, Path.Direction.CW);
                } else {
                    path.addRoundRect(tempRect, Radii.result(radius), Path.Direction.CW);
                }
            }
        }
    }

    private float getCircleRadiusForMonthAnimation(float x, float y) {
        float distToLeftCorner = x - cr.hPadding;
        float distToRightCorner = getWidth() - distToLeftCorner;

        float distToTopCorner = y - gridTop();
        float distToBottomCorner = getHeight() - distToTopCorner;

        int intersection = ShortRange.findIntersection(enabledCellRange, inMonthRange, INVALID_RANGE);
        if (intersection == INVALID_RANGE) {
            return Float.NaN;
        }

        int startCell = ShortRange.getStart(intersection);
        int endCell = ShortRange.getEnd(intersection);

        int startCellGridY = startCell / 7;
        int startCellGridX = startCell - startCellGridY * 7;

        int endCellGridY = endCell / 7;
        int endCellGridX = endCell * endCellGridY * 7;

        float startCellLeft = getCellLeft(startCellGridX);
        float startCellTop = getCellTop(startCellGridY);

        float endCellLeft = getCellLeft(endCellGridX);
        float endCellBottom = getCellTop(endCellGridY) + cellSize;

        float distToStartCell = distance(startCellLeft, startCellTop, x, y);
        float distToEndCell = distance(endCellLeft, endCellBottom, x, y);

        return Math.max(distToLeftCorner, Math.max(distToRightCorner, Math.max(distToTopCorner,
                Math.max(distToBottomCorner, Math.max(distToStartCell, distToEndCell)))
        ));
    }

    private float distance(float x1, float y1, float x2, float y2) {
        float xDist = x2 - x1;
        float yDist = y2 - y1;

        return (float) Math.sqrt(xDist * xDist + yDist * yDist);
    }

    private void forceResetCustomPath() {
        customPathRange = 0;
    }

    private float gridTop() {
        float height;
        if (weekdayType == WeekdayType.SHORT) {
            height = cr.shortWeekdayRowHeight;
        } else {
            height = cr.narrowWeekdayRowHeight;
        }

        return height + cr.weekdayRowMarginBottom;
    }

    private float getCellLeft(int gridX) {
        return cr.hPadding + columnWidth * (gridX + 0.5f) - cellSize * 0.5f;
    }

    private float getCellTop(int gridY) {
        return gridTop() + gridY * cellSize + gridY * cr.yCellMargin;
    }

    private float getCellRight(int gridX) {
        return getCellLeft(gridX) + cellSize;
    }

    private boolean isSelectionRangeContainsIndex(int index) {
        return ((selectionType != SelectionType.NONE && selectionType != SelectionType.CELL) &&
                ShortRange.contains(selectedRange, index));
    }

    public int getGridIndexByPointOnScreen(float x, float y) {
        int gridX = (int) ((x - cr.hPadding) / columnWidth);
        int gridY = (int) ((y - gridTop()) / (cellSize + cr.yCellMargin));

        return gridY * 7 + gridX;
    }

    private boolean isXInActiveZone(float x) {
        return x >= cr.hPadding && x <= getWidth() - cr.hPadding;
    }
}
