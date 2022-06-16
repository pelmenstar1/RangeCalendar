package io.github.pelmenstar1.rangecalendar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.TypedArray
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.icu.text.DisplayContext
import android.os.Build
import android.os.Parcelable
import android.text.format.DateFormat
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.*
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.ResourcesCompat
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import io.github.pelmenstar1.rangecalendar.decoration.CellDecor
import io.github.pelmenstar1.rangecalendar.decoration.DecorAnimationFractionInterpolator
import io.github.pelmenstar1.rangecalendar.decoration.DecorLayoutOptions
import io.github.pelmenstar1.rangecalendar.selection.WideSelectionData
import io.github.pelmenstar1.rangecalendar.utils.getLazyValue
import io.github.pelmenstar1.rangecalendar.utils.getLocaleCompat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*
import kotlin.math.abs

/**
 * A calendar view which supports range selection and decorations.
 * Internally it uses [ViewPager2] view, so such term as "page" is used here to denote "page" in which calendar view with grid is stored.
 * - Minimum and maximum dates can be set through [minDate] and [maxDate].
 * - [onSelectionListener] can be used to respond to selection
 * - [selectDay], [selectWeek], [selectMonth] can be used to programmatically set a selection with animation or not.
 * - Most appropriate colors are automatically extracted from attributes, but you can still change it.
 * - [allowedSelectionTypes] method can be used to (dis-)allow types of selection
 * - [onPageChangeListener] can be used to respond to calendar page changes.
 * [selectedCalendarYear], [selectedCalendarMonth] can be used to get year & month of selected page respectively.
 * - [setYearAndMonth] can be used to change selected calendar. [moveToPreviousMonth], [moveToNextMonth] also can be used.
 * - [addDecoration], [addDecorations], [insertDecorations], [removeDecoration], [removeDecorationRange], [removeAllDecorationsFromCell]
 * can be used to manipulate cell decorations. See [CellDecor].
 */
class RangeCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {
    /**
     * Represents a "gate" which determines whether to allow selection or not.
     * Note
     */
    interface SelectionGate {
        /**
         * Determines whether cell selection is valid.
         *
         * @param year  year of selected date
         * @param month month of selected date, 1-based
         * @param dayOfMonth   day of month of selected date, 1-based
         *
         * @return true, if selection is valid; false, if selection request should be declined for some reasons.
         */
        fun cell(year: Int, month: Int, dayOfMonth: Int): Boolean

        /**
         * Determines whether week selection is valid.
         *
         * @param weekIndex  index of the week, 0-based
         * @param startYear  year of selection's start
         * @param startMonth month of selection's start, 1-based
         * @param startDay   day of month of selection's start, 1-based
         * @param endYear    year of selection's end
         * @param endMonth   month of selection's end, 1-based
         * @param endDay     day of month of selection's end, 1-based
         *
         * @return true, if selection is valid; false, if selection request should be declined for some reasons.
         */
        fun week(
            weekIndex: Int,
            startYear: Int, startMonth: Int, startDay: Int,
            endYear: Int, endMonth: Int, endDay: Int
        ): Boolean

        /**
         * Determines whether month selection is valid.
         *
         * @param year  year of selection
         * @param month month of selection, 1-based
         *
         * @return true, if selection is valid; false, if selection request should be declined for some reasons.
         */
        fun month(year: Int, month: Int): Boolean

        /**
         * Determines whether custom-range selection is valid.
         *
         * @param startYear year of start of the range
         * @param startMonth month of start of the range, 1-based
         * @param startDay day of start of the range, 1-based
         *
         * @param endYear year of end of the range
         * @param endMonth year of end of the range, 1-based
         * @param endDay year of end of the range, 1-based
         *
         * @return true, if selection is valid; false, if selection request should be declined for some reasons.
         */
        fun customRange(
            startYear: Int, startMonth: Int, startDay: Int,
            endYear: Int, endMonth: Int, endDay: Int
        ): Boolean
    }

    /**
     * Fires appropriate method when user selects day, week or month.
     */
    interface OnSelectionListener {
        /**
         * Fires when selection is cleared.
         * It can happen when selection view is on screen and clear selection button is clicked
         */
        fun onSelectionCleared()

        /**
         * Fires when user selects a day.
         *
         * @param year  year of selected date
         * @param month month of selected date, 1-based
         * @param day   day of month of selected date, 1-based
         */
        fun onDaySelected(year: Int, month: Int, day: Int)

        /**
         * Fires when user selects a week.
         *
         * @param weekIndex  index of the week, 0-based
         * @param startYear  year of selection's start
         * @param startMonth month of selection's start, 1-based
         * @param startDay   day of month of selection's start, 1-based
         * @param endYear    year of selection's end
         * @param endMonth   month of selection's end, 1-based
         * @param endDay     day of month of selection's end, 1-based
         */
        fun onWeekSelected(
            weekIndex: Int,
            startYear: Int, startMonth: Int, startDay: Int,
            endYear: Int, endMonth: Int, endDay: Int
        )

        /**
         * Fires when user selects a month.
         *
         * @param year  year of selection
         * @param month month of selection, 1-based
         */
        fun onMonthSelected(year: Int, month: Int)

        /**
         * Fires when user selects a custom range.
         *
         * @param startYear year of start of the range
         * @param startMonth month of start of the range, 1-based
         * @param startDay day of start of the range, 1-based
         *
         * @param endYear year of end of the range
         * @param endMonth year of end of the range, 1-based
         * @param endDay year of end of the range, 1-based
         */
        fun onCustomRangeSelected(
            startYear: Int, startMonth: Int, startDay: Int,
            endYear: Int, endMonth: Int, endDay: Int
        )
    }

    /**
     * A functional interface which responds when calendar page is changed
     */
    fun interface OnCalendarChangeListener {
        /**
         * This method will be invoked when calendar page is changed
         *
         * @param year year of selected page
         * @param month month of selected page, 1-based
         */
        fun onPageChanged(year: Int, month: Int)
    }

    inner class AllowedSelectionTypes {
        /**
         * Sets whether selecting a cell is enabled or not.
         *
         * @return current instance
         */
        fun cell(state: Boolean): AllowedSelectionTypes {
            return setFlag(SelectionType.CELL, state)
        }

        /**
         * Sets whether selecting a week is enabled or not.
         *
         * @return current instance
         */
        fun week(state: Boolean): AllowedSelectionTypes {
            return setFlag(SelectionType.WEEK, state)
        }

        /**
         * Sets whether selecting a month is enabled or not.
         *
         * @return current instance
         */
        fun month(state: Boolean): AllowedSelectionTypes {
            return setFlag(SelectionType.MONTH, state)
        }

        /**
         * Sets whether selecting a custom range is enabled or not.
         *
         * @return current instance
         */
        fun customRange(state: Boolean): AllowedSelectionTypes {
            return setFlag(SelectionType.CUSTOM, state)
        }

        private fun setFlag(type: SelectionType, state: Boolean): AllowedSelectionTypes {
            // Set the bit at position specified by type if 'state' is true, otherwise unset the bit.
            val mask = selectionTypeMask(type)
            val flags = allowedSelectionFlags

            val newFlags = if (state) {
                flags or mask
            } else {
                flags and mask.inv()
            }

            setAllowedSelectionFlags(newFlags)

            return this
        }
    }

