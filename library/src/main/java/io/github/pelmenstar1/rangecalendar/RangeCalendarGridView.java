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
import android.text.format.DateFormat;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;

import androidx.annotation.ColorInt;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.customview.widget.ExploreByTouchHelper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

@SuppressLint("ViewConstructor")
final class RangeCalendarGridView extends View {
    public interface OnSelectionListener {
        void onSelectionCleared();

        void onCellSelected(int index);

        void onWeekSelected(int weekIndex, int startIndex, int endIndex);
    }

    private static final class Radii {
        public static final float[] value = new float[8];

        public static void clear() {
            Arrays.fill(value, 0);
        }

        // left top
        public static void lt(float v) {
            value[0] = value[1] = v;
        }

        // right top
        public static void rt(float v) {
            value[2] = value[3] = v;
        }

        // right bottom
        public static void rb(float v) {
            value[4] = value[5] = v;
        }

        // left bottom
        public static void lb(float v) {
            value[6] = value[7] = v;
        }
    }

    public static final class PackedSelectionInfo {
        public static int create(int type, int data, boolean withAnimation) {
            return (type << 24) | (data << 8) | (withAnimation ? 1 : 0);
        }

        public static int getType(int packed) {
            return packed >> 24;
        }

        public static int getData(int packed) {
            return (packed >> 8) & 0xFFFF;
        }

        public static boolean getWithAnimation(int packed) {
            return (packed & 0xFF) == 1;
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

            if (PositiveIntRange.contains(grid.enabledCellRange, virtualViewId)) {
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
            int currentMonthStart = PositiveIntRange.getStart(grid.inMonthRange);
            int currentMonthEnd = PositiveIntRange.getEnd(grid.inMonthRange);

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

    public static final float DEFAULT_RR_RADIUS_RATIO = 0.5f;

    private static final int CELL_IN_MONTH = 0;
    private static final int CELL_OUT_MONTH = 1;
    private static final int CELL_SELECTED = 2;
    private static final int CELL_DISABLED = 3;
    private static final int CELL_TODAY = 4;

    private static final int CELL_HOVER_BIT = 1 << 31;
    private static final int CELL_DATA_MASK = ~CELL_HOVER_BIT;

    private static final long ALL_SELECTED = PositiveIntRange.create(0, 42);

    public static final int DEFAULT_COMMON_ANIM_DURATION = 250;
    public static final int DEFAULT_HOVER_ANIM_DURATION = 100;
    private static final long DOUBLE_TOUCH_MAX_MILLIS = 500;

    private static final String TAG = "RangeCalendarGridView";

    private static final int REVERSE_BIT = 1 << 31;
    private static final int ANIMATION_DATA_MASK = ~REVERSE_BIT;

    private static final int NO_ANIMATION = 0;
    private static final int WEEK_TO_CELL_ON_ROW_ANIMATION = 1;
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

    private long inMonthRange;
    private long enabledCellRange = ALL_SELECTED;

    private int todayIndex = -1;
    private int hoverIndex = -1;

    int year;
    int month;

    private int prevSelectionType;
    private int selectionType;

    private int prevSelectedCell;
    int selectedCell;

    private long prevSelectedRange;
    private long selectedRange;

    private int selectedWeekIndex;

    @Nullable
    private Path selectedMonthPath;
    private long selectedMonthPathRange;

    private int animType = 0;
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

    private final Runnable clearHoverIndexCallback = () -> hoverIndex = -1;

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
            forceResetMonthPath();
        }

        invalidate();
    }

    public void setInMonthRange(long range) {
        this.inMonthRange = range;

        reselect();
    }

    public void setEnabledCellRange(long range) {
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
            forceResetMonthPath();
        }

        invalidate();
        touchHelper.invalidateRoot();
    }

    public void setTodayIndex(int index) {
        todayIndex = index;

        invalidate();
    }

    private void reselect() {
        int savedSelectionType = selectionType;
        int savedSelectedCell = selectedCell;
        int savedWeekIndex = selectedWeekIndex;

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

        if(selectionType == SelectionType.MONTH) {
            forceResetMonthPath();
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

                        if (PositiveIntRange.contains(enabledCellRange, index)) {
                            setHoverIndex(index);
                        }

                    }
                    break;
                case MotionEvent.ACTION_UP:
                    performClick();

                    if (isXInActiveZone(x)) {
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

                            hoverIndex = -1;
                        }
                    }

