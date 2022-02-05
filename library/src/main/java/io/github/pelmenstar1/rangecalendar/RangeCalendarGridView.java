package io.github.pelmenstar1.rangecalendar;

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
import android.util.Log;
import android.view.Choreographer;
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
    private static final Choreographer choreographer = Choreographer.getInstance();

    public interface OnSelectionListener {
        void onSelectionCleared();
        void onCellSelected(int index);
        void onWeekSelected(int weekIndex, int startIndex, int endIndex);
        void onMonthSelected();
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

    private abstract class InternalAnimationHandler {
        private long startTime;
        private final long duration;
        protected boolean forward;

        public final Choreographer.FrameCallback tickCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long time) {
                long elapsed = time - startTime;

                if (elapsed >= duration) {
                    onEnd();
                } else {
                    float fraction = (float) elapsed / duration;
                    if (!forward) {
                        fraction = 1f - fraction;
                    }

                    onTick(fraction);

                    choreographer.postFrameCallback(this);
                }

                invalidate();
            }
        };

        public final Choreographer.FrameCallback startCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long time) {
                startTime = time;
                onTick(forward ? 0f : 1f);

                choreographer.postFrameCallback(tickCallback);
            }
        };

        public InternalAnimationHandler() {
            duration = ANIM_DURATION;
        }

        public InternalAnimationHandler(long duration) {
            this.duration = duration;
        }

        public final void start() {
            start(true);
        }

        public final void start(boolean forward) {
            this.forward = forward;

            choreographer.postFrameCallback(startCallback);
        }

        public abstract void onEnd();

        public abstract void onTick(float fraction);
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

            int x = (int)grid.getCellLeft(gridX);
            int y = (int)grid.getCellTop(gridY);
            int cellSize = (int)grid.cellSize;

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
            int currentMonthStart = PositiveIntRange.getStart(grid.currentMonthRange);
            int currentMonthEnd = PositiveIntRange.getEnd(grid.currentMonthRange);

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

    public static final float RR_RADIUS_RATIO = 0.5f;

    private static final int CELL_CURRENT_MONTH = 0;
    private static final int CELL_NOT_CURRENT_MONTH = 1;
    private static final int CELL_SELECTED = 2;
    private static final int CELL_DISABLED = 3;
    private static final int CELL_TODAY = 4;

    private static final int CELL_HOVER_BIT = 1 << 31;
    private static final int CELL_HOVER_ON_SELECTION_BIT = 1 << 30;
    private static final int CELL_DATA_MASK = ~(CELL_HOVER_BIT | CELL_HOVER_ON_SELECTION_BIT);

    private static final long ALL_SELECTED = PositiveIntRange.create(0, 42);

    private static final long ANIM_DURATION = 200 * 1000000;
    private static final long HOVER_ALPHA_ANIM_DURATION = 100 * 1000000;
    private static final long DOUBLE_TOUCH_MAX_MILLIS = 500;

    private static final String TAG = "RangeCalendarGridView";

    private static final int WEEK_TO_CELL_ON_ROW_ANIMATION = 1;
    private static final int CELL_TO_WEEK_ON_ROW_ANIMATION = 2;
    private static final int MOVE_CELL_ON_ROW_ANIMATION = 3;
    private static final int MOVE_CELL_ON_COLUMN_ANIMATION = 4;

    static final String[] DAYS;

    final byte[] cells = new byte[42];

    private final RectF tempRect = new RectF();

    private int selectionType;

    private float cellSize;
    private float columnWidth;

    private float rrRadiusRatio = RR_RADIUS_RATIO;

    private final Paint dayNumberPaint;
    private final Paint weekdayPaint;

    private final Paint dualAnimSelectionPaint;
    private final Paint selectionPaint;

    private final Paint cellHoverPaint;
    private final Paint cellHoverOnSelectionPaint;

    private int currentMonthColor;
    private int notCurrentMonthColor;
    private int disabledDayNumberColor;
    private int todayColor;

    @Nullable
    private Path selectedMonthPath;

    private long lastTouchTime = -1;
    private int lastTouchCell = -1;

    private float weekSelectionFraction = 0f;

    @Nullable
    private OnSelectionListener onSelectionListener;

    private long currentMonthRange;
    private long enabledCellRange = ALL_SELECTED;

    private int todayIndex = -1;
    private int hoverIndex = -1;

    int year;
    int month;

    private int prevSelectedCell;
    int selectedCell;
    private long selectedRange;

    private long dualAnimSelectedWeekRange;
    private int selectedWeekIndex;

    private int customAnim = 0;
    private float customAnimFraction = 0f;

    private boolean isDualAnimRunning = false;
    private boolean isClearHoverAnimation;
    boolean isFirstDaySunday;

    final CalendarResources cr;
    final TouchHelper touchHelper;

    private int weekdayType = WeekdayType.SHORT;

    int clickOnCellSelectionBehavior;

    private final InternalAnimationHandler weekAnimation = new InternalAnimationHandler() {
        @Override
        public void onEnd() {
            isDualAnimRunning = false;
            weekSelectionFraction = 1f;

            invalidate();
        }

        @Override
        public void onTick(float fraction) {
            weekSelectionFraction = fraction;

            if (isDualAnimRunning) {
                setInvFractionAlphaOnDualPaint(fraction);
            }
        }
    };

    private final InternalAnimationHandler customFractionAnimation = new InternalAnimationHandler() {
        @Override
        public void onEnd() {
            customAnim = 0;
        }

        @Override
        public void onTick(float fraction) {
            customAnimFraction = fraction;
        }
    };

    private final InternalAnimationHandler selectionBgAnimation = new InternalAnimationHandler() {
        @Override
        public void onEnd() {
            isDualAnimRunning = false;
            selectionPaint.setAlpha(255);
            dualAnimSelectionPaint.setAlpha(0);
        }

        @Override
        public void onTick(float fraction) {
            int alpha = (int) (fraction * 255);

            selectionPaint.setAlpha(alpha);
            dualAnimSelectionPaint.setAlpha(255 - alpha);
        }
    };

    private final class HoverAnimationHandler extends InternalAnimationHandler {
        private final Paint paint;
        private final int originAlpha;

        public HoverAnimationHandler(@NotNull Paint paint) {
            super(HOVER_ALPHA_ANIM_DURATION);

            this.paint = paint;
            originAlpha = paint.getAlpha();
        }

        @Override
        public void onEnd() {
            paint.setAlpha(forward ? originAlpha : 0);
            if (isClearHoverAnimation) {
                hoverIndex = -1;
            }

            isClearHoverAnimation = false;
        }

        @Override
        public void onTick(float fraction) {
            paint.setAlpha((int) (fraction * originAlpha));
        }
    }

    private final HoverAnimationHandler hoverAlphaAnimation;
    private final HoverAnimationHandler hoverOnSelectionAnimation;

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

        currentMonthColor = defTextColor;
        notCurrentMonthColor = cr.textColorNotCurrentMonth;
        disabledDayNumberColor = cr.textColorDisabled;
        todayColor = colorPrimary;

        selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectionPaint.setColor(colorPrimary);
        selectionPaint.setStyle(Paint.Style.FILL);

        dualAnimSelectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dualAnimSelectionPaint.setColor(colorPrimary);
        dualAnimSelectionPaint.setStyle(Paint.Style.FILL);

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

        hoverAlphaAnimation = new HoverAnimationHandler(cellHoverPaint);
        hoverOnSelectionAnimation = new HoverAnimationHandler(cellHoverOnSelectionPaint);
    }

    public void setSelectionColor(@ColorInt int color) {
        selectionPaint.setColor(color);
        invalidate();
    }

    public void setDayNumberTextSize(float size) {
        dayNumberPaint.setTextSize(size);
        invalidate();
    }

    public void setCurrentMonthDayNumberColor(@ColorInt int color) {
        currentMonthColor = color;
        invalidate();
    }

    public void setNotCurrentMonthDayNumberColor(@ColorInt int color) {
        notCurrentMonthColor = color;
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
            refreshSelectedMonthPath();
        }

        invalidate();
    }

    public void setCurrentMonthRange(long range) {
        this.currentMonthRange = range;

        reselect();
    }

    public void setEnabledCellRange(long range) {
        this.enabledCellRange = range;

        reselect();
    }

    public void setWeekdayType(@WeekdayTypeInt int type) {
        // There is no narrow weekdays before API < 24
        if(Build.VERSION.SDK_INT < 24 && type == WeekdayType.NARROW) {
            type = WeekdayType.SHORT;
        }

        if(!(type == WeekdayType.SHORT || type == WeekdayType.NARROW)) {
            throw new IllegalArgumentException("Invalid weekday type. Use constants from WeekdayType");
        }

        this.weekdayType = type;

        if(selectionType == SelectionType.MONTH) {
            refreshSelectedMonthPath();
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

        clearSelection(false);

        switch (savedSelectionType) {
            case SelectionType.CELL:
                selectCell(savedSelectedCell, true);
                break;
            case SelectionType.WEEK:
                selectWeek(savedWeekIndex, true);
                break;
            case SelectionType.MONTH:
                selectMonth(true);
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

        if (selectionType == SelectionType.MONTH) {
            refreshSelectedMonthPath();
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
        if(selectionType == SelectionType.CELL && selectedCell == index) {
            if(isUser && clickOnCellSelectionBehavior == ClickOnCellSelectionBehavior.CLEAR) {
                clearSelection();
            }

            return;
        }

        if (hoverIndex >= 0) {
            clearHoverIndex();
        }

        int oldSelType = selectionType;
        prevSelectedCell = selectedCell;

        selectionType = SelectionType.CELL;
        selectedCell = index;

        OnSelectionListener listener = onSelectionListener;
        if (listener != null) {
            listener.onCellSelected(index);
        }

        if (doAnimation) {
            if (oldSelType == SelectionType.WEEK) {
                int weekStart = PositiveIntRange.getStart(selectedRange);
                int weekEnd = PositiveIntRange.getEnd(selectedRange);

                if (index >= weekStart && index <= weekEnd) {
                    customAnim = WEEK_TO_CELL_ON_ROW_ANIMATION;
                    customFractionAnimation.start();

                    return;
                }
            } else if (oldSelType == SelectionType.CELL) {
                int gridY = index / 7;
                int prevGridY = prevSelectedCell / 7;

                if (prevGridY == gridY) {
                    customAnim = MOVE_CELL_ON_ROW_ANIMATION;
                    customFractionAnimation.start();
                } else {
                    int gridX = index - gridY * 7;
                    int prevGridX = prevSelectedCell - prevGridY * 7;

                    if (gridX == prevGridX) {
                        customAnim = MOVE_CELL_ON_COLUMN_ANIMATION;
                        customFractionAnimation.start();
                    } else {
                        isDualAnimRunning = true;
                        startSelectionBackgroundAnimation();
                    }
                }

                return;
            }

            startSelectionBackgroundAnimation();
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

        int oldSelType = selectionType;
        long oldWeekRange = selectedRange;

        selectionType = SelectionType.WEEK;
        selectedWeekIndex = weekIndex;
        selectedRange = PositiveIntRange.create(start, end);

        OnSelectionListener listener = onSelectionListener;
        if (listener != null) {
            listener.onWeekSelected(weekIndex, start, end);
        }

        if (doAnimation) {
            if (oldSelType == SelectionType.CELL && selectedCell >= start && selectedCell <= end) {
                customAnim = CELL_TO_WEEK_ON_ROW_ANIMATION;
                weekSelectionFraction = 1f;

                customFractionAnimation.start();
            } else {
                dualAnimSelectedWeekRange = oldWeekRange;
                isDualAnimRunning = (oldSelType == SelectionType.WEEK);

                weekAnimation.start();
            }
        } else {
            weekSelectionFraction = 1f;
            invalidate();
        }
    }

    private void setInvFractionAlphaOnDualPaint(float fraction) {
        int alpha = 255 - (int) (fraction * 255f);

        dualAnimSelectionPaint.setAlpha(alpha);
    }

    public void selectMonth() {
        selectMonth(true);
    }

    public void selectMonth(boolean doAnimation) {
        selectionType = SelectionType.MONTH;

        long oldSelectedMonthRange = selectedRange;
        selectedRange = PositiveIntRange.findIntersection(
                currentMonthRange,
                enabledCellRange
        );

        if (selectedRange == PositiveIntRange.NO_INTERSECTION) {
            if (selectedMonthPath != null) {
                selectedMonthPath.rewind();
            }
        } else if (oldSelectedMonthRange != selectedRange) {
            refreshSelectedMonthPath();
        } else {
            return;
        }

        OnSelectionListener listener = onSelectionListener;
        if (listener != null) {
            listener.onMonthSelected();
        }

        if (doAnimation) {
            startSelectionBackgroundAnimation();
        } else {
            invalidate();
        }
    }

    private void startSelectionBackgroundAnimation() {
        selectionPaint.setAlpha(0);
        dualAnimSelectionPaint.setAlpha(255);

        selectionBgAnimation.start();
    }

    private void setHoverIndex(int index) {
        if ((selectionType == SelectionType.CELL && selectedCell == index) || hoverIndex == index) {
            return;
        }

        hoverIndex = index;

        if (isSelectionRangeContainsIndex(index)) {
            hoverOnSelectionAnimation.start();
        } else {
            hoverAlphaAnimation.start();
        }
    }

    void clearHoverIndex() {
        isClearHoverAnimation = true;

        if (isSelectionRangeContainsIndex(hoverIndex)) {
            hoverOnSelectionAnimation.start(false);
        } else {
            hoverAlphaAnimation.start(false);
        }
    }

    public void clearSelection() {
        clearSelection(true);
    }

    private void clearSelection(boolean fireEvent) {
        if(selectionType == SelectionType.NONE) {
            return;
        }

        selectionType = SelectionType.NONE;
        selectedCell = -1;
        selectedWeekIndex = -1;
        selectedRange = 0;

        OnSelectionListener listener = onSelectionListener;
        if(fireEvent && listener != null) {
            listener.onSelectionCleared();
        }

        invalidate();
    }

    public void onGridChanged() {
        invalidate();
        touchHelper.invalidateRoot();
    }

    @Override
    public void onDraw(@NotNull Canvas c) {
        drawSelection(c);
        drawWeekdayRow(c);
        drawCells(c);
    }

    private void drawSelection(@NotNull Canvas c) {
        switch (customAnim) {
            case WEEK_TO_CELL_ON_ROW_ANIMATION:
                drawWeekToCellSelection(c);
                break;
            case CELL_TO_WEEK_ON_ROW_ANIMATION:
                drawCellToWeekSelection(c);
                break;
            case MOVE_CELL_ON_ROW_ANIMATION:
                drawCellSelection(
                        c,
                        selectedCell,
                        customAnimFraction, 1f,
                        selectionPaint
                );
                break;
            case MOVE_CELL_ON_COLUMN_ANIMATION:
                drawCellSelection(
                        c,
                        selectedCell,
                        1f, customAnimFraction,
                        selectionPaint
                );
                break;
            default:
                switch (selectionType) {
                    case SelectionType.CELL:
                        if (isDualAnimRunning) {
                            drawCellSelection(c, prevSelectedCell, 1f, 1f, dualAnimSelectionPaint);
                        }

                        drawCellSelection(c, selectedCell, 1f, 1f, selectionPaint);
                        break;
                    case SelectionType.WEEK:
                        if (isDualAnimRunning) {
                            drawWeekFromCenterSelection(
                                    c,
                                    1f,
                                    dualAnimSelectedWeekRange,
                                    dualAnimSelectionPaint
                            );
                        }

                        drawWeekFromCenterSelection(c, weekSelectionFraction, selectedRange, selectionPaint);

                        break;
                    case SelectionType.MONTH:
                        drawMonthSelection(c);
                        break;
                }
        }
    }

    private void drawWeekSelection(
            @NotNull Canvas c,
            float leftAnchor,
            float rightAnchor,
            float fraction,
            long weekRange,
            @NotNull Paint paint
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

        c.drawRoundRect(tempRect, radius, radius, paint);
    }

    private void drawWeekFromCenterSelection(
            @NotNull Canvas c,
            float fraction,
            long range,
            @NotNull Paint paint
    ) {
        int selStart = PositiveIntRange.getStart(range);
        int selEnd = PositiveIntRange.getEnd(range);

        float midX = cr.hPadding +
                (float) (selStart + selEnd - 14 * (selStart / 7) + 1) * cellSize * 0.5f;

        drawWeekSelection(c, midX, midX, fraction, range, paint);
    }

    private void drawWeekToCellSelection(@NotNull Canvas c) {
        drawWeekCellSelection(c, 1f - customAnimFraction);
    }

    private void drawCellToWeekSelection(@NotNull Canvas c) {
        drawWeekCellSelection(c, customAnimFraction);
    }

    private void drawWeekCellSelection(@NotNull Canvas c, float fraction) {
        float cellLeft = getCellLeft(selectedCell % 7);
        float cellRight = cellLeft + cellSize;

        drawWeekSelection(c, cellLeft, cellRight, fraction, selectedRange, selectionPaint);
    }

    private void drawMonthSelection(@NotNull Canvas c) {
        if (selectedMonthPath == null) {
            Log.e(TAG, "selectionMonthPath == null");
        } else {
            c.drawPath(selectedMonthPath, selectionPaint);
        }
    }

    private void drawCellSelection(
            @NotNull Canvas c,
            int cell,
            float xFraction, float yFraction,
            @NotNull Paint paint
    ) {
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

        c.drawRoundRect(tempRect, radius, radius, paint);
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
            if (PositiveIntRange.contains(currentMonthRange, i)) {
                if (i == todayIndex) {
                    if (isSelectionRangeContainsIndex(i)) {
                        cellType = CELL_CURRENT_MONTH;
                    } else {
                        cellType = CELL_TODAY;
                    }
                } else {
                    cellType = CELL_CURRENT_MONTH;
                }
            } else {
                cellType = CELL_NOT_CURRENT_MONTH;
            }
        } else {
            cellType = CELL_DISABLED;
        }

        if (i == hoverIndex) {
            cellType |= (isSelectionRangeContainsIndex(i) ?
                    CELL_HOVER_ON_SELECTION_BIT :
                    CELL_HOVER_BIT);
        }

        return cellType;
    }

    private void drawCell(@NotNull Canvas c, float x, float y, int day, int cellType) {
        boolean isAnyHover = (cellType & (CELL_HOVER_BIT | CELL_HOVER_ON_SELECTION_BIT)) != 0;
        int color = 0;

        switch (cellType & CELL_DATA_MASK) {
            case CELL_SELECTED:
            case CELL_CURRENT_MONTH:
                color = currentMonthColor;
                break;
            case CELL_NOT_CURRENT_MONTH:
                color = notCurrentMonthColor;
                break;
            case CELL_DISABLED:
                color = disabledDayNumberColor;
                break;
            case CELL_TODAY:
                color = isAnyHover ? currentMonthColor : todayColor;
                break;
        }

        if (isAnyHover) {
            Paint hoverPaint;
            if ((cellType & CELL_HOVER_BIT) != 0) {
                hoverPaint = cellHoverPaint;
            } else {
                hoverPaint = cellHoverOnSelectionPaint;
            }

            float radius = rrRadius();

            tempRect.set(x, y, x + cellSize, y + cellSize);

            c.drawRoundRect(
                    tempRect,
                    radius, radius,
                    hoverPaint
            );
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

    private void refreshSelectedMonthPath() {
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

    private float gridTop() {
        float height;
        if(weekdayType == WeekdayType.SHORT) {
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