    private class ExtractAttributesScope(
        private val calendarView: RangeCalendarView,
        private val attrs: TypedArray
    ) {
        fun color(@StyleableRes index: Int, styleType: Int) {
            if (attrs.hasValue(index)) {
                val color = attrs.getColor(index, 0)

                calendarView.adapter.setStyleColor(styleType, color, notify = false)
            }

            extract(index, styleType) { getColor(index, 0) }
        }

        fun dimension(@StyleableRes index: Int, styleType: Int) {
            extract(index, styleType) { getDimension(index, 0f).toBits() }
        }

        fun int(@StyleableRes index: Int, styleType: Int) {
            extract(index, styleType) { getInt(index, 0) }
        }

        fun boolean(@StyleableRes index: Int, styleType: Int) {
            extract(index, styleType) { if (getBoolean(index, false)) 1 else 0 }
        }

        private inline fun extract(
            @StyleableRes index: Int,
            styleType: Int,
            toInt: TypedArray.() -> Int
        ) {
            if (attrs.hasValue(index)) {
                calendarView.adapter.setStyleInt(styleType, toInt(attrs), notify = false)
            }
        }
    }

    private val pager: ViewPager2
    private val prevButton: ImageButton
    private val nextOrClearButton: ImageButton
    private val infoView: TextView

    private var _timeZone: TimeZone

    private var _minDate = PackedDate.MIN_DATE
    private var _maxDate = PackedDate.MAX_DATE

    private var _minDateEpoch = PackedDate.MIN_DATE_EPOCH
    private var _maxDateEpoch = PackedDate.MAX_DATE_EPOCH

    private var infoViewYm = YearMonth(0)
    private var isReceiverRegistered = false
    private var currentCalendarYm = YearMonth(0)
    private val adapter: RangeCalendarPagerAdapter

    private var allowedSelectionTypesObj: AllowedSelectionTypes? = null

    // All the types are allowed
    private var allowedSelectionFlags = 0x1E

    private val buttonSize: Int
    private val hPadding: Int
    private val topContainerMarginBottom: Int
    private var dateFormatter: SimpleDateFormat? = null

    // formatter for API >= 24
    @RequiresApi(24)
    private var dateFormatter24: android.icu.text.SimpleDateFormat? = null

    private val cachedDate = Date()

    private var _selectionView: View? = null
    private var svAnimator: ValueAnimator? = null

    // valid while svAnimator is running
    private var isSvTransitionForward = false

    var selectionViewTransitionInterpolator: TimeInterpolator = LINEAR_INTERPOLATOR

    private var svTransitionDuration = SV_TRANSITION_DURATION
    private var _hasSvClearButton = true
    private var isSelectionViewOnScreen = false
    private var svLayoutParams = CalendarSelectionViewLayoutParams.DEFAULT

    private val prevIcon: MoveButtonDrawable
    private val nextIcon: MoveButtonDrawable

    private var isFirstDaySunday = false

    private val nextMonthDescription: String
    private val clearSelectionDescription: String

    private val layoutRect = Rect()
    private val layoutOutRect = Rect()