                    break;
                case MotionEvent.ACTION_CANCEL:
                    clearHoverIndex();
                    break;
            }
        }

        invalidate();

        return true;
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
                int startIndex = PositiveIntRange.getStart(selectedRange);
                int left = (int) getCellLeft(startIndex);
                int right = (int) getCellRight(PositiveIntRange.getEnd(selectedRange));
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

    public void select(int packed) {
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
                    int weekStart = PositiveIntRange.getStart(selectedRange);
                    int weekEnd = PositiveIntRange.getEnd(selectedRange);

                    if (index >= weekStart && index <= weekEnd) {
                        animType = WEEK_TO_CELL_ON_ROW_ANIMATION;
                    } else {
                        animType = WEEK_TO_CELL_ANIMATION;
                    }

                    break;
                }
            }

            startAnimation(animType, DEFAULT_COMMON_ANIM_DURATION);
        } else {
            invalidate();
        }
    }

    private void selectWeek(int weekIndex, boolean doAnimation) {
        int start = weekIndex * 7;
        int end = start + 6;

        long weekRange = PositiveIntRange.findIntersection(
                PositiveIntRange.create(start, end),
                enabledCellRange
        );
        if (weekRange == PositiveIntRange.NO_INTERSECTION) {
            return;
        }

        start = PositiveIntRange.getStart(weekRange);
        end = PositiveIntRange.getEnd(weekRange);

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
        selectedRange = PositiveIntRange.create(start, end);

        OnSelectionListener listener = onSelectionListener;
        if (listener != null) {
            listener.onWeekSelected(weekIndex, start, end);
        }

        if (doAnimation) {
            int animType = -1;

            if (prevSelectionType == SelectionType.CELL && selectedCell >= start && selectedCell <= end) {
                animType = WEEK_TO_CELL_ON_ROW_ANIMATION | REVERSE_BIT;
            } else if (prevSelectionType == SelectionType.WEEK) {
                animType = DUAL_WEEK_ANIMATION;
            }

            if (animType > 0) {
                startAnimation(animType, DEFAULT_COMMON_ANIM_DURATION);
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
        selectedRange = PositiveIntRange.findIntersection(
                inMonthRange,
                enabledCellRange
        );

        if (selectedRange == PositiveIntRange.NO_INTERSECTION) {
            if (selectedMonthPath != null) {
                selectedMonthPath.rewind();
            }
        } else if (prevSelectedRange == selectedRange) {
            return;
        }

        if (doAnimation) {
            startAnimation(MONTH_ALPHA_ANIMATION, DEFAULT_COMMON_ANIM_DURATION);
        } else {
            invalidate();
        }
    }

    private void setHoverIndex(int index) {
        if ((selectionType == SelectionType.CELL && selectedCell == index) || hoverIndex == index) {
            return;
        }

        hoverIndex = index;

        startAnimation(HOVER_ANIMATION, DEFAULT_HOVER_ANIM_DURATION);
    }

    void clearHoverIndex() {
        startAnimation(HOVER_ANIMATION | REVERSE_BIT, DEFAULT_HOVER_ANIM_DURATION, clearHoverIndexCallback);
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
            startAnimation(CLEAR_SELECTION_ANIMATION | REVERSE_BIT, DEFAULT_COMMON_ANIM_DURATION);
        } else {
            invalidate();
        }
    }

    private void startAnimation(int type, int duration) {
        startAnimation(type, duration, null);
    }

    private void startAnimation(int type, int duration, @Nullable Runnable onEnd) {
        if (animator != null && animator.isRunning()) {
            animator.end();
        }

        animType = type;
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

                    animType = NO_ANIMATION;
                    invalidate();
                }
            });
        }

        animator.setDuration(duration);
        if((type & ANIMATION_DATA_MASK) == HOVER_ANIMATION) {
            animator.setDuration(hoverAnimationDuration);
            animator.setInterpolator(hoverAnimationInterpolator);
        } else {
            animator.setDuration(commonAnimationDuration);
            animator.setInterpolator(commonAnimationInterpolator);
        }

        if((type & REVERSE_BIT) != 0) {
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
        switch (animType & ANIMATION_DATA_MASK) {
            case WEEK_TO_CELL_ON_ROW_ANIMATION: {
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
                int alpha = (int)(animFraction * 255);
                selectionPaint.setAlpha(255 - alpha);
                drawWeekFromCenterSelection(c, 1f, selectedRange);

                selectionPaint.setAlpha(alpha);
                drawCellSelection(c, selectedCell, 1f, 1f);

                selectionPaint.setAlpha(255);

                break;
            }
            case MONTH_ALPHA_ANIMATION: {
                selectionPaint.setAlpha((int)(animFraction * 255f));
                drawMonthSelection(c, selectedRange);

                selectionPaint.setAlpha(255);

                break;
            }
            case CLEAR_SELECTION_ANIMATION: {
                selectionPaint.setAlpha((int)(animFraction * 255));
                drawSelectionNoAnimation(c, prevSelectionType, prevSelectedCell, prevSelectedRange);

                selectionPaint.setAlpha(255);

                break;
            }
            default:
                drawSelectionNoAnimation(c, selectionType, selectedCell, selectedRange);
        }
    }

    private void drawSelectionNoAnimation(@NotNull Canvas c, int sType, int cell, long range) {
        switch (sType) {
            case SelectionType.CELL:
                drawCellSelection(c, cell, 1f, 1f);

                break;
            case SelectionType.WEEK:
                drawWeekFromCenterSelection(c, 1f, range);

                break;
            case SelectionType.MONTH:
                drawMonthSelection(c, range);

                break;
        }
    }

    private void drawWeekSelection(
            @NotNull Canvas c,
            float leftAnchor,
            float rightAnchor,
            float fraction,
            long weekRange
    ) {
        int selStart = PositiveIntRange.getStart(weekRange);
        int selEnd = PositiveIntRange.getEnd(weekRange);

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

    private void drawWeekFromCenterSelection(@NotNull Canvas c, float fraction, long range) {
        int selStart = PositiveIntRange.getStart(range);
        int selEnd = PositiveIntRange.getEnd(range);

        float midX = cr.hPadding +
                (float) (selStart + selEnd - 14 * (selStart / 7) + 1) * cellSize * 0.5f;

        drawWeekSelection(c, midX, midX, fraction, range);
    }

    private void drawWeekCellSelection(@NotNull Canvas c, float fraction) {
        int cell = selectedCell;
        if(selectionType == SelectionType.NONE &&
                clickOnCellSelectionBehavior == ClickOnCellSelectionBehavior.CLEAR
        ) {
            cell = prevSelectedCell;
        }

        float cellLeft = getCellLeft(cell % 7);
        float cellRight = cellLeft + cellSize;

        drawWeekSelection(c, cellLeft, cellRight, fraction, selectedRange);
    }

    private void drawMonthSelection(@NotNull Canvas c, long range) {
        updateSelectedMonthPathIfChanged(range);

        c.drawPath(selectedMonthPath, selectionPaint);
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
            if((animType & ANIMATION_DATA_MASK) == HOVER_ANIMATION) {
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
        } else if (PositiveIntRange.contains(enabledCellRange, i)) {
            if (PositiveIntRange.contains(inMonthRange, i)) {
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

    private void updateSelectedMonthPathIfChanged(long range) {
        if(selectedMonthPathRange == range) {
            return;
        }

        selectedMonthPathRange = range;

        if (selectedMonthPath == null) {
            selectedMonthPath = new Path();
        } else {
            selectedMonthPath.rewind();
        }


        Path path = selectedMonthPath;

        int start = PositiveIntRange.getStart(selectedRange);
        int end = PositiveIntRange.getEnd(selectedRange);

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

            Radii.clear();
            Radii.lt(radius);
            Radii.rt(radius);

            if (startGridX != 0) {
                Radii.lb(radius);
            }

            tempRect.set(startLeft, startTop, lastCellOnRowRight, startBottom);

            path.addRoundRect(tempRect, Radii.value, Path.Direction.CW);

            Radii.clear();
            Radii.rb(radius);
            Radii.lb(radius);

            if (endGridX != 6) {
                Radii.rt(radius);
            }

            tempRect.set(firstCellOnRowX, endTop, endRight, endBottom);
            path.addRoundRect(tempRect, Radii.value, Path.Direction.CW);

            if (endTop > startBottom) {
                Radii.clear();
                if (startGridX != 0) {
                    Radii.lt(radius);
                }

                if (endGridX != 6) {
                    Radii.rb(radius);
                }

                tempRect.set(firstCellOnRowX, startBottom, lastCellOnRowRight, endTop);
                if (startGridX == 0 && endGridX == 6) {
                    path.addRect(tempRect, Path.Direction.CW);
                } else {
                    path.addRoundRect(tempRect, Radii.value, Path.Direction.CW);
                }
            }
        }
    }

    private float getCircleRadiusForMonthAnimation(float x, float y) {
        float distToLeftCorner = x - cr.hPadding;
        float distToRightCorner = getWidth() - distToLeftCorner;

        float distToTopCorner = y - gridTop();
        float distToBottomCorner = getHeight() - distToTopCorner;

        long intersection = PositiveIntRange.findIntersection(enabledCellRange, inMonthRange);
        if(intersection == PositiveIntRange.NO_INTERSECTION) {
            return Float.NaN;
        }

        int startCell = PositiveIntRange.getStart(intersection);
        int endCell = PositiveIntRange.getEnd(intersection);

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

        return (float)Math.sqrt(xDist * xDist + yDist * yDist);
    }

    private void forceResetMonthPath() {
        selectedMonthPathRange = 0;
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
        return ((selectionType == SelectionType.WEEK || selectionType == SelectionType.MONTH) &&
                PositiveIntRange.contains(selectedRange, index));
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
