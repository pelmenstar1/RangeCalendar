package io.github.pelmenstar1.rangecalendar

import android.animation.Animator
import kotlin.jvm.JvmOverloads
import android.view.ViewGroup
import androidx.viewpager2.widget.ViewPager2
import android.widget.ImageButton
import android.widget.TextView
import android.animation.ValueAnimator
import android.animation.TimeInterpolator
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.res.TypedArray
import android.os.Build
import android.os.Parcelable
import android.view.Gravity
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import android.content.IntentFilter
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.graphics.Rect
import android.icu.text.DisplayContext
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.annotation.StyleableRes
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import io.github.pelmenstar1.rangecalendar.decoration.*
import io.github.pelmenstar1.rangecalendar.utils.getLocaleCompat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*
import kotlin.math.abs

/**
 * The special calendar where user can select day, week or month.
 * Minimum and maximum dates can be set by [RangeCalendarView.minDateEpoch] and [RangeCalendarView.maxDateEpoch].
 * There also another convenience versions of these methods.
 * To listen when day, week or month is selected, use [RangeCalendarView.onSelectionListener].
 * To select programmatically, use [RangeCalendarView.selectDay], [RangeCalendarView.selectWeek], [RangeCalendarView.selectMonth].
 * <br></br>
 * Most appropriate colors are automatically extracted from attributes, but you can still change it.
 */
class RangeCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {
    /**
     * Fires appropriate method when user selects day, week or month.
     * <br></br>
     * Because of problems with dates in Java, there is no methods like: <br></br>
     * `void onDateSelected(LocalDate)` <br></br>
     * because you would need to implement multiple methods (like `onDateSelected(LocalDate)`, `onDateSelected(Calendar)`, `onDateSelected(int, int, int)`)
     * instead of one.
     * So you need to convert raw year, month and day yourself.
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
         * @return if for some reasons selected day is not valid, returns false, otherwise true
         */
        fun onDaySelected(year: Int, month: Int, day: Int): Boolean

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
         * @return if for some reasons selected week is not valid, returns false, otherwise true
         */
        fun onWeekSelected(
            weekIndex: Int,
            startYear: Int, startMonth: Int, startDay: Int,
            endYear: Int, endMonth: Int, endDay: Int
        ): Boolean

        /**
         * Fires when user selects a month.
         *
         * @param year  year of selection
         * @param month month of selection, 1-based
         *
         * @return if for some reasons selected range is not valid, returns false, otherwise true
         */
        fun onMonthSelected(year: Int, month: Int): Boolean

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
         *
         * @return if for some reasons selected range is not valid, returns false, otherwise true
         */
        fun onCustomRangeSelected(
            startYear: Int, startMonth: Int, startDay: Int,
            endYear: Int, endMonth: Int, endDay: Int
        ): Boolean
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

        private fun setFlag(shift: Int, state: Boolean): AllowedSelectionTypes {
            // Set the bit at 'shift' position if 'state' is true, otherwise unset the bit.

            val bit = 1 shl shift
            val flags = allowedSelectionFlags

            val newFlags = if (state) {
                flags or bit
            } else {
                flags and bit.inv()
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
            extract(index, styleType, ATTR_COLOR)
        }

        fun dimension(@StyleableRes index: Int, styleType: Int) {
            extract(index, styleType, ATTR_DIMEN)
        }

        fun int(@StyleableRes index: Int, styleType: Int) {
            extract(index, styleType, ATTR_INT)
        }

        fun fraction(@StyleableRes index: Int, styleType: Int) {
            extract(index, styleType, ATTR_FRACTION)
        }

        fun boolean(@StyleableRes index: Int, styleType: Int) {
            extract(index, styleType, ATTR_BOOL)
        }

        private fun extract(@StyleableRes index: Int, styleType: Int, attrType: Int) {
            if (attrs.hasValue(index)) {
                var value = 0
                when (attrType) {
                    ATTR_COLOR -> {
                        value = attrs.getColor(index, 0)
                    }
                    ATTR_DIMEN -> {
                        value = attrs.getDimension(index, 0f).toBits()
                    }
                    ATTR_INT -> {
                        value = attrs.getInteger(index, 0)
                    }
                    ATTR_FRACTION -> {
                        value = attrs.getFraction(index, 1, 1, 0f).toBits()
                    }
                    ATTR_BOOL -> {
                        value = if (attrs.getBoolean(index, false)) 1 else 0
                    }
                }

                calendarView.adapter.setStyleInt(styleType, value, false)
            }
        }

        companion object {
            private const val ATTR_COLOR = 0
            private const val ATTR_DIMEN = 1
            private const val ATTR_INT = 2
            private const val ATTR_FRACTION = 3
            private const val ATTR_BOOL = 4
        }
    }

    private val pager: ViewPager2
    private val prevButton: ImageButton
    private val nextOrClearButton: ImageButton
    private val infoView: TextView

    private var _minDate = MIN_DATE
    private var _maxDate = MAX_DATE

    private var _minDateEpoch = MIN_DATE_EPOCH
    private var _maxDateEpoch = MAX_DATE_EPOCH

