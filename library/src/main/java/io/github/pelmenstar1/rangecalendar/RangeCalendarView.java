package io.github.pelmenstar1.rangecalendar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.icu.text.DisplayContext;
import android.os.Build;
import android.os.Parcelable;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Choreographer;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.res.ResourcesCompat;
import androidx.viewpager2.widget.ViewPager2;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * The special calendar where user can select day, week or month.
 * Minimum and maximum dates can be set by {@link RangeCalendarView#setMinDateEpoch(long)} and {@link RangeCalendarView#setMaxDateEpoch(long)}.
 * There also another convenience versions of these methods.
 * To listen when day, week or month is selected, use {@link RangeCalendarView#setOnSelectionListener(OnSelectionListener)}.
 * To select programmatically, use {@link RangeCalendarView#selectDay(int)}, {@link RangeCalendarView#selectWeek(int, int, int)}, {@link RangeCalendarView#selectMonth(int, int)}.
 * <br/>
 * Most appropriate colors are automatically extracted from attributes, but you can still change it.
 */
public final class RangeCalendarView extends ViewGroup {
    /**
     * Fires appropriate method when user selects day, week or month.
     * <p>
     * Because of problems with dates in Java, there is no methods like: <br/>
     * {@code void onDateSelected(LocalDate)} <br/>
     * because you would need to implement multiple methods (like {@code onDateSelected(LocalDate)}, {@code onDateSelected(Calendar)}, {@code onDateSelected(int, int, int)})
     * instead of one.
     * So you need to convert raw year, month and day yourself.
     */
    public interface OnSelectionListener {
        /**
         * Fires when selection is cleared.
         * It can happen when selection view is on screen and clear selection button is clicked
         */
        void onSelectionCleared();

        /**
         * Fires when user selects a day
         *
         * @param year  year of selected date
         * @param month month of selected date, 1-based
         * @param day   day of month of selected date, 1-based
         */
        void onDaySelected(int year, int month, int day);

        /**
         * Fires when user selects a week
         *
         * @param weekIndex  index of the week, 0-based
         * @param startYear  year of selection's start
         * @param startMonth month of selection's start, 1-based
         * @param startDay   day of month of selection's start, 1-based
         * @param endYear    year of selection's end
         * @param endMonth   month of selection's end, 1-based
         * @param endDay     day of month of selection's end, 1-based
         */
        void onWeekSelected(
                int weekIndex,
                int startYear, int startMonth, int startDay,
                int endYear, int endMonth, int endDay
        );

        /**
         * Fires when user selects a month
         *
         * @param year  year of selection
         * @param month month of selection, 1-based
         */
        void onMonthSelected(int year, int month);
    }

    private static final String TAG = RangeCalendarView.class.getSimpleName();

    private static final String DATE_FORMAT = "MMMM y";

    private static final Choreographer choreographer = Choreographer.getInstance();

    private static final long SV_TRANSITION_DURATION = 300 * 1_000_000;

    static final int MIN_DATE = DateInt.create(1970, 1, 1);
    static final int MAX_DATE = DateInt.create(Short.MAX_VALUE, 12, 30);

    public static final long MIN_DATE_EPOCH = DateInt.toEpochDay(MIN_DATE);
    public static final long MAX_DATE_EPOCH = DateInt.toEpochDay(MAX_DATE);

    private final ViewPager2 pager;
    private final ImageButton prevButton;
    private final ImageButton nextOrClearButton;
    private final TextView infoView;

    @Nullable
    private OnSelectionListener onSelectionListener;

    private int minDate = MIN_DATE;
    private int maxDate = MAX_DATE;

    private long minDateEpoch = MIN_DATE_EPOCH;
    private long maxDateEpoch = MAX_DATE_EPOCH;

    private int infoViewYm;

    private boolean isReceiverRegistered;

    private int currentCalendarYm;
    private final RangeCalendarPagerAdapter adapter;

    private final int buttonSize;
    private final int hPadding;
    private final int topContainerMarginBottom;

    private SimpleDateFormat dateFormatter;

    // formatter for API >= 24
    @RequiresApi(24)
    private android.icu.text.SimpleDateFormat dateFormatter24;

    private final Date cachedDate = new Date();

    private View selectionView;

    private long svTransitionDurationNanos = SV_TRANSITION_DURATION;
    private long svTransitionStartTime;
    private boolean isSVTransitionForward;
    private boolean hasSvClearButton = true;

    private boolean isSelectionViewOnScreen;

    @NotNull
    private CalendarSelectionViewLayoutParams svLayoutParams = CalendarSelectionViewLayoutParams.DEFAULT;

    private final ArrowToCloseDrawable nextIcon;

    private boolean isFirstDaySunday;

    private final String nextMonthDescription;
    private final String clearSelectionDescription;

    private final Rect layoutRect = new Rect();
    private final Rect layoutOutRect = new Rect();

    private final BroadcastReceiver onDateChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(@NotNull Context context, @NotNull Intent intent) {
            String action = intent.getAction();

            if (action.equals(Intent.ACTION_DATE_CHANGED)) {
                refreshToday();
            }
        }
    };

    private final Choreographer.FrameCallback svTransitionTickCb = this::onSVTransitionTick;
    private final Choreographer.FrameCallback svStartCb = time -> {
        svTransitionStartTime = time;
        onSVTransitionTickFraction(isSVTransitionForward ? 0f : 1f);

        choreographer.postFrameCallback(svTransitionTickCb);
    };

    public RangeCalendarView(@NotNull Context context) {
        this(context, null, 0);
    }

    public RangeCalendarView(@NotNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RangeCalendarView(@NotNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        Resources res = context.getResources();
        Resources.Theme theme = context.getTheme();

        buttonSize = res.getDimensionPixelSize(R.dimen.rangeCalendar_actionButtonSize);

        topContainerMarginBottom = res.getDimensionPixelOffset(R.dimen.rangeCalendar_topContainerMarginBottom);

        initLocaleDependentValues();

        Drawable selectableBg = getSelectableItemBackground(context);

        CalendarResources cr = new CalendarResources(context);
        hPadding = (int)cr.hPadding;

        int today = DateInt.today();

        adapter = new RangeCalendarPagerAdapter(context, cr, isFirstDaySunday);
        adapter.setToday(today);
        adapter.setOnSelectionListener(new OnSelectionListener() {
            @Override
            public void onSelectionCleared() {
                if(selectionView != null) {
                    startSelectionViewTransition(false);
                }

                OnSelectionListener listener = onSelectionListener;
                if(listener != null) {
                    listener.onSelectionCleared();
                }
            }

            @Override
            public void onDaySelected(int year, int month, int day) {
                if (selectionView != null) {
                    startSelectionViewTransition(true);
                }

                OnSelectionListener listener = onSelectionListener;
                if (listener != null) {
                    listener.onDaySelected(year, month, day);
                }
            }

            @Override
            public void onWeekSelected(int weekIndex, int startYear, int startMonth, int startDay, int endYear, int endMonth, int endDay) {
                if (selectionView != null) {
                    startSelectionViewTransition(true);
                }

                OnSelectionListener listener = onSelectionListener;
                if (listener != null) {
                    listener.onWeekSelected(
                            weekIndex,
                            startYear, startMonth, startDay,
                            endYear, endMonth, endDay
                    );
                }
            }

            @Override
            public void onMonthSelected(int year, int month) {
                if (selectionView != null) {
                    startSelectionViewTransition(true);
                }

                OnSelectionListener listener = onSelectionListener;
                if (listener != null) {
                    listener.onMonthSelected(year, month);
                }
            }
        });

        pager = new ViewPager2(context);
        pager.setAdapter(adapter);
        pager.setOffscreenPageLimit(3);

        pager.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                int alpha = (int)(510f * Math.abs(0.5f - positionOffset));

                setButtonAlphaIfEnabled(prevButton, alpha);

                if(!hasSvClearButton || !isSelectionViewOnScreen) {
                    setButtonAlphaIfEnabled(nextOrClearButton, alpha);
                }
            }

            @Override
            public void onPageSelected(int position) {
                int ym = adapter.getYearMonthForCalendar(position);
                currentCalendarYm = ym;
                setInfoViewYearMonth(ym);

                setButtonAlphaIfEnabled(prevButton, 255);
                setButtonAlphaIfEnabled(nextOrClearButton, 255);
                updateMoveButtons();
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    adapter.clearHoverAt(adapter.getItemPositionForYearMonth(currentCalendarYm));
                }
            }
        });

        ArrowToCloseDrawable leftIcon = new ArrowToCloseDrawable(
                context, cr.colorControlNormal, ArrowToCloseDrawable.DIRECTION_LEFT
        );

        nextIcon = new ArrowToCloseDrawable(
                context, cr.colorControlNormal, ArrowToCloseDrawable.DIRECTION_RIGHT
        );

        nextMonthDescription = res.getString(R.string.nextMonthDescription);
        clearSelectionDescription = res.getString(R.string.clearSelectionDescription);

        prevButton = new AppCompatImageButton(context);
        prevButton.setImageDrawable(leftIcon);
        prevButton.setOnClickListener(v -> moveToPreviousMonth());
        prevButton.setContentDescription(res.getString(R.string.previousMonthDescription));

        nextOrClearButton = new AppCompatImageButton(context);
        nextOrClearButton.setImageDrawable(nextIcon);
        nextOrClearButton.setContentDescription(nextMonthDescription);
        nextOrClearButton.setOnClickListener(v -> {
            if (isSelectionViewOnScreen && hasSvClearButton) {
                clearSelection();
            } else {
                moveToNextMonth();
            }
        });

        if(selectableBg != null) {
            prevButton.setBackground(selectableBg);
            nextOrClearButton.setBackground(selectableBg.getConstantState().newDrawable(res));
        }

        infoView = new AppCompatTextView(context);
        infoView.setTextColor(cr.textColor);
        infoView.setOnClickListener(v -> {
            adapter.select(SelectionType.MONTH, currentCalendarYm, true);
        });

        addView(pager);
        addView(prevButton);
        addView(nextOrClearButton);
        addView(infoView);

        int todayPos = adapter.getItemPositionForDate(today);
        pager.setCurrentItem(todayPos, false);
        setInfoViewYearMonth(YearMonth.forDate(today));

        if(attrs != null) {
            initFromAttributes(context, attrs, defStyleAttr);
        }
    }

    private void initFromAttributes(
            @NotNull Context context,
            @NotNull AttributeSet attrs,
            int defStyleAttr) {
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.RangeCalendarView, defStyleAttr, 0
        );

        try {
            int selectionColor = a.getColor(
                    R.styleable.RangeCalendarView_rangeCalendar_selectionColor,
                    0
            );
            if(selectionColor != 0) {
                setSelectionColor(selectionColor);
            }

            float dayNumberTextSize = a.getDimension(
                    R.styleable.RangeCalendarView_rangeCalendar_dayNumberTextSize,
                    Float.NaN
            );
            if(!Float.isNaN(dayNumberTextSize)) {
                setDayNumberTextSize(dayNumberTextSize);
            }

            int currentMonthDayNumberColor = a.getColor(
                    R.styleable.RangeCalendarView_rangeCalendar_currentMonthDayNumberColor,
                    0
            );
            if(currentMonthDayNumberColor != 0) {
                setCurrentMonthDayNumberColor(currentMonthDayNumberColor);
            }

            int notCurrentMonthDayNumberColor = a.getColor(
                    R.styleable.RangeCalendarView_rangeCalendar_notCurrentMonthDayNumberColor,
                    0
            );
            if(notCurrentMonthDayNumberColor != 0) {
                setNotCurrentMonthDayNumberColor(notCurrentMonthDayNumberColor);
            }

            int disabledDayNumberColor = a.getColor(
                    R.styleable.RangeCalendarView_rangeCalendar_disabledDayNumberColor,
                    0
            );
            if(disabledDayNumberColor != 0) {
                setDisabledDayNumberColor(disabledDayNumberColor);
            }

            int todayColor = a.getColor(
                    R.styleable.RangeCalendarView_rangeCalendar_todayColor,
                    0
            );
            if(todayColor != 0) {
                setTodayColor(todayColor);
            }

            int weekdayColor = a.getColor(
                    R.styleable.RangeCalendarView_rangeCalendar_weekdayColor,
                    0
            );
            if(weekdayColor != 0) {
                setWeekdayColor(weekdayColor);
            }

            float weekdayTextSize = a.getDimension(
                    R.styleable.RangeCalendarView_rangeCalendar_weekdayTextSize,
                    Float.NaN
            );
            if(!Float.isNaN(weekdayTextSize)) {
                setWeekdayTextSize(weekdayTextSize);
            }

            int hoverColor = a.getColor(
                    R.styleable.RangeCalendarView_rangeCalendar_hoverColor,
                    0
            );
            if(hoverColor != 0) {
                setHoverColor(hoverColor);
            }

            int hoverOnSelectionColor = a.getColor(
                    R.styleable.RangeCalendarView_rangeCalendar_hoverOnSelectionColor,
                    0
            );
            if(hoverOnSelectionColor != 0) {
                setHoverOnSelectionColor(hoverOnSelectionColor);
            }

            float cellSize = a.getDimension(R.styleable.RangeCalendarView_rangeCalendar_cellSize, Float.NaN);
            if(!Float.isNaN(cellSize)) {
                setCellSize(cellSize);
            }

            int weekdayType = a.getInteger(R.styleable.RangeCalendarView_rangeCalendar_weekdayType, -1);
            if(weekdayType != -1) {
                if(weekdayType == WeekdayType.SHORT || weekdayType == WeekdayType.NARROW) {
                    setWeekdayType(weekdayType);
                }
            }

            float rrRadiusRatio = a.getFraction(
                    R.styleable.RangeCalendarView_rangeCalendar_roundRectRadiusRatio,
                    1,
                    1,
                    Float.NaN
            );

            if (!Float.isNaN(rrRadiusRatio)) {
                setRoundRectRadiusRatio(rrRadiusRatio);
            }
        } finally {
            a.recycle();
        }
    }

    private void refreshToday() {
        int today = DateInt.today();

        adapter.setToday(today);
    }

    private void initLocaleDependentValues() {
        Locale locale = LocaleUtils.getLocale(getContext());

        String dateFormat;

        if (Build.VERSION.SDK_INT >= 18) {
            dateFormat = DateFormat.getBestDateTimePattern(locale, DATE_FORMAT);
        } else {
            dateFormat = DATE_FORMAT;
        }

        if (Build.VERSION.SDK_INT >= 24) {
            dateFormatter24 = new android.icu.text.SimpleDateFormat(dateFormat, locale);
            dateFormatter24.setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE);
        } else {
            dateFormatter = new SimpleDateFormat(dateFormat, locale);
        }

        isFirstDaySunday = Calendar
                .getInstance(locale)
                .getFirstDayOfWeek() == Calendar.SUNDAY;
    }

    @NotNull
    @Override
    protected Parcelable onSaveInstanceState() {
        SavedState state = new SavedState(super.onSaveInstanceState());
        state.selectionType = adapter.selectionType;
        state.selectionData = adapter.selectionData;

        state.ym = currentCalendarYm;

        return state;
    }

    @Override
    protected void onRestoreInstanceState(@NotNull Parcelable state) {
        if (state instanceof SavedState) {
            SavedState s = (SavedState) state;
            super.onRestoreInstanceState(s.getSuperState());

            int ym = s.ym;

            setYearAndMonthInternal(YearMonth.getYear(ym), YearMonth.getMonth(ym), false);

            if (s.selectionType != SelectionType.NONE) {
                adapter.select(s.selectionType, s.selectionData, false);
            }
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!isReceiverRegistered) {
            isReceiverRegistered = true;

            registerReceiver();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        unregisterReceiver();
        isReceiverRegistered = false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        pager.measure(widthMeasureSpec, heightMeasureSpec);

        int pagerWidth = pager.getMeasuredWidth();

        int buttonSpec = MeasureSpec.makeMeasureSpec(buttonSize, MeasureSpec.EXACTLY);
        int infoWidthSpec = MeasureSpec.makeMeasureSpec(pager.getMeasuredWidth(), MeasureSpec.AT_MOST);
        int infoHeightSpec = MeasureSpec.makeMeasureSpec(buttonSize, MeasureSpec.AT_MOST);

        if (selectionView != null) {
            int maxWidth = pagerWidth - 2 * hPadding - buttonSize;
            if(!hasSvClearButton) {
                maxWidth -= buttonSize;
            }

            measureChild(
                    selectionView,
                    MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(buttonSize, MeasureSpec.AT_MOST)
            );
        }

        infoView.measure(infoWidthSpec, infoHeightSpec);
        prevButton.measure(buttonSpec, buttonSpec);
        nextOrClearButton.measure(buttonSpec, buttonSpec);

        setMeasuredDimension(
                pager.getMeasuredWidthAndState(),
                resolveSize(pager.getMeasuredHeight() + buttonSize + topContainerMarginBottom, heightMeasureSpec)
        );
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int height = b - t;

        int prevRight = hPadding + buttonSize;
        int nextLeft = width - prevRight;

        View sv = selectionView;
        if (sv != null) {
            int gravity = svLayoutParams.gravity;
            int svWidth = sv.getMeasuredWidth();
            int svHeight = sv.getMeasuredHeight();

            // If selection view is wanted to be in center on x-axis,
            // let it be actual center of the whole view.
            if(gravity == Gravity.CENTER || gravity == Gravity.CENTER_HORIZONTAL) {
                layoutRect.set(
                        hPadding, 0,
                        width - hPadding, buttonSize
                );
            } else {
                layoutRect.set(
                        hasSvClearButton ? hPadding : prevRight, 0,
                        nextLeft, buttonSize
                );
            }

            if(Build.VERSION.SDK_INT >= 17) {
                Gravity.apply(
                        svLayoutParams.gravity,
                        svWidth, svHeight,
                        layoutRect, layoutOutRect,
                        getLayoutDirection()
                );
            } else {
                Gravity.apply(
                        svLayoutParams.gravity,
                        svWidth, svHeight,
                        layoutRect, layoutOutRect
                );
            }

            sv.layout(
                    layoutOutRect.left, layoutOutRect.top,
                    layoutOutRect.right, layoutOutRect.bottom
            );
        }

        int infoWidth = infoView.getMeasuredWidth();
        int infoHeight = infoView.getMeasuredHeight();

        int infoLeft = (width - infoWidth) / 2;
        int infoTop = (buttonSize - infoHeight) / 2;

        prevButton.layout(hPadding, 0, prevRight, buttonSize);
        nextOrClearButton.layout(nextLeft, 0, nextLeft + buttonSize, buttonSize);

        infoView.layout(infoLeft, infoTop, infoLeft + infoWidth, infoTop + infoHeight);

        int pagerTop = buttonSize + topContainerMarginBottom;

        pager.layout(0, pagerTop, pager.getMeasuredWidth(), pagerTop + pager.getMeasuredHeight());
    }

    @Nullable
    private Drawable getSelectableItemBackground(@NotNull Context context) {
        if(Build.VERSION.SDK_INT >= 21) {
            Resources.Theme theme = context.getTheme();

            TypedValue value = new TypedValue();
            theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true);

            return ResourcesCompat.getDrawable(context.getResources(), value.resourceId, theme);
        }
        return null;
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_DATE_CHANGED);

        getContext().registerReceiver(onDateChangedReceiver, filter);
    }

    private void unregisterReceiver() {
        getContext().unregisterReceiver(onDateChangedReceiver);
    }

    /**
     * Gets selection view of calendar.
     */
    @Nullable
    public View getSelectionView() {
        return selectionView;
    }

    /**
     * Sets selection view.
     * When user selects a day or range, this view replace "previous" button and current year & month text view.
     * "Next" button will become "Close" button which clears selection.
     * Set to null not to show any view on selection.
     * <br>
     * Action button or another useful content can be shown.
     * This might be helpful for rational use of space.
     */
    public void setSelectionView(@Nullable View view) {
        View oldSelectionView = selectionView;

        this.selectionView = view;

        if (oldSelectionView != null) {
            // On 0 fraction, selection view will disappear
            onSVTransitionTickFraction(0f);

            // selection view is always in end
            removeViewAt(getChildCount() - 1);
        }

        if (view != null) {
            view.setVisibility(INVISIBLE);
            addView(view);
        }

        requestLayout();
    }

    /**
     * Gets duration (in milliseconds) of transition
     * from "previous" button and year & month text view to selection view and vise verse.
     */
    public long getSelectionViewTransitionDuration() {
        return svTransitionDurationNanos / 1_000_000;
    }

    /**
     * Sets duration (in milliseconds) of transition
     * from "previous" button and year & month text view to selection view and vise verse.
     */
    public void setSelectionViewTransitionDuration(long duration) {
        this.svTransitionDurationNanos = duration * 1_000_000;
    }

    @NotNull
    public CalendarSelectionViewLayoutParams getSelectionViewLayoutParams() {
        return svLayoutParams;
    }

    public void setSelectionViewLayoutParams(@NotNull CalendarSelectionViewLayoutParams layoutParams) {
        this.svLayoutParams = layoutParams;

        requestLayout();
    }

    /**
     * Returns whether selection view on selection should have clear selection button. By default, it's true.
     */
    public boolean hasSelectionViewClearButton() {
        return hasSvClearButton;
    }

    /**
     * Sets whether selection view on selection should have clear selection button.
     *
     * If true, then when selection happens, "previous" button and year & month view will disappear,
     * "next" button will become clear selection button and selection view will appear.
     * <b>Note that, user won't be able to use move buttons, but be able to slide calendars. </b>
     * <br/> <br/>
     * If false, then when selection happens, year & month view will disappear, no move buttons will disappear,
     * selection will be placed where year & month view. User will still be able to use move buttons
     */
    public void setHasSelectionViewClearButton(boolean value) {
        if(hasSvClearButton == value) {
            return;
        }

        hasSvClearButton = value;

        requestLayout();
    }

    private void setSelectionViewOnScreen(boolean state) {
        isSelectionViewOnScreen = state;

        View sv = selectionView;

        if(state) {
            sv.setVisibility(VISIBLE);
            infoView.setVisibility(INVISIBLE);

            if(hasSvClearButton) {
                nextOrClearButton.setContentDescription(clearSelectionDescription);
                prevButton.setVisibility(INVISIBLE);
            }
        } else {
            sv.setVisibility(INVISIBLE);
            infoView.setVisibility(VISIBLE);

            if(hasSvClearButton) {
                nextOrClearButton.setContentDescription(nextMonthDescription);
                prevButton.setVisibility(VISIBLE);
            }
        }

        updateMoveButtons();
    }

    private void startSelectionViewTransition(boolean forward) {
        if ((forward && isSelectionViewOnScreen) || (!forward && !isSelectionViewOnScreen)) {
            return;
        }

        isSVTransitionForward = forward;

        choreographer.postFrameCallback(svStartCb);
    }

    private void onSVTransitionTick(long time) {
        long elapsed = time - svTransitionStartTime;

        if (elapsed >= svTransitionDurationNanos) {
            onSVTransitionTickFraction(isSVTransitionForward ? 1f : 0f);
        } else {
            float fraction = (float) elapsed / svTransitionDurationNanos;

            if (!isSVTransitionForward) {
                fraction = 1f - fraction;
            }

            onSVTransitionTickFraction(fraction);

            choreographer.postFrameCallback(svTransitionTickCb);
        }
    }

    private void onSVTransitionTickFraction(float fraction) {
        View sv = selectionView;

        if(hasSvClearButton) {
            nextIcon.onAnimationFraction(fraction);
        }

        if (fraction < 0.5f) {
            float f = fraction * -2f;

            if(hasSvClearButton) {
                prevButton.setTranslationY(prevButton.getBottom() * f);
            }

            infoView.setTranslationY(infoView.getBottom() * f);

            if (isSelectionViewOnScreen) {
                setSelectionViewOnScreen(false);
            }
        } else {
            float f = (2f * fraction - 2f);

            sv.setTranslationY(f * sv.getBottom());



            if (!isSelectionViewOnScreen) {
                setSelectionViewOnScreen(true);
            }
        }

        invalidate();
    }

    /**
     * Returns minimum date of the calendar in epoch days.
     */
    public long getMinDateEpoch() {
        return minDateEpoch;
    }

    /**
     * Sets minimum date in epoch days.
     *
     * @throws IllegalArgumentException if value is less than {@link RangeCalendarView#MIN_DATE_EPOCH} or
     *                                  greater than {@link RangeCalendarView#MAX_DATE_EPOCH}, or if minimum date is greater than maximum.
     */
    public void setMinDateEpoch(long value) {
        ensureEpochDayValid(value, "value");

        if (value > maxDateEpoch) {
            throw new IllegalArgumentException("min > max");
        }

        minDate = DateInt.fromEpochDay(value);
        minDateEpoch = value;

        onMinMaxChanged();
    }

    /**
     * Returns minimum date of the calendar.
     * Can be used if API >= 26.
     *
     * @see RangeCalendarView#getMinDateEpoch()
     */
    @RequiresApi(26)
    @NotNull
    public LocalDate getMinDate() {
        return DateInt.toLocaleDate(minDate);
    }

    /**
     * Sets minimum date.
     * Can be used if API >= 26.
     *
     * @throws IllegalArgumentException if date is less than 1 January 1970 or greater than 30 December 32767, or if minimum date is greater than maximum.
     * @see RangeCalendarView#setMinDateEpoch(long)
     */
    @RequiresApi(26)
    public void setMinDate(@NotNull LocalDate date) {
        setMinDateEpoch(date.toEpochDay());
    }

    /**
     * Sets minimum date to given calendar.
     *
     * @see RangeCalendarView#getMinDateEpoch()
     */
    public void getMinDate(@NotNull Calendar calendar) {
        DateInt.toCalendar(minDate, calendar);
    }

    /**
     * Sets minimum date.
     *
     * @throws IllegalArgumentException if date is less than 1 January 1970 or greater than 30 December 32767, or if minimum date is greater than maximum.
     * @see RangeCalendarView#setMinDateEpoch(long)
     */
    public void setMinDate(@NotNull Calendar calendar) {
        setMinDateEpoch(calendarToEpochDay(calendar));
    }

    /**
     * Sets minimum date.
     *
     * @param year  year, in range [1970; 32767]
     * @param month month, in range [1; 12]
     * @param day   day of month, in range [1; *days in month*]
     * @throws IllegalArgumentException if values is out of ranges
     */
    public void setMinDate(int year, int month, int day) {
        ensureDateValid(year, month, day);

        setMinDateEpoch(DateInt.toEpochDay(year, month, day));
    }

    /**
     * Returns maximum date of the calendar in epoch days.
     */
    public long getMaxDateEpoch() {
        return maxDateEpoch;
    }

    /**
     * Sets maximum date in epoch days.
     *
     * @throws IllegalArgumentException if value is less than {@link RangeCalendarView#MIN_DATE_EPOCH} or
     *                                  greater than {@link RangeCalendarView#MAX_DATE_EPOCH}, or if maximum date is less than minimum.
     */
    public void setMaxDateEpoch(long value) {
        ensureEpochDayValid(value, "value");

        if (value < minDateEpoch) {
            throw new IllegalArgumentException("max < min");
        }

        maxDate = DateInt.fromEpochDay(value);
        maxDateEpoch = value;

        onMinMaxChanged();
    }

    /**
     * Returns maximum date of the calendar.
     * Can be used if API >= 26.
     *
     * @see RangeCalendarView#getMaxDateEpoch()
     */
    @RequiresApi(26)
    @NotNull
    public LocalDate getMaxDate() {
        return DateInt.toLocaleDate(maxDate);
    }

    /**
     * Sets maximum date.
     * Can be used if API >= 26.
     *
     * @throws IllegalArgumentException if date is less than 1 January 1970 or greater than 30 December 32767,
     *                                  or if maximum date is less than minimum.
     * @see RangeCalendarView#setMaxDateEpoch(long)
     */
    @RequiresApi(26)
    public void setMaxDate(@NotNull LocalDate date) {
        setMaxDateEpoch(date.toEpochDay());
    }


    /**
     * Sets maximum date to given calendar.
     *
     * @see RangeCalendarView#getMaxDateEpoch()
     */
    public void getMaxDate(@NotNull Calendar calendar) {
        DateInt.toCalendar(maxDate, calendar);
    }

    /**
     * Sets maximum date.
     *
     * @throws IllegalArgumentException if date is less than 1 January 1970 or greater than 30 December 32767, or if maximum date is less than minimum.
     * @see RangeCalendarView#setMinDateEpoch(long)
     */
    public void setMaxDate(@NotNull Calendar calendar) {
        setMaxDateEpoch(calendarToEpochDay(calendar));
    }

    /**
     * Sets maximum date.
     *
     * @param year  year, in range [1970; 32767]
     * @param month month, in range [1; 12]
     * @param day   day of month, in range [1; *days in month*]
     * @throws IllegalArgumentException if values is out of ranges
     */
    public void setMaxDate(int year, int month, int day) {
        ensureDateValid(year, month, day);

        setMaxDateEpoch(DateInt.toEpochDay(year, month, day));
    }

    private void onMinMaxChanged() {
        adapter.setRange(minDate, minDateEpoch, maxDate, maxDateEpoch);

        pager.setCurrentItem(Math.max(0, adapter.getItemPositionForYearMonth(currentCalendarYm)));
    }

    /**
     * Returns current selection listener
     */
    @Nullable
    public OnSelectionListener getOnSelectionListener() {
        return onSelectionListener;
    }

    /**
     * Sets a selection listener
     */
    public void setOnSelectionListener(@Nullable OnSelectionListener value) {
        this.onSelectionListener = value;
    }

    /**
     * Returns background color of selection
     */
    @ColorInt
    public int getSelectionColor() {
        return adapter.getStyleInt(RangeCalendarPagerAdapter.STYLE_SELECTION_COLOR);
    }

    /**
     * Sets a background color of selection, by default it's primary color
     */
    public void setSelectionColor(@ColorInt int color) {
        adapter.setStyleInt(RangeCalendarPagerAdapter.STYLE_SELECTION_COLOR, color);
    }

    /**
     * Returns a text size of day number, in pixels
     */
    public float getDayNumberTextSize() {
        return adapter.getStyleFloat(RangeCalendarPagerAdapter.STYLE_DAY_NUMBER_TEXT_SIZE);
    }

    /**
     * Sets a text size of day number, in pixels
     */
    public void setDayNumberTextSize(float size) {
        adapter.setStyleFloat(RangeCalendarPagerAdapter.STYLE_DAY_NUMBER_TEXT_SIZE, size);
    }

    /**
     * Returns color of day which is in range of current month
     */
    @ColorInt
    public int getCurrentMonthDayNumberColor() {
        return adapter.getStyleInt(RangeCalendarPagerAdapter.STYLE_CURRENT_MONTH_DAY_NUMBER_COLOR);
    }

    /**
     * Sets color of day which is in range of current month
     */
    public void setCurrentMonthDayNumberColor(@ColorInt int color) {
        adapter.setStyleInt(RangeCalendarPagerAdapter.STYLE_CURRENT_MONTH_DAY_NUMBER_COLOR, color);
    }

    /**
     * Returns color of day which is not in range of current month
     */
    @ColorInt
    public int getNotCurrentMonthDayNumberColor() {
        return adapter.getStyleInt(RangeCalendarPagerAdapter.STYLE_NOT_CURRENT_MONTH_DAY_NUMBER_COLOR);
    }

    /**
     * Sets color of day which is not in range of current month
     */
    public void setNotCurrentMonthDayNumberColor(@ColorInt int color) {
        adapter.setStyleInt(RangeCalendarPagerAdapter.STYLE_NOT_CURRENT_MONTH_DAY_NUMBER_COLOR, color);
    }

    /**
     * Returns color of disabled day which is out of minimum and maximum
     */
    @ColorInt
    public int getDisabledDayNumberColor() {
        return adapter.getStyleInt(RangeCalendarPagerAdapter.STYLE_DISABLED_DAY_NUMBER_COLOR);
    }

    /**
     * Sets color of disabled day which is out of minimum and maximum
     */
    public void setDisabledDayNumberColor(@ColorInt int color) {
        adapter.setStyleInt(RangeCalendarPagerAdapter.STYLE_DISABLED_DAY_NUMBER_COLOR, color);
    }

    /**
     * Returns color of disabled day which is out of minimum and maximum
     */
    @ColorInt
    public int getTodayColor() {
        return adapter.getStyleInt(RangeCalendarPagerAdapter.STYLE_TODAY_COLOR);
    }

    /**
     * Sets a color of today, by default it's primary color
     */
    public void setTodayColor(@ColorInt int color) {
        adapter.setStyleInt(RangeCalendarPagerAdapter.STYLE_TODAY_COLOR, color);
    }

    /**
     * Returns a color of weekday
     */
    @ColorInt
    public int geWeekdayColor() {
        return adapter.getStyleInt(RangeCalendarPagerAdapter.STYLE_WEEKDAY_COLOR);
    }

    /**
     * Sets a color of weekday
     */
    public void setWeekdayColor(@ColorInt int color) {
        adapter.setStyleInt(RangeCalendarPagerAdapter.STYLE_WEEKDAY_COLOR, color);
    }

    /**
     * Returns  a text size of weekday, in pixels
     */
    public float getWeekdayTextSize() {
        return adapter.getStyleFloat(RangeCalendarPagerAdapter.STYLE_WEEKDAY_TEXT_SIZE);
    }

    /**
     * Sets a text size of weekday, in pixels
     */
    public void setWeekdayTextSize(float size) {
        adapter.setStyleFloat(RangeCalendarPagerAdapter.STYLE_WEEKDAY_TEXT_SIZE, size);
    }

    /**
     * Returns hover color
     */
    @ColorInt
    public int getHoverColor() {
        return adapter.getStyleInt(RangeCalendarPagerAdapter.STYLE_HOVER_COLOR);
    }

    /**
     * Sets a hover color
     */
    public void setHoverColor(@ColorInt int color) {
        adapter.setStyleInt(RangeCalendarPagerAdapter.STYLE_HOVER_COLOR, color);
    }

    /**
     * Returns hover color on selection
     */
    @ColorInt
    public int getHoverOnSelectionColor() {
        return adapter.getStyleInt(RangeCalendarPagerAdapter.STYLE_HOVER_ON_SELECTION_COLOR);
    }

    /**
     * Sets a hover color that'll be shown when hovered cell in in selection, by default it's darker version of primary color
     */
    public void setHoverOnSelectionColor(@ColorInt int color) {
        adapter.setStyleInt(RangeCalendarPagerAdapter.STYLE_HOVER_ON_SELECTION_COLOR, color);
    }

    /**
     * Gets ratio of radius of round rectangles' in selection.
     * Radius will be computed as multiplication of cell size to this ratio.
     */
    public float getRoundRectRadiusRatio() {
        return adapter.getStyleFloat(RangeCalendarPagerAdapter.STYLE_RR_RADIUS_RATIO);
    }

    /**
     * Sets ratio of radius of round rectangles' in selection.
     * Set 0, to draw simple rectangles in selection.
     * Set 0.5f and more, to draw circles in cell selection
     */
    public void setRoundRectRadiusRatio(float ratio) {
        adapter.setStyleFloat(RangeCalendarPagerAdapter.STYLE_RR_RADIUS_RATIO, ratio);
    }

    /**
     * Gets size of cell, in pixels
     */
    public float getCellSize() {
        return adapter.getStyleFloat(RangeCalendarPagerAdapter.STYLE_CELL_SIZE);
    }

    /**
     * Sets size of cell, in pixels, should greater than 0
     */
    public void setCellSize(float size) {
        adapter.setStyleFloat(RangeCalendarPagerAdapter.STYLE_CELL_SIZE, size);
    }

    /**
     * Returns current weekday type, can be {@link WeekdayType#SHORT} or {@link WeekdayType#NARROW}.
     * By default, it's {@link WeekdayType#SHORT}
     */
    @WeekdayTypeInt
    public int getWeekdayType() {
        return adapter.getStyleInt(RangeCalendarPagerAdapter.STYLE_WEEKDAY_TYPE);
    }

    /**
     * Sets weekday type. Should be {@link WeekdayType#SHORT} or {@link WeekdayType#NARROW}. <br/>
     * If {@link WeekdayType#SHORT}, then weekdays will be: Mo, Tue, Wed, Thu, Fri. <br/> <br/>
     * If {@link WeekdayType#NARROW}, then weekdays will be: M, T, W, T, F.
     * Note that, narrow weekdays are not always one-letter. It's based on locale.
     *
     * @throws IllegalArgumentException if type is not one of {@link WeekdayType} constants
     */
    public void setWeekdayType(@WeekdayTypeInt int type) {
        adapter.setStyleInt(RangeCalendarPagerAdapter.STYLE_WEEKDAY_TYPE, type);
    }

    /**
     * Returns behaviour of what to do when user clicks on already selected cell.
     */
    @ClickOnCellSelectionBehaviourInt
    public int getClickOnCellSelectionBehaviour() {
        return adapter.getStyleInt(RangeCalendarPagerAdapter.STYLE_CLICK_ON_CELL_SELECTION_BEHAVIOR);
    }

    /**
     * Sets behaviour of what to do when user clicks on already selected cell.
     *
     * @param value {@link ClickOnCellSelectionBehavior#NONE} or {@link ClickOnCellSelectionBehavior#CLEAR}
     */
    public void setClickOnCellSelectionBehaviour(int value) {
        adapter.setStyleInt(RangeCalendarPagerAdapter.STYLE_CLICK_ON_CELL_SELECTION_BEHAVIOR, value);
    }

    /**
     * Moves (if it's possible) to previous month with animation.
     *
     * @see RangeCalendarView#moveToPreviousMonth(boolean)
     */
    public void moveToPreviousMonth() {
        moveToPreviousMonth(true);
    }

    /**
     * Moves (if it's possible) to previous month.
     *
     * @param withAnimation whether to do it with slide animation or not
     */
    public void moveToPreviousMonth(boolean withAnimation) {
        int pos = pager.getCurrentItem();
        if (pos > 0) {
            pager.setCurrentItem(pos - 1);
        }
    }

    /**
     * Moves (if it's possible) to next month with animation.
     *
     * @see RangeCalendarView#moveToNextMonth(boolean)
     */
    public void moveToNextMonth() {
        moveToNextMonth(true);
    }

    /**
     * Moves (if it's possible) to next month.
     *
     * @param withAnimation whether to do it with slide animation or not
     */
    public void moveToNextMonth(boolean withAnimation) {
        int pos = pager.getCurrentItem();
        int count = adapter.getItemCount();

        if (pos < count - 1) {
            pager.setCurrentItem(pos + 1);
        }
    }

    /**
     * Sets current year and month.
     * If new year and month differ from old ones, then it'll change with animation
     *
     * @param year  year, should be in range [1970; 32767]
     * @param month month, 1-based
     * @throws IllegalArgumentException if year and month are out of ranges
     */
    public void setYearAndMonth(int year, int month) {
        setYearAndMonth(year, month, true);
    }

    /**
     * Sets current year and month.
     *
     * @param year         year, should be in range [1970; 32767]
     * @param month        month, 1-based
     * @param smoothScroll whether to do it with slide animation or not
     * @throws IllegalArgumentException if year and month are out of ranges
     */
    public void setYearAndMonth(int year, int month, boolean smoothScroll) {
        ensureYearMonthValid(year, month);

        setYearAndMonthInternal(year, month, smoothScroll);
    }

    private void setYearAndMonthInternal(int year, int month, boolean smoothScroll) {
        currentCalendarYm = YearMonth.create(year, month);

        int position = adapter.getItemPositionForYearMonth(currentCalendarYm);

        pager.setCurrentItem(position, smoothScroll);
    }

    /**
     * Selects a date in epoch days with animation
     */
    public void selectDay(int epochDay) {
        selectDay(epochDay, true);
    }

    /**
     * Selects a date in epoch days
     *
     * @param withAnimation whether to do it with animation or not
     */
    public void selectDay(int epochDay, boolean withAnimation) {
        ensureEpochDayValid(epochDay, "epochDay");

        selectDayInternal(DateInt.fromEpochDay(epochDay), withAnimation);
    }

    /**
     * Selects a date with animation.
     * Can be used if API >= 26.
     */
    @RequiresApi(26)
    public void selectDay(@NotNull LocalDate date) {
        selectDay(date, true);
    }

    /**
     * Selects a date with animation.
     * Can be used if API >= 26.
     *
     * @param withAnimation whether to do it with animation or not
     */
    @RequiresApi(26)
    public void selectDay(@NotNull LocalDate date, boolean withAnimation) {
        selectDayInternal(DateInt.fromLocalDate(date), withAnimation);
    }

    /**
     * Selects a date in given by {@link Calendar} with animation
     */
    public void selectDay(@NotNull Calendar calendar) {
        selectDay(calendar, true);
    }

    /**
     * Selects a date in given by {@link Calendar}
     *
     * @param withAnimation whether to do it with animation or not
     */
    public void selectDay(@NotNull Calendar calendar, boolean withAnimation) {
        selectDayInternal(DateInt.fromCalendar(calendar), withAnimation);
    }

    private void selectDayInternal(int date, boolean withAnimation) {
        selectInternal(SelectionType.CELL, date, withAnimation);
    }

    /**
     * Selects a week with animation.
     *
     * @param year      year, should be in range [1970; 32767]
     * @param month     month, 1-based
     * @param weekIndex index of week, 0-based
     */
    public void selectWeek(int year, int month, int weekIndex) {
        selectWeek(year, month, weekIndex, true);
    }

    /**
     * Selects a week.
     *
     * @param year          year, should be in range [1970; 32767]
     * @param month         month, 1-based
     * @param weekIndex     index of week, 0-based
     * @param withAnimation whether to do it with animation or not
     */
    public void selectWeek(int year, int month, int weekIndex, boolean withAnimation) {
        selectInternal(
                SelectionType.WEEK,
                IntPair.create(YearMonth.create(year, month), weekIndex),
                withAnimation
        );
    }

    /**
     * Selects a month with animation
     *
     * @param year  year, should be in range [1970; 32767]
     * @param month month, 1-based
     */
    public void selectMonth(int year, int month) {
        selectMonth(year, month, true);
    }

    /**
     * Selects a month with animation
     *
     * @param year          year, should be in range [1970; 32767]
     * @param month         month, 1-based
     * @param withAnimation whether to do it with animation or not
     */
    public void selectMonth(int year, int month, boolean withAnimation) {
        selectInternal(SelectionType.MONTH, YearMonth.create(year, month), withAnimation);
    }

    private void selectInternal(int type, long data, boolean withAnimation) {
        adapter.select(type, data, withAnimation);
        pager.setCurrentItem(adapter.getItemPositionForSelection(type, data), withAnimation);
    }

    /**
     * Clears current calendar selection
     */
    public void clearSelection() {
        adapter.clearSelection();
    }

    private void updateMoveButtons() {
        int position = pager.getCurrentItem();
        int count = adapter.getItemCount();

        prevButton.setEnabled(position != 0);

        if(!hasSvClearButton || !isSelectionViewOnScreen) {
            nextOrClearButton.setEnabled(position != count - 1);
        } else {
            nextOrClearButton.setEnabled(true);
        }
    }

    private void setInfoViewYearMonth(int ym) {
        if (infoViewYm != ym) {
            infoViewYm = ym;

            int year = YearMonth.getYear(ym);
            int month = YearMonth.getMonth(ym);

            cachedDate.setTime(DateInt.toEpochDay(year, month, 1) * DateInt.MILLIS_IN_DAY);

            String text;

            if (Build.VERSION.SDK_INT >= 24) {
                text = dateFormatter24.format(cachedDate);
            } else {
                text = dateFormatter.format(cachedDate);
            }

            infoView.setText(text);
        }
    }

    private static void setButtonAlphaIfEnabled(@NotNull ImageButton button, int alpha) {
        if(button.isEnabled()) {
            Drawable drawable = button.getDrawable();

            if(drawable != null) {
                drawable.setAlpha(alpha);
            }
        }
    }

    private static void ensureEpochDayValid(long epochDay, String argName) {
        if (epochDay < MIN_DATE_EPOCH || epochDay > MAX_DATE_EPOCH) {
            throw new IllegalArgumentException(argName);
        }
    }

    private static void ensureYearMonthValid(int year, int month) {
        if (year < 1970 || year > DateInt.MAX_YEAR) {
            throw new IllegalArgumentException("Invalid year (" + year + ")");
        }

        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid month (" + month + ")");
        }
    }

    private static void ensureDateValid(int year, int month, int day) {
        ensureYearMonthValid(year, month);

        if (day < 1 || day > TimeUtils.getDaysInMonth(year, month)) {
            throw new IllegalArgumentException("Invalid day (" + day + ")");
        }
    }

    private static long calendarToEpochDay(@NotNull Calendar calendar) {
        return DateInt.toEpochDay(DateInt.fromCalendar(calendar));
    }
}