    private val onDateChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_DATE_CHANGED -> {
                    refreshToday()
                }
                Intent.ACTION_TIMEZONE_CHANGED -> {
                    if (observeTimeZoneChanges) {
                        _timeZone = if (Build.VERSION.SDK_INT >= 30) {
                            TimeZone.getTimeZone(intent.getStringExtra(Intent.EXTRA_TIMEZONE))
                        } else {
                            TimeZone.getDefault()
                        }

                        refreshToday()
                    }
                }
            }
        }
    }

    init {
        val res = context.resources

        buttonSize = res.getDimensionPixelSize(R.dimen.rangeCalendar_actionButtonSize)
        topContainerMarginBottom =
            res.getDimensionPixelOffset(R.dimen.rangeCalendar_topContainerMarginBottom)
        initLocaleDependentValues()

        val selectableBg = getSelectableItemBackground(context)
        val cr = CalendarResources(context)

        hPadding = cr.hPadding.toInt()
        val today = PackedDate.today()

        adapter = RangeCalendarPagerAdapter(cr, isFirstDaySunday)
        adapter.setToday(today)
        adapter.setSelectionGate(object : SelectionGate {
            override fun cell(year: Int, month: Int, dayOfMonth: Int) = internalGate(SelectionType.CELL) {
                it.cell(year, month, dayOfMonth)
            }

            override fun week(
                weekIndex: Int,
                startYear: Int,
                startMonth: Int,
                startDay: Int,
                endYear: Int,
                endMonth: Int,
                endDay: Int
            ) = internalGate(SelectionType.WEEK) {
                it.week(weekIndex, startYear, startMonth, startDay, endYear, endMonth, endDay)
            }

            override fun month(year: Int, month: Int) = internalGate(SelectionType.MONTH) {
                it.month(year, month)
            }

            override fun customRange(
                startYear: Int,
                startMonth: Int,
                startDay: Int,
                endYear: Int,
                endMonth: Int,
                endDay: Int
            ) = internalGate(SelectionType.CUSTOM) {
                it.customRange(startYear, startMonth, startDay, endYear, endMonth, endDay)
            }

            private inline fun internalGate(
                type: SelectionType,
                method: (SelectionGate) -> Boolean
            ): Boolean {
                return if(isSelectionTypeAllowed(type)) {
                    val gate = selectionGate

                    if(gate != null) {
                        method(gate)
                    } else {
                        true
                    }
                } else {
                    false
                }
            }
        })
        adapter.setOnSelectionListener(object : OnSelectionListener {
            override fun onSelectionCleared() {
                if (_selectionView != null) {
                    startSelectionViewTransition(false)
                }

                onSelectionListener?.onSelectionCleared()
            }

            override fun onDaySelected(year: Int, month: Int, day: Int) {
                onSelectedHandler {
                    onDaySelected(year, month, day)
                }
            }

            override fun onWeekSelected(
                weekIndex: Int,
                startYear: Int,
                startMonth: Int,
                startDay: Int,
                endYear: Int,
                endMonth: Int,
                endDay: Int
            ) {
                onSelectedHandler {
                    onWeekSelected(
                        weekIndex,
                        startYear, startMonth, startDay,
                        endYear, endMonth, endDay
                    )
                }
            }

            override fun onMonthSelected(year: Int, month: Int) {
                onSelectedHandler {
                    onMonthSelected(year, month)
                }
            }

            override fun onCustomRangeSelected(
                startYear: Int, startMonth: Int, startDay: Int,
                endYear: Int, endMonth: Int, endDay: Int
            ) {
                onSelectedHandler {
                    onCustomRangeSelected(
                        startYear, startMonth, startDay,
                        endYear, endMonth, endDay
                    )
                }
            }

            private inline fun onSelectedHandler(method: OnSelectionListener.() -> Unit) {
                startSelectionViewTransition(true)
                onSelectionListener?.method()
            }
        })

        val stateChangeDuration = SV_TRANSITION_DURATION / 2

        prevIcon = MoveButtonDrawable(
            context, cr.colorControlNormal,
            MoveButtonDrawable.DIRECTION_LEFT, MoveButtonDrawable.ANIM_TYPE_VOID_TO_ARROW
        ).apply {
            setAnimationFraction(1f)
            setStateChangeDuration(stateChangeDuration)
        }

        nextIcon = MoveButtonDrawable(
            context, cr.colorControlNormal,
            MoveButtonDrawable.DIRECTION_RIGHT, MoveButtonDrawable.ANIM_TYPE_ARROW_TO_CLOSE
        ).apply {
            setStateChangeDuration(stateChangeDuration)
        }

        nextMonthDescription = res.getString(R.string.nextMonthDescription)
        clearSelectionDescription = res.getString(R.string.clearSelectionDescription)

        prevButton = AppCompatImageButton(context).apply {
            setImageDrawable(prevIcon)
            setOnClickListener { moveToPreviousMonth() }
            contentDescription = res.getString(R.string.previousMonthDescription)
        }

        nextOrClearButton = AppCompatImageButton(context).apply {
            setImageDrawable(nextIcon)
            contentDescription = nextMonthDescription
            setOnClickListener {
                if (isSelectionViewOnScreen && _hasSvClearButton) {
                    clearSelection()
                } else {
                    moveToNextMonth()
                }
            }
        }

        if (selectableBg != null) {
            prevButton.setBackground(selectableBg)
            nextOrClearButton.setBackground(selectableBg.constantState!!.newDrawable(res))
        }

        infoView = AppCompatTextView(context).apply {
            setTextColor(cr.textColor)
            setOnClickListener {
                selectMonth(currentCalendarYm, true)
            }
        }

        pager = ViewPager2(context).apply {
            adapter = this@RangeCalendarView.adapter
            offscreenPageLimit = 1
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isSaveFromParentEnabled = false

            registerOnPageChangeCallback(object : OnPageChangeCallback() {
                override fun onPageScrolled(
                    position: Int,
                    positionOffset: Float,
                    positionOffsetPixels: Int
                ) {
                    val alpha = (510f * abs(0.5f - positionOffset)).toInt()
                    setButtonAlphaIfEnabled(prevButton, alpha)

                    if (!_hasSvClearButton || !isSelectionViewOnScreen) {
                        setButtonAlphaIfEnabled(nextOrClearButton, alpha)
                    }
                }

                override fun onPageSelected(position: Int) {
                    val ym = this@RangeCalendarView.adapter.getYearMonthForCalendar(position)

                    currentCalendarYm = ym
                    setInfoViewYearMonth(ym)

                    setButtonAlphaIfEnabled(prevButton, 255)
                    setButtonAlphaIfEnabled(nextOrClearButton, 255)

                    updateMoveButtons()

                    onPageChangeListener?.onPageChanged(ym.year, ym.month)
                }

                override fun onPageScrollStateChanged(state: Int) {
                    val adapter = this@RangeCalendarView.adapter

                    if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                        adapter.clearHoverAt(adapter.getItemPositionForYearMonth(currentCalendarYm))
                    }
                }
            })
        }

        addView(pager)
        addView(prevButton)
        addView(nextOrClearButton)
        addView(infoView)

        setYearAndMonthInternal(YearMonth.forDate(today), false)

        _timeZone = TimeZone.getDefault()

        attrs?.let { initFromAttributes(context, it, defStyleAttr) }
    }

    private fun initFromAttributes(
        context: Context,
        attrs: AttributeSet,
        defStyleAttr: Int
    ) {
        val a = context.obtainStyledAttributes(
            attrs, R.styleable.RangeCalendarView, defStyleAttr, 0
        )
        try {
            extractAttributes(a) {
                if (a.hasValue(R.styleable.RangeCalendarView_rangeCalendar_selectionColor)) {
                    val color =
                        a.getColor(R.styleable.RangeCalendarView_rangeCalendar_selectionColor, 0)

                    setSelectionColor(color)
                }

                color(
                    R.styleable.RangeCalendarView_rangeCalendar_inMonthDayNumberColor,
                    RangeCalendarGridView.COLOR_STYLE_IN_MONTH
                )
                color(
                    R.styleable.RangeCalendarView_rangeCalendar_outMonthDayNumberColor,
                    RangeCalendarGridView.COLOR_STYLE_OUT_MONTH
                )
                color(
                    R.styleable.RangeCalendarView_rangeCalendar_disabledDayNumberColor,
                    RangeCalendarGridView.COLOR_STYLE_DISABLED
                )
                color(
                    R.styleable.RangeCalendarView_rangeCalendar_todayColor,
                    RangeCalendarGridView.COLOR_STYLE_TODAY
                )
                color(
                    R.styleable.RangeCalendarView_rangeCalendar_weekdayColor,
                    RangeCalendarGridView.COLOR_STYLE_WEEKDAY
                )
                color(
                    R.styleable.RangeCalendarView_rangeCalendar_hoverColor,
                    RangeCalendarGridView.COLOR_STYLE_HOVER
                )
                color(
                    R.styleable.RangeCalendarView_rangeCalendar_hoverOnSelectionColor,
                    RangeCalendarGridView.COLOR_STYLE_HOVER_ON_SELECTION
                )
                dimension(
                    R.styleable.RangeCalendarView_rangeCalendar_dayNumberTextSize,
                    RangeCalendarPagerAdapter.STYLE_DAY_NUMBER_TEXT_SIZE
                )
                dimension(
                    R.styleable.RangeCalendarView_rangeCalendar_weekdayTextSize,
                    RangeCalendarPagerAdapter.STYLE_WEEKDAY_TEXT_SIZE
                )
                dimension(
                    R.styleable.RangeCalendarView_rangeCalendar_cellSize,
                    RangeCalendarPagerAdapter.STYLE_CELL_SIZE
                )
                int(
                    R.styleable.RangeCalendarView_rangeCalendar_weekdayType,
                    RangeCalendarPagerAdapter.STYLE_WEEKDAY_TYPE
                )
                int(
                    R.styleable.RangeCalendarView_rangeCalendar_clickOnCellSelectionBehavior,
                    RangeCalendarPagerAdapter.STYLE_CLICK_ON_CELL_SELECTION_BEHAVIOR
                )
                dimension(
                    R.styleable.RangeCalendarView_rangeCalendar_cellRoundRadius,
                    RangeCalendarPagerAdapter.STYLE_CELL_RR_RADIUS
                )
                boolean(
                    R.styleable.RangeCalendarView_rangeCalendar_vibrateOnSelectingCustomRange,
                    RangeCalendarPagerAdapter.STYLE_VIBRATE_ON_SELECTING_CUSTOM_RANGE
                )
                int(
                    R.styleable.RangeCalendarView_rangeCalendar_selectionFillGradientBoundsType,
                    RangeCalendarPagerAdapter.STYLE_SELECTION_FILL_GRADIENT_BOUNDS_TYPE
                )
            }
        } finally {
            a.recycle()
        }
    }

    private inline fun extractAttributes(
        attrs: TypedArray,
        block: ExtractAttributesScope.() -> Unit
    ) {
        block(ExtractAttributesScope(this, attrs))
    }

    private fun refreshToday() {
        adapter.setToday(PackedDate.today(_timeZone))
    }

    private fun initLocaleDependentValues() {
        val locale = context.getLocaleCompat()

        // Find best format and create formatter
        val dateFormat = if (Build.VERSION.SDK_INT >= 18) {
            DateFormat.getBestDateTimePattern(locale, DATE_FORMAT)
        } else {
            DATE_FORMAT
        }

        if (Build.VERSION.SDK_INT >= 24) {
            dateFormatter24 = android.icu.text.SimpleDateFormat(dateFormat, locale).apply {
                setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE)
            }
        } else {
            dateFormatter = SimpleDateFormat(dateFormat, locale)
        }

        isFirstDaySunday = Calendar
            .getInstance(locale)
            .firstDayOfWeek == Calendar.SUNDAY
    }

    override fun onSaveInstanceState(): Parcelable {
        return SavedState(super.onSaveInstanceState()!!).apply {
            selectionType = adapter.selectionType
            selectionData = adapter.selectionData
            selectionYm = adapter.selectionYm
            ym = currentCalendarYm
        }
    }

    override fun onRestoreInstanceState(state: Parcelable) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)

            val ym = state.ym

            // There's no need to 'smooth-scroll' it, because if there's smooth-scroll,
            // then it will look ugly.
            // For example, it would be strange if the calendar started to scroll from min page to
            // previous selected page when you rotate the screen.
            setYearAndMonthInternal(ym, smoothScroll = false)

            // Restore selection if there's one.
            if (state.selectionType != SelectionType.NONE) {
                adapter.select(
                    state.selectionYm, state.selectionType, state.selectionData
                )
            }
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        registerReceiver()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        unregisterReceiver()
    }

    private fun registerReceiver() {
        if (!isReceiverRegistered) {
            isReceiverRegistered = true

            context.registerReceiver(onDateChangedReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_DATE_CHANGED)
            })
        }
    }

    private fun unregisterReceiver() {
        if (isReceiverRegistered) {
            isReceiverRegistered = false

            context.unregisterReceiver(onDateChangedReceiver)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        pager.measure(widthMeasureSpec, heightMeasureSpec)

        val pagerWidth = pager.measuredWidth

        val buttonSpec = MeasureSpec.makeMeasureSpec(buttonSize, MeasureSpec.EXACTLY)
        val infoWidthSpec = MeasureSpec.makeMeasureSpec(pager.measuredWidth, MeasureSpec.AT_MOST)
        val infoHeightSpec = MeasureSpec.makeMeasureSpec(buttonSize, MeasureSpec.AT_MOST)

        if (_selectionView != null) {
            var maxWidth = pagerWidth - 2 * hPadding - buttonSize
            if (!_hasSvClearButton) {
                maxWidth -= buttonSize
            }

            measureChild(
                _selectionView,
                MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(buttonSize, MeasureSpec.AT_MOST)
            )
        }

        infoView.measure(infoWidthSpec, infoHeightSpec)
        prevButton.measure(buttonSpec, buttonSpec)
        nextOrClearButton.measure(buttonSpec, buttonSpec)

        setMeasuredDimension(
            pager.measuredWidthAndState,
            resolveSize(
                pager.measuredHeight + buttonSize + topContainerMarginBottom,
                heightMeasureSpec
            )
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l

        val prevRight = hPadding + buttonSize
        val nextLeft = width - prevRight
        val sv = _selectionView

        if (sv != null) {
            val gravity = svLayoutParams.gravity
            val svWidth = sv.measuredWidth
            val svHeight = sv.measuredHeight

            // If selection view is wanted to be in center on x-axis,
            // let it be actual center of the whole view.
            if (gravity == Gravity.CENTER || gravity == Gravity.CENTER_HORIZONTAL) {
                layoutRect.set(hPadding, 0, width - hPadding, buttonSize)
            } else {
                layoutRect.set(
                    if (_hasSvClearButton) hPadding else prevRight,
                    0,
                    nextLeft,
                    buttonSize
                )
            }

            if (Build.VERSION.SDK_INT >= 17) {
                Gravity.apply(
                    svLayoutParams.gravity,
                    svWidth, svHeight,
                    layoutRect, layoutOutRect,
                    layoutDirection
                )
            } else {
                Gravity.apply(
                    svLayoutParams.gravity,
                    svWidth, svHeight,
                    layoutRect, layoutOutRect
                )
            }

            sv.layout(
                layoutOutRect.left, layoutOutRect.top,
                layoutOutRect.right, layoutOutRect.bottom
            )
        }

        val infoWidth = infoView.measuredWidth
        val infoHeight = infoView.measuredHeight
        val infoLeft = (width - infoWidth) / 2
        val infoTop = (buttonSize - infoHeight) / 2

        prevButton.layout(hPadding, 0, prevRight, buttonSize)
        nextOrClearButton.layout(nextLeft, 0, nextLeft + buttonSize, buttonSize)
        infoView.layout(infoLeft, infoTop, infoLeft + infoWidth, infoTop + infoHeight)

        val pagerTop = buttonSize + topContainerMarginBottom
        pager.layout(0, pagerTop, pager.measuredWidth, pagerTop + pager.measuredHeight)
    }

    private fun getSelectableItemBackground(context: Context): Drawable? {
        val theme = context.theme

        val value = TypedValue()
        theme.resolveAttribute(R.attr.selectableItemBackgroundBorderless, value, true)

        return ResourcesCompat.getDrawable(context.resources, value.resourceId, theme)
    }

    private fun setSelectionViewOnScreen(state: Boolean) {
        isSelectionViewOnScreen = state
        val sv = _selectionView!!

        if (state) {
            sv.visibility = VISIBLE
            infoView.visibility = INVISIBLE
            if (_hasSvClearButton) {
                nextOrClearButton.contentDescription = clearSelectionDescription
            }
        } else {
            sv.visibility = INVISIBLE
            infoView.visibility = VISIBLE
            if (_hasSvClearButton) {
                nextOrClearButton.contentDescription = nextMonthDescription
            }
        }
        updateMoveButtons()
    }

    private fun startSelectionViewTransition(forward: Boolean) {
        if (_selectionView == null) {
            return
        }

        var animator = svAnimator

        // Don't continue if we want to show selection view and it's already shown and vise versa,
        // but continue if animation is currently running and direction of current animation is not equals to new one.
        if (animator != null &&
            (!animator.isRunning || isSvTransitionForward == forward) &&
            forward == isSelectionViewOnScreen
        ) {
            return
        }

        if (animator == null) {
            animator = AnimationHelper.createFractionAnimator { fraction ->
                onSVTransitionTick(fraction)
            }

            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    if (!isSvTransitionForward) {
                        prevButton.visibility = VISIBLE
                    }
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (isSvTransitionForward) {
                        prevButton.visibility = GONE
                    }
                }
            })
        }
        isSvTransitionForward = forward

        var startPlaytime: Long = 0
        if (animator.isRunning) {
            startPlaytime = animator.currentPlayTime
            animator.end()
        }

        animator.interpolator = selectionViewTransitionInterpolator
        animator.duration = svTransitionDuration

        // ValueAnimator.setCurrentFraction() could be used, but it's available only from API >= 22,
        animator.currentPlayTime = startPlaytime

        if (forward) {
            animator.start()
        } else {
            animator.reverse()
        }
    }

    private fun onSVTransitionTick(fraction: Float) {
        val sv = _selectionView!!

        if (_hasSvClearButton) {
            prevIcon.setAnimationFraction(1f - fraction)
            nextIcon.setAnimationFraction(fraction)
        }

        if (fraction < 0.5f) {
            val f = fraction * -2f
            infoView.translationY = infoView.bottom * f

            if (isSelectionViewOnScreen) {
                setSelectionViewOnScreen(false)
            }
        } else {
            val f = 2f * fraction - 2f
            sv.translationY = f * sv.bottom

            if (!isSelectionViewOnScreen) {
                setSelectionViewOnScreen(true)
            }
        }
    }

    /**
     * Gets or sets the selection view.
     *
     * When user selects a day or range, this view replace "previous" button and current year & month text view.
     * "Next" button will become "Close" button which clears selection.
     * Set to null not to show any view on selection.
     * <br></br>
     * Action button or another useful content can be shown instead of year & month text view.
     * This might be helpful for rational use of space.
     */
    var selectionView: View?
        get() = _selectionView
        set(value) {
            val oldSelectionView = _selectionView
            _selectionView = value

            if (oldSelectionView != null) {
                // On 0 fraction, selection view will disappear
                onSVTransitionTick(0f)

                // selection view is always in end
                removeViewAt(childCount - 1)
            }

            if (value != null) {
                value.visibility = INVISIBLE
                addView(value)
            }
            requestLayout()
        }

    /**
     * Gets or sets duration (in milliseconds) of transition
     * from "previous" button and year & month text view to selection view and vise verse.
     */
    var selectionViewTransitionDuration: Long
        get() = svTransitionDuration
        set(duration) {
            svTransitionDuration = duration
            val stateChangeDuration = duration / 2

            prevIcon.setStateChangeDuration(stateChangeDuration)
            nextIcon.setStateChangeDuration(stateChangeDuration)
        }

    /**
     * Gets or sets layout params for selection view
     */
    var selectionViewLayoutParams: CalendarSelectionViewLayoutParams
        get() = svLayoutParams
        set(layoutParams) {
            svLayoutParams = layoutParams
            requestLayout()
        }

    /**
     * Gets or sets whether selection view on selection should have clear selection button.
     *
     * If true, then when selection happens, "previous" button and year & month view will disappear,
     * "next" button will become clear selection button and selection view will appear.
     * **Note that, user won't be able to use move buttons, but be able to slide calendars. **
     * <br></br> <br></br>
     * If false, then when selection happens, year & month view will disappear, no move buttons will disappear,
     * selection will be placed where year & month view. User will still be able to use move buttons
     */
    var hasSelectionViewClearButton: Boolean
        get() = _hasSvClearButton
        set(value) {
            if (_hasSvClearButton == value) {
                return
            }

            _hasSvClearButton = value
            requestLayout()
        }

    /**
     * Gets or sets minimum date.
     *
     * @throws IllegalArgumentException if date is before 1 January 0000 or after 30 December 65535,
     * or if minimum date is greater than maximum.
     */
    var minDate: LocalDate
        get() = _minDate.toLocalDate()
        set(value) {
            _minDateEpoch = value.toEpochDay()

            requireValidEpochDayOnLocalDateTransform(_minDateEpoch)
            require(_minDateEpoch <= _maxDateEpoch) { "Minimum date is greater than maximum one" }

            _minDate = PackedDate.fromLocalDate(value)

            onMinMaxChanged()
        }

    /**
     * Gets or sets minimum date.
     *
     * @throws IllegalArgumentException if date is before 1 January 0000 or after 30 December 65535,
     * or if minimum date is greater than maximum.
     */
    var maxDate: LocalDate
        get() = _maxDate.toLocalDate()
        set(value) {
            _maxDateEpoch = value.toEpochDay()

            requireValidEpochDayOnLocalDateTransform(_maxDateEpoch)
            require(_minDateEpoch <= _maxDateEpoch) { "Minimum date is greater than maximum one" }

            _minDate = PackedDate.fromLocalDate(value)

            onMinMaxChanged()
        }

    private fun onMinMaxChanged() {
        adapter.setRange(_minDate, _minDateEpoch, _maxDate, _maxDateEpoch)

        setYearAndMonthInternal(currentCalendarYm, false)
    }

    /**
     * Gets or sets whether system time zone changes should be observed.
     *
     * If `true`, then when system time zone is changed, [timeZone] is updated to the new one
     * and "today" cell is updated as well.
     * If 'false', then system time zone changes are unobserved.
     */
    var observeTimeZoneChanges: Boolean = true

    /**
     * Gets or sets calendar's time zone.
     * By default, it's default system time zone ([TimeZone.getDefault]).
     *
     * Calendar's time zone affects to "today" cell recognition.
     * When new time zone is set, "today" cell is updated.
     *
     * **NOTE:** when new value is set, [observeTimeZoneChanges] is set to `false`.
     * This is due to the assumption that when custom time zone (not default system one) is set,
     * it's expected that the calendar's time zone wouldn't be overwritten when system one is changed.
     * In spite of the assumption, it's not forbidden to set [observeTimeZoneChanges] to `true`.
     */
    var timeZone: TimeZone
        get() = _timeZone
        set(value) {
            if (!_timeZone.hasSameRules(value)) {
                _timeZone = value
                observeTimeZoneChanges = false

                refreshToday()
            }
        }

    /**
     * Gets or sets a listener which responds to selection changes
     */
    var onSelectionListener: OnSelectionListener? = null

    /**
     * Gets or sets a selection "gate" which determines whether to allow selection or not.
     */
    var selectionGate: SelectionGate? = null

    /**
     * Gets or sets a listener which responds when calendar page is changed (by user or from code)
     */
    var onPageChangeListener: OnCalendarChangeListener? = null

    /**
     * Returns year of selected calendar page. Note, this is not year of selection.
     */
    val selectedCalendarYear: Int
        get() = currentCalendarYm.year

    /**
     * Returns month of selected calendar page, 1-based. Note, this is not month of selection.
     */
    val selectedCalendarMonth: Int
        get() = currentCalendarYm.month

    /**
     * Gets or sets a background fill of selection,
     * by default it's solid color which color is extracted from [R.attr.colorPrimary]
     */
    var selectionFill: Fill
        get() = adapter.getStyleObject(RangeCalendarPagerAdapter.STYLE_SELECTION_FILL)
        set(value) {
            adapter.setStyleObject(RangeCalendarPagerAdapter.STYLE_SELECTION_FILL, value)
        }

    /**
     * Sets background color of selection. A convenience method for [selectionFill].
     * It's the same as `calendar.selectionFill = Fill.solid(color)`
     *
     * @param value background color of selection.
     */
    fun setSelectionColor(@ColorInt value: Int) {
        selectionFill = Fill.solid(value)
    }

    /**
     * Sets background color of selection using color long. A convenience method for [selectionFill].
     * It's the same as `calendar.selectionFill = Fill.solid(color)`.
     *
     * Supported when API level is 26 and higher.
     *
     * @param value background color of selection.
     */
    @RequiresApi(26)
    fun setSelectionColor(@ColorLong value: Long) {
        selectionFill = Fill.solid(value)
    }

    /**
     * Gets or sets the way of determining bounds of selection. It only matters when selection fill is gradient-like.
     */
    var selectionFillGradientBoundsType: SelectionFillGradientBoundsType
        get() = SelectionFillGradientBoundsType.ofOrdinal(
            adapter.getStyleInt(
                RangeCalendarPagerAdapter.STYLE_SELECTION_FILL_GRADIENT_BOUNDS_TYPE,
            )
        )
        set(value) {
            adapter.setStyleInt(
                RangeCalendarPagerAdapter.STYLE_SELECTION_FILL_GRADIENT_BOUNDS_TYPE,
                value.ordinal
            )
        }

    /**
     * Gets or sets a text size of day number, in pixels
     */
    var dayNumberTextSize: Float
        get() = adapter.getStyleFloat(RangeCalendarPagerAdapter.STYLE_DAY_NUMBER_TEXT_SIZE)
        set(size) {
            adapter.setStyleFloat(RangeCalendarPagerAdapter.STYLE_DAY_NUMBER_TEXT_SIZE, size)
        }

    /**
     * Gets or sets color of day which is in range of selected calendar's month.
     *
     * If the color is initially set by [inMonthDayNumberColorLong] and the color space isn't sRGB,
     * calling the getter will cause additional allocations and loss of data.
     */
    @get:ColorInt
    var inMonthDayNumberColor: Int
        get() = adapter.getStyleColorInt(RangeCalendarGridView.COLOR_STYLE_IN_MONTH)
        set(@ColorInt color) {
            adapter.setStyleColor(RangeCalendarGridView.COLOR_STYLE_IN_MONTH, color)
        }

    /**
     * Gets or sets color of day is in range of selected calendar's month using color long.
     *
     * Supported when API level is 26 and higher.
     */
    @get:ColorLong
    @get:RequiresApi(26)
    @set:RequiresApi(26)
    var inMonthDayNumberColorLong: Long
        get() = adapter.getStyleColorLong(RangeCalendarGridView.COLOR_STYLE_IN_MONTH)
        set(@ColorLong color) {
            adapter.setStyleColor(RangeCalendarGridView.COLOR_STYLE_IN_MONTH, color)
        }

    /**
     * Gets or sets color of day which is out of selected calendar's month.
     *
     * If the color is initially set by [outMonthDayNumberColorLong] and the color space isn't sRGB,
     * calling the getter will cause additional allocations and loss of data.
     */
    @get:ColorInt
    var outMonthDayNumberColor: Int
        get() = adapter.getStyleColorInt(RangeCalendarGridView.COLOR_STYLE_OUT_MONTH)
        set(@ColorInt color) {
            adapter.setStyleColor(RangeCalendarGridView.COLOR_STYLE_OUT_MONTH, color)
        }

    /**
     * Gets or sets color of day is out of selected calendar's month using color long.
     *
     * Supported when API level is 26 and higher.
     */
    @get:ColorLong
    @get:RequiresApi(26)
    @set:RequiresApi(26)
    var outMonthDayNumberColorLong: Long
        get() = adapter.getStyleColorLong(RangeCalendarGridView.COLOR_STYLE_OUT_MONTH)
        set(@ColorLong color) {
            adapter.setStyleColor(RangeCalendarGridView.COLOR_STYLE_OUT_MONTH, color)
        }

    /**
     * Gets or sets color of disabled day which is out of enabled range created by [minDate] and [maxDate].
     *
     * If the color is initially set by [disabledDayNumberColorLong] and the color space isn't sRGB,
     * calling the getter will cause additional allocations and loss of data.
     */
    @get:ColorInt
    var disabledDayNumberColor: Int
        get() = adapter.getStyleColorInt(RangeCalendarGridView.COLOR_STYLE_DISABLED)
        set(@ColorInt color) {
            adapter.setStyleColor(RangeCalendarGridView.COLOR_STYLE_DISABLED, color)
        }

    /**
     * Gets or sets color of disabled day which is out of enabled range created by [minDate] and [maxDate].
     *
     * Supported when API level is 26 and higher.
     */
    @get:ColorLong
    @get:RequiresApi(26)
    @set:RequiresApi(26)
    var disabledDayNumberColorLong: Long
        get() = adapter.getStyleColorLong(RangeCalendarGridView.COLOR_STYLE_DISABLED)
        set(@ColorLong color) {
            adapter.setStyleColor(RangeCalendarGridView.COLOR_STYLE_DISABLED, color)
        }

    /**
     * Gets or sets a text color of cell which represents today date,
     * by default it's color extracted from [R.attr.colorPrimary].
     *
     * If the color is initially set by [todayColorLong] and the color space isn't sRGB,
     * calling the getter will cause additional allocations and loss of data.
     */
    @get:ColorInt
    var todayColor: Int
        get() = adapter.getStyleColorInt(RangeCalendarGridView.COLOR_STYLE_TODAY)
        set(@ColorInt color) {
            adapter.setStyleColor(RangeCalendarGridView.COLOR_STYLE_TODAY, color)
        }

    /**
     * Gets or sets a text color of cell which represents today date,
     * by default it's color extracted from [R.attr.colorPrimary].
     *
     * Supported when API level is 26 and higher.
     */
    @get:ColorLong
    @get:RequiresApi(26)
    @set:RequiresApi(26)
    var todayColorLong: Long
        get() = adapter.getStyleColorLong(RangeCalendarGridView.COLOR_STYLE_TODAY)
        set(@ColorLong color) {
            adapter.setStyleColor(RangeCalendarGridView.COLOR_STYLE_TODAY, color)
        }

    /**
     * Gets or sets a text color of weekday,
     * by default it's a color extracted from [R.style.TextAppearance_AppCompat].
     *
     * If the color is initially set by [weekdayColorLong] and the color space isn't sRGB,
     * calling the getter will cause additional allocations and loss of data.
     */
    @get:ColorInt
    var weekdayColor: Int
        get() = adapter.getStyleColorInt(RangeCalendarGridView.COLOR_STYLE_WEEKDAY)
        set(@ColorInt color) {
            adapter.setStyleColor(RangeCalendarGridView.COLOR_STYLE_WEEKDAY, color)
        }

    /**
     * Gets or sets a text color of weekday,
     * by default it's a color extracted from [R.style.TextAppearance_AppCompat].
     *
     * Supported when API level is 26 and higher.
     */
    @get:ColorLong
    @get:RequiresApi(26)
    @set:RequiresApi(26)
    var weekdayColorLong: Long
        get() = adapter.getStyleColorLong(RangeCalendarGridView.COLOR_STYLE_WEEKDAY)
        set(@ColorLong color) {
            adapter.setStyleColor(RangeCalendarGridView.COLOR_STYLE_WEEKDAY, color)
        }

    /**
     * Gets or sets a text size of weekday, in pixels
     */
    var weekdayTextSize: Float
        get() = adapter.getStyleFloat(RangeCalendarPagerAdapter.STYLE_WEEKDAY_TEXT_SIZE)
        set(size) {
            adapter.setStyleFloat(RangeCalendarPagerAdapter.STYLE_WEEKDAY_TEXT_SIZE, size)
        }

    /**
     * Gets or sets a background color of cell which appears when user hovers under a cell
     * (when a pointer is registered to be down).
     *
     * If the color is initially set by [hoverColorLong] and the color space isn't sRGB,
     * calling the getter will cause additional allocations and loss of data.
     *
     * @see [hoverOnSelectionColor]
     */
    @get:ColorInt
    var hoverColor: Int
        get() = adapter.getStyleColorInt(RangeCalendarGridView.COLOR_STYLE_HOVER)
        set(color) {
            adapter.setStyleColor(RangeCalendarGridView.COLOR_STYLE_HOVER, color)
        }

    /**
     *  Gets or sets a background color of cell which appears when user hovers under a cell
     * (when a pointer is registered to be down).
     *
     * Supported when API level is 26 and higher.
     */
    @get:ColorLong
    @get:RequiresApi(26)
    @set:RequiresApi(26)
    var hoverColorLong: Long
        get() = adapter.getStyleColorLong(RangeCalendarGridView.COLOR_STYLE_HOVER)
        set(@ColorLong color) {
            adapter.setStyleColor(RangeCalendarGridView.COLOR_STYLE_HOVER, color)
        }

    /**
     * Gets or sets a background color of cell which appears when user hovers under a cell and selection contains the cell.
     * As background of the calendar and selection color are different, common [hoverColor] wouldn't look fine on selection.
     *
     * If the color is initially set by [hoverOnSelectionColorLong] and the color space isn't sRGB,
     * calling the getter will cause additional allocations and loss of data.
     */
    @get:ColorInt
    var hoverOnSelectionColor: Int
        get() = adapter.getStyleColorInt(RangeCalendarGridView.COLOR_STYLE_HOVER_ON_SELECTION)
        set(color) {
            adapter.setStyleColor(RangeCalendarGridView.COLOR_STYLE_HOVER_ON_SELECTION, color)
        }

    /**
     *  Gets or sets a background color of cell which appears when user hovers under a cell
     * (when a pointer is registered to be down).
     *
     * Supported when API level is 26 and higher.
     */
    @get:ColorLong
    @get:RequiresApi(26)
    @set:RequiresApi(26)
    var hoverOnSelectionColorLong: Long
        get() = adapter.getStyleColorLong(RangeCalendarGridView.COLOR_STYLE_HOVER_ON_SELECTION)
        set(@ColorLong color) {
            adapter.setStyleColor(RangeCalendarGridView.COLOR_STYLE_HOVER_ON_SELECTION, color)
        }

    /**
     * Gets or sets round radius of cell shape.
     *
     * Set to 0f if cell shape should be rectangle.
     * Set to [Float.POSITIVE_INFINITY] if cell shape is wanted to be circle regardless the size of it.
     */
    var cellRoundRadius: Float
        get() = adapter.getStyleFloat(RangeCalendarPagerAdapter.STYLE_CELL_RR_RADIUS)
        set(ratio) {
            adapter.setStyleFloat(RangeCalendarPagerAdapter.STYLE_CELL_RR_RADIUS, ratio)
        }

    /**
     * Gets or sets size of cell, in pixels, should be greater than 0
     */
    var cellSize: Float
        get() = adapter.getStyleFloat(RangeCalendarPagerAdapter.STYLE_CELL_SIZE)
        set(size) {
            adapter.setStyleFloat(RangeCalendarPagerAdapter.STYLE_CELL_SIZE, size)
        }

    /**
     * Gets or sets weekday type. Should be [WeekdayType.SHORT] or [WeekdayType.NARROW].
     * If [WeekdayType.SHORT], then weekdays will be: Mo, Tue, Wed, Thu, Fri.
     * If [WeekdayType.NARROW], then weekdays will be: M, T, W, T, F.
     * Note:
     * - Narrow weekdays is dependent on user locale and are not always one-letter.
     * - **If API level is less than 24, [WeekdayType.NARROW] won't work.**
     *
     * @throws IllegalArgumentException if type is not one of [WeekdayType] constants
     */
    var weekdayType: WeekdayType
        get() = adapter.getStyleEnum(
            RangeCalendarPagerAdapter.STYLE_WEEKDAY_TYPE,
            WeekdayType::ofOrdinal
        )
        set(type) {
            adapter.setStyleEnum(RangeCalendarPagerAdapter.STYLE_WEEKDAY_TYPE, type)
        }


    /**
     * Gets or sets behavior of what to do when user clicks on already selected cell.
     */
    var clickOnCellSelectionBehavior: ClickOnCellSelectionBehavior
        get() {
            return adapter.getStyleEnum(
                RangeCalendarPagerAdapter.STYLE_CLICK_ON_CELL_SELECTION_BEHAVIOR,
                ClickOnCellSelectionBehavior::ofOrdinal
            )
        }
        set(value) {
            adapter.setStyleEnum(
                RangeCalendarPagerAdapter.STYLE_CLICK_ON_CELL_SELECTION_BEHAVIOR,
                value
            )
        }

    /**
     * Gets or sets duration (in ms) of common calendar animations like selecting cell, week, month, clearing selection etc.
     * In other words, it's duration of all animations except hover animation.
     *
     * @throws IllegalArgumentException if duration is negative
     */
    var commonAnimationDuration: Int
        get() = adapter.getStyleInt(RangeCalendarPagerAdapter.STYLE_COMMON_ANIMATION_DURATION)
        set(duration) {
            require(duration >= 0) { "duration" }
            adapter.setStyleInt(RangeCalendarPagerAdapter.STYLE_COMMON_ANIMATION_DURATION, duration)
        }

    /**
     * Gets or sets time interpolator of common calendar animations.
     * By default, the interpolator is linear.
     */
    var commonAnimationInterpolator: TimeInterpolator
        get() = adapter.getStyleObject(RangeCalendarPagerAdapter.STYLE_COMMON_ANIMATION_INTERPOLATOR)
        set(value) {
            adapter.setStyleObject(
                RangeCalendarPagerAdapter.STYLE_COMMON_ANIMATION_INTERPOLATOR,
                value
            )
        }

    /**
     * Gets or sets duration (in ms) of hover animation.
     *
     * @throws IllegalArgumentException if duration is negative
     */
    var hoverAnimationDuration: Int
        get() = adapter.getStyleInt(RangeCalendarPagerAdapter.STYLE_HOVER_ANIMATION_DURATION)
        set(duration) {
            require(duration >= 0) { "duration" }

            adapter.setStyleInt(RangeCalendarPagerAdapter.STYLE_HOVER_ANIMATION_DURATION, duration)
        }


    /**
     * Gets or sets time interpolator of hover animation.
     */
    var hoverAnimationInterpolator: TimeInterpolator
        get() = adapter.getStyleObject(RangeCalendarPagerAdapter.STYLE_HOVER_ANIMATION_INTERPOLATOR)
        set(value) {
            adapter.setStyleObject(
                RangeCalendarPagerAdapter.STYLE_HOVER_ANIMATION_INTERPOLATOR,
                value
            )
        }

    /**
     * Gets or sets whether device should vibrate when user starts to select custom range.
     */
    var vibrateOnSelectingCustomRange: Boolean
        get() = adapter.getStyleBool(RangeCalendarPagerAdapter.STYLE_VIBRATE_ON_SELECTING_CUSTOM_RANGE)
        set(state) {
            adapter.setStyleBool(
                RangeCalendarPagerAdapter.STYLE_VIBRATE_ON_SELECTING_CUSTOM_RANGE,
                state
            )
        }

    /**
     * Changes calendar page to the previous one. If it's not possible, nothing will happen.
     *
     * @param withAnimation whether to do it with slide animation or not
     */
    @JvmOverloads
    fun moveToPreviousMonth(withAnimation: Boolean = true) {
        val pos = pager.currentItem

        // Check if there's a previous page
        if (pos > 0) {
            pager.setCurrentItem(pos - 1, withAnimation)
        }
    }

    /**
     * Changes calendar page to the previous one. If it's not possible, nothing will happen.
     *
     * @param withAnimation whether to do it with slide animation or not
     */
    @JvmOverloads
    fun moveToNextMonth(withAnimation: Boolean = true) {
        val pos = pager.currentItem
        val count = adapter.itemCount

        // Check if there's next page
        if (pos < count - 1) {
            pager.setCurrentItem(pos + 1, withAnimation)
        }
    }

    /**
     * Selects page with specified year & month.
     *
     * @param year  year, should be in range `[1970; 32767]`
     * @param month month, 1-based
     * @param smoothScroll whether to do it with slide animation or not
     * @throws IllegalArgumentException if year and month are out of their valid ranges
     */
    @JvmOverloads
    fun setYearAndMonth(year: Int, month: Int, smoothScroll: Boolean = true) {
        require(year in 0..PackedDate.MAX_YEAR) { "Invalid year ($year)" }
        require(month in 1..12) { "Invalid month ($month)" }

        setYearAndMonthInternal(YearMonth(year, month), smoothScroll)
    }

    private fun setYearAndMonthInternal(ym: YearMonth, smoothScroll: Boolean) {
        currentCalendarYm = ym

        val position = adapter.getItemPositionForYearMonth(ym)

        pager.setCurrentItem(position, smoothScroll)
        updateMoveButtons()
    }

    /**
     * Selects a date if cell selection is allowed.
     *
     * @param withAnimation whether to do it with animation or not
     */
    @JvmOverloads
    fun selectDay(date: LocalDate, withAnimation: Boolean = true) {
        selectInternal(
            SelectionType.CELL,
            WideSelectionData.cell(PackedDate.fromLocalDate(date)),
            withAnimation
        )
    }

    /**
     * Selects a week if week selection is allowed.
     *
     * @param year          year, should be in range `[1970; 32767]`
     * @param month         month, 1-based
     * @param weekIndex     index of week, 0-based
     * @param withAnimation whether to do it with animation or not
     */
    @JvmOverloads
    fun selectWeek(year: Int, month: Int, weekIndex: Int, withAnimation: Boolean = true) {
        selectInternal(
            SelectionType.WEEK,
            WideSelectionData.week(YearMonth(year, month), weekIndex),
            withAnimation
        )
    }

    /**
     * Selects a month if month selection is allowed.
     *
     * @param year          year, should be in range `[1970; 32767]`
     * @param month         month, 1-based
     * @param withAnimation whether to do it with animation or not
     */
    @JvmOverloads
    fun selectMonth(year: Int, month: Int, withAnimation: Boolean = true) {
        selectMonth(YearMonth(year, month), withAnimation)
    }

    private fun selectMonth(ym: YearMonth, withAnimation: Boolean) {
        selectInternal(
            SelectionType.MONTH,
            WideSelectionData.month(ym),
            withAnimation
        )
    }

    /**
     * Selects a custom range if custom range selection is allowed.
     *
     * @param startDate start of the range, inclusive
     * @param endDate end of the range, inclusive
     * @param withAnimation whether to do it with animation of not
     */
    fun selectCustom(startDate: LocalDate, endDate: LocalDate, withAnimation: Boolean = true) {
        selectInternal(
            SelectionType.CUSTOM,
            WideSelectionData.customRange(PackedDateRange.fromLocalDates(startDate, endDate)),
            withAnimation
        )
    }

    private fun selectInternal(type: SelectionType, data: WideSelectionData, withAnimation: Boolean) {
        if (isSelectionTypeAllowed(type)) {
            val ym = adapter.getYearMonthForSelection(type, data)

            val actuallySelected = adapter.select(type, data, withAnimation)
            if(actuallySelected) {
                val position = adapter.getItemPositionForYearMonth(ym)

                pager.setCurrentItem(position, withAnimation)
            }
        }
    }

    /**
     * Clears a selection.
     */
    fun clearSelection() {
        adapter.clearSelection()
    }

    /**
     * Returns instance of object which encapsulates changing availability of selection types.
     * Note that each method is mutating and if you call, for example, `cell(false)` and cell is currently selected,
     * then selection will be cleared.
     * So, if a cell is currently selected, after chain `allowedSelectionTypes().cell(false).cell(true)`, cell selection will be cleared although it's enabled.
     *
     */
    fun allowedSelectionTypes(): AllowedSelectionTypes {
        return getLazyValue(
            allowedSelectionTypesObj,
            { AllowedSelectionTypes() },
            { allowedSelectionTypesObj = it }
        )
    }

    private fun setAllowedSelectionFlags(flags: Int) {
        allowedSelectionFlags = flags
        val selectionType = adapter.selectionType

        // Clear selection if current selection type become disallowed.
        if (!isSelectionTypeAllowed(selectionType)) {
            clearSelection()
        }
    }

    /**
     * Determines whether specified selection type is allowed. [SelectionType.NONE] is always disallowed.
     *
     * @param type selection type to test.
     *
     * @see allowedSelectionTypes
     */
    fun isSelectionTypeAllowed(type: SelectionType): Boolean {
        return (allowedSelectionFlags and selectionTypeMask(type)) != 0
    }

    private fun selectionTypeMask(type: SelectionType): Int {
        return 1 shl type.ordinal
    }

    /**
     * Adds decoration to the cell. Note that, only one type of decoration can exist in one cell.
     *
     * @param decor decoration to be added
     * @param date specifies the cell to which the decoration is added.
     * @param withAnimation whether to animate the change or not.
     */
    @JvmOverloads
    fun addDecoration(
        decor: CellDecor,
        date: LocalDate,
        withAnimation: Boolean = true
    ) {
        adapter.addDecoration(decor, PackedDate.fromLocalDate(date), withAnimation)
    }

    /**
     * Adds multiple decorations to the cell. Note that, only one type of decoration can exist in one cell.
     *
     * @param decors array of decorations to be added
     * @param date specifies the cell to which the decoration is added.
     * @param fractionInterpolator instance of [DecorAnimationFractionInterpolator] that interpolates one fraction to set of elements.
     * May be null, if no animation is desired.
     */
    @JvmOverloads
    fun <T : CellDecor> addDecorations(
        decors: Array<out T>,
        date: LocalDate,
        fractionInterpolator: DecorAnimationFractionInterpolator? = DecorAnimationFractionInterpolator.Simultaneous
    ) {
        adapter.addDecorations(decors, PackedDate.fromLocalDate(date), fractionInterpolator)
    }

    /**
     * Inserts multiple decorations to the cell. Note that, only one type of decoration can exist in one cell.
     *
     * @param indexInCell decoration index in specified cell at which insert decorations
     * @param decors array of decorations to be added
     * @param date specifies the cell to which the decoration is added.
     * @param fractionInterpolator instance of [DecorAnimationFractionInterpolator] that interpolates one fraction to set of elements.
     * May be null, if no animation is desired.
     */
    @JvmOverloads
    fun <T : CellDecor> insertDecorations(
        indexInCell: Int,
        decors: Array<out T>,
        date: LocalDate,
        fractionInterpolator: DecorAnimationFractionInterpolator? = DecorAnimationFractionInterpolator.Simultaneous
    ) {
        adapter.insertDecorations(
            indexInCell,
            decors,
            PackedDate.fromLocalDate(date),
            fractionInterpolator
        )
    }

    /**
     * Removes specified decoration from the cell it's assigned.
     *
     * @param decor decoration to be removed
     * @param withAnimation whether to animate the change
     */
    @JvmOverloads
    fun removeDecoration(decor: CellDecor, withAnimation: Boolean = true) {
        adapter.removeDecoration(decor, withAnimation)
    }

    /**
     * Removes range of decorations from the cell.
     *
     * @param start start index of the range.
     * @param endInclusive end (inclusive) index of the range.
     * @param date specifies the cell.
     * @param fractionInterpolator instance of [DecorAnimationFractionInterpolator] that interpolates one fraction to set of elements.
     * May be null, if no animation is desired.
     */
    @JvmOverloads
    fun removeDecorationRange(
        start: Int,
        endInclusive: Int,
        date: LocalDate,
        fractionInterpolator: DecorAnimationFractionInterpolator? = DecorAnimationFractionInterpolator.Simultaneous
    ) {
        adapter.removeDecorationRange(
            start,
            endInclusive,
            PackedDate.fromLocalDate(date),
            fractionInterpolator
        )
    }

    /**
     * Removes all decorations from the cell.
     *
     * @param date specifies the cell.
     * @param fractionInterpolator instance of [DecorAnimationFractionInterpolator] that interpolates one fraction to set of elements.
     * May be null, if no animation is desired.
     */
    @JvmOverloads
    fun removeAllDecorationsFromCell(
        date: LocalDate,
        fractionInterpolator: DecorAnimationFractionInterpolator? = DecorAnimationFractionInterpolator.Simultaneous
    ) {
        adapter.removeAllDecorations(PackedDate.fromLocalDate(date), fractionInterpolator)
    }

    @JvmOverloads
    fun setDecorationLayoutOptions(
        date: LocalDate,
        options: DecorLayoutOptions,
        withAnimation: Boolean = true
    ) {
        adapter.setDecorationLayoutOptions(PackedDate.fromLocalDate(date), options, withAnimation)
    }

    fun setDecorationDefaultLayoutOptions(options: DecorLayoutOptions?) {
        adapter.setStyleObject(
            RangeCalendarPagerAdapter.STYLE_DECOR_DEFAULT_LAYOUT_OPTIONS,
            options
        )
    }

    private fun updateMoveButtons() {
        val position = pager.currentItem
        val count = adapter.itemCount

        prevButton.isEnabled = position != 0

        if (!_hasSvClearButton || !isSelectionViewOnScreen) {
            nextOrClearButton.isEnabled = position != count - 1
        } else {
            nextOrClearButton.isEnabled = true
        }
    }

    private fun setInfoViewYearMonth(ym: YearMonth) {
        if (infoViewYm != ym) {
            infoViewYm = ym

            cachedDate.time = PackedDate(ym, 1).toEpochDay() * PackedDate.MILLIS_IN_DAY

            infoView.text = if (Build.VERSION.SDK_INT >= 24) {
                dateFormatter24!!.format(cachedDate)
            } else {
                dateFormatter!!.format(cachedDate)
            }
        }
    }

    companion object {
        private val TAG = RangeCalendarView::class.java.simpleName

        private const val DATE_FORMAT = "MMMM y"
        private const val SV_TRANSITION_DURATION: Long = 300

        private fun setButtonAlphaIfEnabled(button: ImageButton, alpha: Int) {
            if (button.isEnabled) {
                button.drawable?.alpha = alpha
            }
        }

        private fun requireValidEpochDayOnLocalDateTransform(epochDay: Long) {
            require(PackedDate.isValidEpochDay(epochDay)) { "Date is out of valid range" }
        }
    }
}