    private var infoViewYm = YearMonth(0)
    private var isReceiverRegistered = false
    private var currentCalendarYm = YearMonth(0)
    private val adapter: RangeCalendarPagerAdapter

    private var allowedSelectionTypesObj: AllowedSelectionTypes? = null
    private var allowedSelectionFlags = SELECTION_DAY_FLAG or
            SELECTION_WEEK_FLAG or
            SELECTION_MONTH_FLAG or
            SELECTION_CUSTOM_FLAG

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
            val action = intent.action
            if (action == Intent.ACTION_DATE_CHANGED) {
                refreshToday()
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
        adapter.setOnSelectionListener(object : OnSelectionListener {
            override fun onSelectionCleared() {
                if (_selectionView != null) {
                    startSelectionViewTransition(false)
                }

                onSelectionListener?.onSelectionCleared()
            }

            override fun onDaySelected(year: Int, month: Int, day: Int): Boolean {
                return onSelectedHandler(SELECTION_DAY_FLAG) {
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
            ): Boolean {
                return onSelectedHandler(SELECTION_WEEK_FLAG) {
                    onWeekSelected(
                        weekIndex,
                        startYear, startMonth, startDay,
                        endYear, endMonth, endDay
                    )
                }
            }

            override fun onMonthSelected(year: Int, month: Int): Boolean {
                return onSelectedHandler(SELECTION_MONTH_FLAG) {
                    onMonthSelected(year, month)
                }
            }

            override fun onCustomRangeSelected(
                startYear: Int, startMonth: Int, startDay: Int,
                endYear: Int, endMonth: Int, endDay: Int
            ): Boolean {
                return onSelectedHandler(SELECTION_CUSTOM_FLAG) {
                    onCustomRangeSelected(
                        startYear, startMonth, startDay,
                        endYear, endMonth, endDay
                    )
                }
            }

            private inline fun onSelectedHandler(
                flag: Int,
                method: OnSelectionListener.() -> Boolean
            ): Boolean {
                if ((allowedSelectionFlags and flag) == 0) {
                    return false
                }

                val listener = onSelectionListener
                if (listener != null) {
                    val allowed = listener.method()

                    if (allowed) {
                        startSelectionViewTransition(true)
                    }

                    return allowed
                } else {
                    startSelectionViewTransition(true)
                }

                return true
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
                adapter.select(
                    SelectionType.MONTH,
                    currentCalendarYm.totalMonths.toLong(),
                    true
                )
            }
        }

        pager = ViewPager2(context).apply {
            adapter = this@RangeCalendarView.adapter
            offscreenPageLimit = 3
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
                color(
                    R.styleable.RangeCalendarView_rangeCalendar_selectionColor,
                    RangeCalendarPagerAdapter.STYLE_SELECTION_COLOR
                )
                color(
                    R.styleable.RangeCalendarView_rangeCalendar_inMonthDayNumberColor,
                    RangeCalendarPagerAdapter.STYLE_IN_MONTH_DAY_NUMBER_COLOR
                )
                color(
                    R.styleable.RangeCalendarView_rangeCalendar_outMonthDayNumberColor,
                    RangeCalendarPagerAdapter.STYLE_OUT_MONTH_DAY_NUMBER_COLOR
                )
                color(
                    R.styleable.RangeCalendarView_rangeCalendar_disabledDayNumberColor,
                    RangeCalendarPagerAdapter.STYLE_DISABLED_DAY_NUMBER_COLOR
                )
                color(
                    R.styleable.RangeCalendarView_rangeCalendar_todayColor,
                    RangeCalendarPagerAdapter.STYLE_TODAY_COLOR
                )
                color(
                    R.styleable.RangeCalendarView_rangeCalendar_weekdayColor,
                    RangeCalendarPagerAdapter.STYLE_WEEKDAY_COLOR
                )
                color(
                    R.styleable.RangeCalendarView_rangeCalendar_hoverColor,
                    RangeCalendarPagerAdapter.STYLE_HOVER_COLOR
                )
                color(
                    R.styleable.RangeCalendarView_rangeCalendar_hoverOnSelectionColor,
                    RangeCalendarPagerAdapter.STYLE_HOVER_ON_SELECTION_COLOR
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
                fraction(
                    R.styleable.RangeCalendarView_rangeCalendar_roundRectRadiusRatio,
                    RangeCalendarPagerAdapter.STYLE_RR_RADIUS_RATIO
                )
                boolean(
                    R.styleable.RangeCalendarView_rangeCalendar_vibrateOnSelectingCustomRange,
                    RangeCalendarPagerAdapter.STYLE_VIBRATE_ON_SELECTING_CUSTOM_RANGE
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
        adapter.setToday(PackedDate.today())
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

        if (!isReceiverRegistered) {
            isReceiverRegistered = true

            registerReceiver()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        if (isReceiverRegistered) {
            isReceiverRegistered = false

            unregisterReceiver()
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
        if (Build.VERSION.SDK_INT >= 21) {
            val theme = context.theme

            val value = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)
            return ResourcesCompat.getDrawable(context.resources, value.resourceId, theme)
        }

        return null
    }

    private fun registerReceiver() {
        context.registerReceiver(onDateChangedReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
        })
    }

    private fun unregisterReceiver() {
        context.unregisterReceiver(onDateChangedReceiver)
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
     * Action button or another useful content can be shown.
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
     * Gets or sets [CalendarSelectionViewLayoutParams] for selection view
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
     * Gets or sets minimum date in epoch days.
     *
     * @throws IllegalArgumentException if value is less than [RangeCalendarView.MIN_DATE_EPOCH] or
     * greater than [RangeCalendarView.MAX_DATE_EPOCH], or if minimum date is greater than maximum.
     */
    var minDateEpoch: Long
        get() = _minDateEpoch
        set(value) {
            ensureEpochDayValid(value, "value")
            require(value <= _maxDateEpoch) { "min > max" }

            _minDate = PackedDate.fromEpochDay(value)
            _minDateEpoch = value

            onMinMaxChanged()
        }

    /**
     * Gets or sets minimum date.
     * Can be used if API >= 26.
     *
     * @throws IllegalArgumentException if date is less than 1 January 1970 or greater than 30 December 32767, or if minimum date is greater than maximum.
     * @see RangeCalendarView.minDateEpoch
     */
    @get:RequiresApi(26)
    @set:RequiresApi(26)
    var minDate: LocalDate
        get() = _minDate.toLocalDate()
        set(value) {
            minDateEpoch = value.toEpochDay()
        }

    /**
     * Sets minimum date to given calendar.
     *
     * @see RangeCalendarView.minDateEpoch
     */
    fun getMinDate(calendar: Calendar) {
        _minDate.toCalendar(calendar)
    }

    /**
     * Sets minimum date.
     *
     * @throws IllegalArgumentException if date is less than 1 January 1970 or greater than 30 December 32767, or if minimum date is greater than maximum.
     * @see RangeCalendarView.minDateEpoch
     */
    fun setMinDate(calendar: Calendar) {
        minDateEpoch = (calendarToEpochDay(calendar))
    }

    /**
     * Sets minimum date.
     *
     * @param year  year, in range [1970; 32767]
     * @param month month, in range [1; 12]
     * @param day   day of month, in range [1; *days in month*]
     * @throws IllegalArgumentException if values is out of ranges
     */
    fun setMinDate(year: Int, month: Int, day: Int) {
        ensureDateValid(year, month, day)

        minDateEpoch = PackedDate(year, month, day).toEpochDay()
    }

    /**
     * Gets or sets maximum date in epoch days.
     *
     * @throws IllegalArgumentException if value is less than [RangeCalendarView.MIN_DATE_EPOCH] or
     * greater than [RangeCalendarView.MAX_DATE_EPOCH], or if maximum date is less than minimum.
     */
    var maxDateEpoch: Long
        get() = _maxDateEpoch
        set(value) {
            ensureEpochDayValid(value, "value")
            require(value >= _minDateEpoch) { "max < min" }

            _maxDate = PackedDate.fromEpochDay(value)
            _maxDateEpoch = value

            onMinMaxChanged()
        }

    /**
     * Sets or gets maximum date.
     * Can be used if API >= 26.
     *
     * @throws IllegalArgumentException if date is less than 1 January 1970 or greater than 30 December 32767,
     * or if maximum date is less than minimum.
     *
     * @see RangeCalendarView.maxDateEpoch
     */
    @get:RequiresApi(26)
    @set:RequiresApi(26)
    var maxDate: LocalDate
        get() = _maxDate.toLocalDate()
        set(value) {
            maxDateEpoch = value.toEpochDay()
        }

    /**
     * Sets maximum date to given calendar.
     *
     * @see RangeCalendarView.maxDateEpoch
     */
    fun getMaxDate(calendar: Calendar) {
        _maxDate.toCalendar(calendar)
    }

    /**
     * Sets maximum date.
     *
     * @throws IllegalArgumentException if date is less than 1 January 1970 or greater than 30 December 32767, or if maximum date is less than minimum.
     * @see RangeCalendarView.maxDateEpoch
     */
    fun setMaxDate(calendar: Calendar) {
        maxDateEpoch = calendarToEpochDay(calendar)
    }

    /**
     * Sets maximum date.
     *
     * @param year  year, in range [1970; 32767]
     * @param month month, in range [1; 12]
     * @param day   day of month, in range [1; *days in month*]
     * @throws IllegalArgumentException if values is out of ranges
     */
    fun setMaxDate(year: Int, month: Int, day: Int) {
        ensureDateValid(year, month, day)

        maxDateEpoch = PackedDate(year, month, day).toEpochDay()
    }

    private fun onMinMaxChanged() {
        adapter.setRange(_minDate, _minDateEpoch, _maxDate, _maxDateEpoch)

        setYearAndMonthInternal(currentCalendarYm, false)
    }

    var onSelectionListener: OnSelectionListener? = null

    /**
     * Sets or gets a listener which responds when calendar page is changed (by user or from code)
     */
    var onPageChangeListener: OnCalendarChangeListener? = null

    /**
     * Returns year of selected calendar
     */
    val selectedCalendarYear: Int
        get() = currentCalendarYm.year

    /**
     * Returns month of selected calendar, 1-based
     */
    val selectedCalendarMonth: Int
        get() = currentCalendarYm.month

    /**
     * Gets or sets a background color of selection, by default it's primary color
     */
    @get:ColorInt
    var selectionColor: Int
        get() = adapter.getStyleInt(RangeCalendarPagerAdapter.STYLE_SELECTION_COLOR)
        set(@ColorInt color) {
            adapter.setStyleInt(RangeCalendarPagerAdapter.STYLE_SELECTION_COLOR, color)
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
     * Gets or sets color of day which is in range of selected month
     */
    @get:ColorInt
    var inMonthDayNumberColor: Int
        get() = adapter.getStyleInt(RangeCalendarPagerAdapter.STYLE_IN_MONTH_DAY_NUMBER_COLOR)
        set(@ColorInt color) {
            adapter.setStyleInt(RangeCalendarPagerAdapter.STYLE_IN_MONTH_DAY_NUMBER_COLOR, color)
        }

    /**
     * Gets or sets color of day which is not in range of current month
     */
    @get:ColorInt
    var outMonthDayNumberColor: Int
        get() = adapter.getStyleInt(RangeCalendarPagerAdapter.STYLE_OUT_MONTH_DAY_NUMBER_COLOR)
        set(@ColorInt color) {
            adapter.setStyleInt(RangeCalendarPagerAdapter.STYLE_OUT_MONTH_DAY_NUMBER_COLOR, color)
        }

    /**
     * Gets or sets color of disabled day which is out of minimum and maximum
     */
    @get:ColorInt
    var disabledDayNumberColor: Int
        get() = adapter.getStyleInt(RangeCalendarPagerAdapter.STYLE_DISABLED_DAY_NUMBER_COLOR)
        set(@ColorInt color) {
            adapter.setStyleInt(RangeCalendarPagerAdapter.STYLE_DISABLED_DAY_NUMBER_COLOR, color)
        }

    /**
     * Sets or gets the color of today, by default it's primary color
     */
    @get:ColorInt
    var todayColor: Int
        get() = adapter.getStyleInt(RangeCalendarPagerAdapter.STYLE_TODAY_COLOR)
        set(@ColorInt color) {
            adapter.setStyleInt(RangeCalendarPagerAdapter.STYLE_TODAY_COLOR, color)
        }

    /**
     * Gets or sets a color of weekday
     */
    @get:ColorInt
    var weekdayColor: Int
        get() = adapter.getStyleInt(RangeCalendarPagerAdapter.STYLE_WEEKDAY_COLOR)
        set(@ColorInt color) {
            adapter.setStyleInt(RangeCalendarPagerAdapter.STYLE_WEEKDAY_COLOR, color)
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
     * Gets or sets a hover color
     */
    @get:ColorInt
    var hoverColor: Int
        get() = adapter.getStyleInt(RangeCalendarPagerAdapter.STYLE_HOVER_COLOR)
        set(color) {
            adapter.setStyleInt(RangeCalendarPagerAdapter.STYLE_HOVER_COLOR, color)
        }

    /**
     * Gets or sets a hover color that'll be shown when hovered cell in in selection, by default it's darker version of primary color
     */
    @get:ColorInt
    var hoverOnSelectionColor: Int
        get() = adapter.getStyleInt(RangeCalendarPagerAdapter.STYLE_HOVER_ON_SELECTION_COLOR)
        set(color) {
            adapter.setStyleInt(RangeCalendarPagerAdapter.STYLE_HOVER_ON_SELECTION_COLOR, color)
        }

    /**
     * Gets or sets ratio of radius of round rectangles' in selection.
     * Radius will be computed as multiplication of cell size to this ratio.
     *
     * Set 0, to draw simple rectangles in selection.
     * Set 0.5f and more, to draw circles in cell selection
     */
    var roundRectRadiusRatio: Float
        get() = adapter.getStyleFloat(RangeCalendarPagerAdapter.STYLE_RR_RADIUS_RATIO)
        set(ratio) {
            adapter.setStyleFloat(RangeCalendarPagerAdapter.STYLE_RR_RADIUS_RATIO, ratio)
        }

    /**
     * Gets or sets size of cell, in pixels, should greater than 0
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
     * Note that, narrow weekdays are not always one-letter. It's based on locale.
     * Also, if API level < 24, then [WeekdayType.NARROW] won't work.
     *
     * @throws IllegalArgumentException if type is not one of [WeekdayType] constants
     */
    @get:WeekdayTypeInt
    var weekdayType: Int
        get() = adapter.getStyleInt(RangeCalendarPagerAdapter.STYLE_WEEKDAY_TYPE)
        set(type) {
            adapter.setStyleInt(RangeCalendarPagerAdapter.STYLE_WEEKDAY_TYPE, type)
        }


    /**
     * Gets or sets behavior of what to do when user clicks on already selected cell.
     */
    @get:ClickOnCellSelectionBehaviourInt
    var clickOnCellSelectionBehavior: Int
        get() = adapter.getStyleInt(RangeCalendarPagerAdapter.STYLE_CLICK_ON_CELL_SELECTION_BEHAVIOR)
        set(value) {
            adapter.setStyleInt(
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

    var gridGradientEnabled: Boolean
        get() = adapter.getStyleBool(RangeCalendarPagerAdapter.STYLE_GRID_GRADIENT_ENABLED)
        set(state) {
            adapter.setStyleBool(RangeCalendarPagerAdapter.STYLE_GRID_GRADIENT_ENABLED, state)
        }

    private val gradientStartEndColorPair: PackedIntPair
        get() {
            val bits =
                adapter.getStyleLong(RangeCalendarPagerAdapter.STYLE_GRID_GRADIENT_START_END_COLORS)

            return PackedIntPair(bits)
        }

    @get:ColorInt
    val gradientStartColor: Int
        get() = gradientStartEndColorPair.first

    @get:ColorInt
    val gradientEndColor: Int
        get() = gradientStartEndColorPair.second

    fun setGradientColors(@ColorInt start: Int, @ColorInt end: Int) {
        adapter.setStyleLong(
            RangeCalendarPagerAdapter.STYLE_GRID_GRADIENT_START_END_COLORS,
            packInts(start, end)
        )
    }

    /**
     * Moves (if it's possible) to previous month.
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
     * Moves (if it's possible) to next month.
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
     * Sets current year and month.
     *
     * @param year  year, should be in range [1970; 32767]
     * @param month month, 1-based
     * @param smoothScroll whether to do it with slide animation or not
     * @throws IllegalArgumentException if year and month are out of ranges
     */
    fun setYearAndMonth(year: Int, month: Int, smoothScroll: Boolean = true) {
        ensureYearMonthValid(year, month)

        setYearAndMonthInternal(YearMonth(year, month), smoothScroll)
    }

    private fun setYearAndMonthInternal(ym: YearMonth, smoothScroll: Boolean) {
        currentCalendarYm = ym

        val position = adapter.getItemPositionForYearMonth(ym)

        pager.setCurrentItem(position, smoothScroll)
        updateMoveButtons()
    }

    /**
     * Selects a date in epoch days.
     *
     * @param withAnimation whether to do it with animation or not
     */
    @JvmOverloads
    fun selectDay(epochDay: Long, withAnimation: Boolean = true) {
        ensureEpochDayValid(epochDay, "epochDay")
        selectDayInternal(PackedDate.fromEpochDay(epochDay), withAnimation)
    }

    /**
     * Selects a date with animation.
     * Can be used if API >= 26.
     *
     * @param withAnimation whether to do it with animation or not
     */
    @RequiresApi(26)
    @JvmOverloads
    fun selectDay(date: LocalDate, withAnimation: Boolean = true) {
        selectDayInternal(PackedDate.fromLocalDate(date), withAnimation)
    }

    /**
     * Selects a date in given by [Calendar]
     *
     * @param withAnimation whether to do it with animation or not
     */
    @JvmOverloads
    fun selectDay(calendar: Calendar, withAnimation: Boolean = true) {
        selectDayInternal(PackedDate.fromCalendar(calendar), withAnimation)
    }

    private fun selectDayInternal(date: PackedDate, withAnimation: Boolean) {
        selectInternal(SelectionType.CELL, date.bits.toLong(), withAnimation)
    }

    /**
     * Selects a week.
     *
     * @param year          year, should be in range [1970; 32767]
     * @param month         month, 1-based
     * @param weekIndex     index of week, 0-based
     * @param withAnimation whether to do it with animation or not
     */
    @JvmOverloads
    fun selectWeek(year: Int, month: Int, weekIndex: Int, withAnimation: Boolean = true) {
        selectInternal(
            SelectionType.WEEK,
            packInts(YearMonth(year, month).totalMonths, weekIndex),
            withAnimation
        )
    }

    /**
     * Selects a month with animation.
     *
     * @param year          year, should be in range [1970; 32767]
     * @param month         month, 1-based
     * @param withAnimation whether to do it with animation or not
     */
    @JvmOverloads
    fun selectMonth(year: Int, month: Int, withAnimation: Boolean = true) {
        selectInternal(
            SelectionType.MONTH,
            YearMonth(year, month).totalMonths.toLong(),
            withAnimation
        )
    }

    /**
     * Selects a custom range.
     *
     * @param startEpoch start of the range epoch day, inclusive
     * @param endEpoch end of the range epoch day, inclusive
     * @param withAnimation whether to do it with animation or not
     */
    @JvmOverloads
    fun selectCustom(startEpoch: Long, endEpoch: Long, withAnimation: Boolean = true) {
        selectCustomInternal(
            PackedDate.fromEpochDay(startEpoch),
            PackedDate.fromEpochDay(endEpoch),
            withAnimation
        )
    }

    /**
     * Selects a custom range.
     *
     * @param startDate start of the range defined by [Calendar], inclusive
     * @param endDate end of the range defined by [Calendar], inclusive
     * @param withAnimation whether to do it with animation or not
     */
    @JvmOverloads
    fun selectCustom(startDate: Calendar, endDate: Calendar, withAnimation: Boolean = true) {
        selectCustomInternal(
            PackedDate.fromCalendar(startDate),
            PackedDate.fromCalendar(endDate),
            withAnimation
        )
    }

    /**
     * Selects a custom range with animation. Can be used if API >= 26
     *
     * @param startDate start of the range, inclusive
     * @param endDate end of the range, inclusive
     * @param withAnimation whether to do it with animation of not
     */
    @RequiresApi(26)
    fun selectCustom(startDate: LocalDate, endDate: LocalDate, withAnimation: Boolean = true) {
        selectCustomInternal(
            PackedDate.fromLocalDate(startDate),
            PackedDate.fromLocalDate(endDate),
            withAnimation
        )
    }

    private fun selectCustomInternal(
        startDate: PackedDate,
        endDate: PackedDate,
        withAnimation: Boolean
    ) {
        selectInternal(
            SelectionType.CUSTOM,
            packInts(startDate.bits, endDate.bits),
            withAnimation
        )
    }

    private fun selectInternal(type: Int, data: Long, withAnimation: Boolean) {
        val position = adapter.getItemPositionForYearMonth(
            adapter.getYearMonthForSelection(type, data)
        )
        adapter.select(type, data, withAnimation)
        pager.setCurrentItem(position, withAnimation)
    }

    /**
     * Clears current calendar selection
     */
    fun clearSelection() {
        adapter.clearSelection()
    }

    /**
     * Returns instance of object which encapsulates changing availability of selection types.
     * Notes:
     * Each method is mutating and if you call, for example, `cell(false)` and cell is currently selected,
     * then selection will be cleared.
     * So, after `allowedSelectionTypes().cell(false).cell(true)`, when cell is selected,
     * selection will be cleared although it's enabled.
     *
     * If selection type is disabled, it won't trigger the selection listener.
     *
     */
    fun allowedSelectionTypes(): AllowedSelectionTypes {
        var obj = allowedSelectionTypesObj
        if (obj == null) {
            obj = AllowedSelectionTypes()
            allowedSelectionTypesObj = obj
        }

        return obj
    }

    private fun setAllowedSelectionFlags(flags: Int) {
        allowedSelectionFlags = flags
        val selectionType = adapter.selectionType

        // Clear selection if current selection type become disallowed.
        if (selectionType != SelectionType.NONE && flags and (1 shl selectionType) == 0) {
            clearSelection()
        }
    }

    @JvmOverloads
    fun addDecoration(
        decor: CellDecor,
        epochDay: Long,
        withAnimation: Boolean = true
    ) {
        addDecorationInternal(decor, PackedDate.fromEpochDay(epochDay), withAnimation)
    }

    @RequiresApi(26)
    @JvmOverloads
    fun addDecoration(
        decor: CellDecor,
        date: LocalDate,
        withAnimation: Boolean = true
    ) {
        addDecorationInternal(decor, PackedDate.fromLocalDate(date), withAnimation)
    }

    @JvmOverloads
    fun addDecoration(
        decor: CellDecor,
        calendar: Calendar,
        withAnimation: Boolean = true
    ) {
        addDecorationInternal(decor, PackedDate.fromCalendar(calendar), withAnimation)
    }

    @JvmOverloads
    fun addDecoration(
        decor: CellDecor,
        year: Int,
        month: Int,
        day: Int,
        withAnimation: Boolean = true
    ) {
        addDecorationInternal(decor, PackedDate(year, month, day), withAnimation)
    }

    private fun addDecorationInternal(
        decor: CellDecor,
        date: PackedDate,
        withAnimation: Boolean
    ) {
        adapter.addDecoration(decor, date, withAnimation)
    }

    @JvmOverloads
    fun <T : CellDecor> addDecorations(
        decors: Array<out T>,
        epochDay: Long,
        fractionInterpolator: DecorAnimationFractionInterpolator? = DecorAnimationFractionInterpolator.Simultaneous
    ) {
        addDecorationsInternal(decors, PackedDate.fromEpochDay(epochDay), fractionInterpolator)
    }

    @JvmOverloads
    @RequiresApi(26)
    fun <T : CellDecor> addDecorations(
        decors: Array<out T>,
        date: LocalDate,
        fractionInterpolator: DecorAnimationFractionInterpolator? = DecorAnimationFractionInterpolator.Simultaneous
    ) {
        addDecorationsInternal(decors, PackedDate.fromLocalDate(date), fractionInterpolator)
    }

    @JvmOverloads
    fun <T : CellDecor> addDecorations(
        decors: Array<out T>,
        calendar: Calendar,
        fractionInterpolator: DecorAnimationFractionInterpolator? = DecorAnimationFractionInterpolator.Simultaneous
    ) {
        addDecorationsInternal(decors, PackedDate.fromCalendar(calendar), fractionInterpolator)
    }

    @JvmOverloads
    fun <T : CellDecor> addDecorations(
        decors: Array<out T>,
        year: Int, month: Int, dayOfMonth: Int,
        fractionInterpolator: DecorAnimationFractionInterpolator? = DecorAnimationFractionInterpolator.Simultaneous
    ) {
        addDecorationsInternal(decors, PackedDate(year, month, dayOfMonth), fractionInterpolator)
    }

    private fun <T : CellDecor> addDecorationsInternal(
        decors: Array<out T>,
        date: PackedDate,
        fractionInterpolator: DecorAnimationFractionInterpolator?
    ) {
        adapter.addDecorations(decors, date, fractionInterpolator)
    }

    @JvmOverloads
    fun <T : CellDecor> insertDecorations(
        indexInCell: Int,
        decors: Array<out T>,
        epochDay: Long,
        fractionInterpolator: DecorAnimationFractionInterpolator? = DecorAnimationFractionInterpolator.Simultaneous
    ) {
        insertDecorationsInternal(indexInCell, decors, PackedDate.fromEpochDay(epochDay), fractionInterpolator)
    }

    @JvmOverloads
    @RequiresApi(26)
    fun <T : CellDecor> insertDecorations(
        indexInCell: Int,
        decors: Array<out T>,
        date: LocalDate,
        fractionInterpolator: DecorAnimationFractionInterpolator? = DecorAnimationFractionInterpolator.Simultaneous
    ) {
        insertDecorationsInternal(indexInCell, decors, PackedDate.fromLocalDate(date), fractionInterpolator)
    }

    @JvmOverloads
    fun <T : CellDecor> insertDecorations(
        indexInCell: Int,
        decors: Array<out T>,
        calendar: Calendar,
        fractionInterpolator: DecorAnimationFractionInterpolator? = DecorAnimationFractionInterpolator.Simultaneous
    ) {
        insertDecorationsInternal(indexInCell, decors, PackedDate.fromCalendar(calendar), fractionInterpolator)
    }

    @JvmOverloads
    fun <T : CellDecor> insertDecorations(
        indexInCell: Int,
        decors: Array<out T>,
        year: Int, month: Int, dayOfMonth: Int,
        fractionInterpolator: DecorAnimationFractionInterpolator? = DecorAnimationFractionInterpolator.Simultaneous
    ) {
        insertDecorationsInternal(indexInCell, decors, PackedDate(year, month, dayOfMonth), fractionInterpolator)
    }

    private fun <T : CellDecor> insertDecorationsInternal(
        indexInCell: Int,
        decors: Array<out T>,
        date: PackedDate,
        fractionInterpolator: DecorAnimationFractionInterpolator?
    ) {
        adapter.insertDecorations(indexInCell, decors, date, fractionInterpolator)
    }

    @JvmOverloads
    fun removeDecoration(decor: CellDecor, withAnimation: Boolean = true) {
        adapter.removeDecoration(decor, withAnimation)
    }

    @JvmOverloads
    fun removeDecorationRange(
        start: Int,
        endInclusive: Int,
        epochDay: Long,
        fractionInterpolator: DecorAnimationFractionInterpolator? = DecorAnimationFractionInterpolator.Simultaneous
    ) {
        removeDecorationRangeInternal(
            start,
            endInclusive,
            PackedDate.fromEpochDay(epochDay),
            fractionInterpolator
        )
    }

    @JvmOverloads
    fun removeDecorationRange(
        start: Int,
        endInclusive: Int,
        calendar: Calendar,
        fractionInterpolator: DecorAnimationFractionInterpolator? = DecorAnimationFractionInterpolator.Simultaneous
    ) {
        removeDecorationRangeInternal(
            start,
            endInclusive,
            PackedDate.fromCalendar(calendar),
            fractionInterpolator
        )
    }

    @JvmOverloads
    @RequiresApi(26)
    fun removeDecorationRange(
        start: Int,
        endInclusive: Int,
        date: LocalDate,
        fractionInterpolator: DecorAnimationFractionInterpolator? = DecorAnimationFractionInterpolator.Simultaneous
    ) {
        removeDecorationRangeInternal(
            start,
            endInclusive,
            PackedDate.fromLocalDate(date),
            fractionInterpolator
        )
    }

    @JvmOverloads
    fun removeDecorationRange(
        start: Int,
        endInclusive: Int,
        year: Int, month: Int, dayOfMonth: Int,
        fractionInterpolator: DecorAnimationFractionInterpolator? = DecorAnimationFractionInterpolator.Simultaneous
    ) {
        removeDecorationRangeInternal(
            start, endInclusive,
            PackedDate(year, month, dayOfMonth),
            fractionInterpolator
        )
    }

    private fun removeDecorationRangeInternal(
        start: Int,
        endInclusive: Int,
        date: PackedDate,
        fractionInterpolator: DecorAnimationFractionInterpolator?
    ) {
        adapter.removeDecorationRange(start, endInclusive, date, fractionInterpolator)
    }

    @JvmOverloads
    fun removeAllDecorationsFromCell(
        epochDay: Long,
        fractionInterpolator: DecorAnimationFractionInterpolator? = DecorAnimationFractionInterpolator.Simultaneous
    ) {
        removeAllDecorationsFromCellInternal(PackedDate.fromEpochDay(epochDay), fractionInterpolator)
    }

    @JvmOverloads
    fun removeAllDecorationsFromCell(
        calendar: Calendar,
        fractionInterpolator: DecorAnimationFractionInterpolator? = DecorAnimationFractionInterpolator.Simultaneous
    ) {
        removeAllDecorationsFromCellInternal(PackedDate.fromCalendar(calendar), fractionInterpolator)
    }

    @JvmOverloads
    @RequiresApi(26)
    fun removeAllDecorationsFromCell(
        date: LocalDate,
        fractionInterpolator: DecorAnimationFractionInterpolator? = DecorAnimationFractionInterpolator.Simultaneous
    ) {
        removeAllDecorationsFromCellInternal(PackedDate.fromLocalDate(date), fractionInterpolator)
    }

    @JvmOverloads
    fun removeAllDecorationsFromCell(
        year: Int, month: Int, dayOfMonth: Int,
        fractionInterpolator: DecorAnimationFractionInterpolator? = DecorAnimationFractionInterpolator.Simultaneous
    ) {
        removeAllDecorationsFromCellInternal(PackedDate(year, month, dayOfMonth), fractionInterpolator)
    }

    private fun removeAllDecorationsFromCellInternal(
        date: PackedDate,
        fractionInterpolator: DecorAnimationFractionInterpolator?
    ) {
        adapter.removeAllDecorations(date, fractionInterpolator)
    }

    @JvmOverloads
    fun setDecorationLayoutOptions(
        year: Int, month: Int, dayOfMonth: Int,
        options: DecorLayoutOptions,
        withAnimation: Boolean = true
    ) {
        setDecorationLayoutOptionsInternal(PackedDate(year, month, dayOfMonth), options, withAnimation)
    }

    @JvmOverloads
    fun setDecorationLayoutOptions(
        calendar: Calendar,
        options: DecorLayoutOptions,
        withAnimation: Boolean = true
    ) {
        setDecorationLayoutOptionsInternal(PackedDate.fromCalendar(calendar), options, withAnimation)
    }

    @JvmOverloads
    @RequiresApi(26)
    fun setDecorationLayoutOptions(
        date: LocalDate,
        options: DecorLayoutOptions,
        withAnimation: Boolean = true
    ) {
        setDecorationLayoutOptionsInternal(PackedDate.fromLocalDate(date), options, withAnimation)
    }

    private fun setDecorationLayoutOptionsInternal(
        date: PackedDate,
        options: DecorLayoutOptions,
        withAnimation: Boolean
    ) {
        adapter.setDecorationLayoutOptions(date, options, withAnimation)
    }

    fun setDecorationDefaultLayoutOptions(options: DecorLayoutOptions?) {
        adapter.setStyleObject(RangeCalendarPagerAdapter.STYLE_DECOR_DEFAULT_LAYOUT_OPTIONS, options)
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

        internal val MIN_DATE = PackedDate(1970, 1, 1)
        internal val MAX_DATE = PackedDate(Short.MAX_VALUE.toInt(), 12, 30)

        @JvmField
        val MIN_DATE_EPOCH = MIN_DATE.toEpochDay()

        @JvmField
        val MAX_DATE_EPOCH = MAX_DATE.toEpochDay()

        private const val SELECTION_DAY_FLAG = 1 shl SelectionType.CELL
        private const val SELECTION_WEEK_FLAG = 1 shl SelectionType.WEEK
        private const val SELECTION_MONTH_FLAG = 1 shl SelectionType.MONTH
        private const val SELECTION_CUSTOM_FLAG = 1 shl SelectionType.CUSTOM

        private fun setButtonAlphaIfEnabled(button: ImageButton, alpha: Int) {
            if (button.isEnabled) {
                button.drawable?.alpha = alpha
            }
        }

        private fun ensureEpochDayValid(epochDay: Long, argName: String) {
            require(epochDay in MIN_DATE_EPOCH..MAX_DATE_EPOCH) { argName }
        }

        private fun ensureYearMonthValid(year: Int, month: Int) {
            require(year in 1970..PackedDate.MAX_YEAR) { "Invalid year ($year)" }
            require(month in 1..12) { "Invalid month ($month)" }
        }

        private fun ensureDateValid(year: Int, month: Int, day: Int) {
            ensureYearMonthValid(year, month)

            val daysInMonth = TimeUtils.getDaysInMonth(year, month)
            require(day in 1..daysInMonth) {
                "Invalid day ($day)"
            }
        }

        private fun calendarToEpochDay(calendar: Calendar): Long {
            return PackedDate.fromCalendar(calendar).toEpochDay()
        }
    }
}