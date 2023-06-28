package com.github.pelmenstar1.rangecalendar

import android.animation.TimeInterpolator
import android.content.Context
import android.content.res.Configuration
import android.content.res.TypedArray
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.Parcelable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StyleableRes
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.github.pelmenstar1.rangecalendar.RangeCalendarView.SelectionGate
import com.github.pelmenstar1.rangecalendar.decoration.CellDecor
import com.github.pelmenstar1.rangecalendar.decoration.DecorAnimationFractionInterpolator
import com.github.pelmenstar1.rangecalendar.decoration.DecorLayoutOptions
import com.github.pelmenstar1.rangecalendar.selection.CellAnimationType
import com.github.pelmenstar1.rangecalendar.selection.SelectionManager
import com.github.pelmenstar1.rangecalendar.utils.getBestDatePatternCompat
import com.github.pelmenstar1.rangecalendar.utils.getLocaleCompat
import com.github.pelmenstar1.rangecalendar.utils.getSelectableItemBackground
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import android.text.format.DateFormat
import kotlin.math.max

/**
 * A calendar view which supports range selection and decorations.
 * Internally it uses [ViewPager2] view, so such term as "page" is used here to denote ViewPager2`s page in which calendar view with grid is stored.
 * - Minimum and maximum dates can be set through [minDate] and [maxDate].
 * - [onSelectionListener] can be used to respond to selection.
 * - [selectDay], [selectWeek], [selectMonth] can be used to programmatically set a selection with animation or not.
 * - Most appropriate colors are automatically extracted from attributes, but you can still change it.
 * - [onPageChangeListener] can be used to respond to calendar page changes.
 * [selectedCalendarYear], [selectedCalendarMonth] can be used to get year & month of selected page respectively.
 * - [setYearAndMonth] can be used to change selected calendar. [moveToPreviousMonth], [moveToNextMonth] also can be used.
 * - [addDecoration], [addDecorations], [insertDecorations], [removeDecoration], [removeDecorationRange], [removeAllDecorationsFromCell]
 * can be used to manipulate cell decorations. See [CellDecor].
 *
 * ## Selection gate
 *
 * The gate is used to intercept all selection requests and determine whether such selection is valid and should be allowed.
 * If the gate is specified, appropriate methods are called when there's a selection request. A selection request can
 * come from user (motion events) or from the code. Such methods as [selectDay], [selectWeek], [selectMonth], [selectRange]
 * "sends" a selection request. There's also situations when a selection request may not be processed by [SelectionGate].
 * Usually, it's when there are another factors that rejects a request, for example when a range to be selected is out of
 * enabled range. Besides, a selection request can be processed by [SelectionGate] when [minDate] or [maxDate] are called.
 */
class RangeCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {
    /**
     * Represents a "gate" which determines whether to allow selection or not.
     */
    interface SelectionGate {
        fun range(
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
         * Fires when on date range selection. Start date of selection specified by [startYear], [startMonth], [startDay] is inclusive.
         * End date specified by [endYear], [endMonth], [endDay] is inclusive, so that if a single cell is selected, start date is equal to end date.
         * But start date cannot be greater than end date.
         *
         * @param startYear year of the start date
         * @param startMonth month of the start date, 1-based
         * @param startDay day of month of the start date, 1-based
         * @param endYear year of the end date
         * @param endMonth month of the end date, 1-based
         * @param endDay day of month of the end date, 1-based
         */
        fun onSelection(
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

    /**
     * The interface is responsible for formatting year and month to user-friendly string.
     */
    interface InfoFormatter {
        /**
         * Returns user-friendly representation of [year] and [month] values.
         *
         * @param year year, in range `[0; 65535]`
         * @param month month, 1-based.
         */
        fun format(year: Int, month: Int): CharSequence
    }

    /**
     * The interface has same purpose as [InfoFormatter] but also depends on current configuration's locale.
     */
    interface LocalizedInfoFormatter : InfoFormatter {
        /**
         * It's called when current configuration's locale is changed.
         */
        fun onLocaleChanged(newLocale: Locale)
    }

    internal class DefaultLocalizedInfoFormatter(
        context: Context,
        pattern: String
    ) : LocalizedInfoFormatter {
        val dateFormatter = CompatDateFormatter(context, pattern)

        override fun format(year: Int, month: Int): CharSequence {
            return dateFormatter.format(PackedDate(year, month, 1))
        }

        override fun onLocaleChanged(newLocale: Locale) {
            dateFormatter.locale = newLocale
        }
    }

    private class ExtractAttributesScope(
        private val calendarView: RangeCalendarView,
        private val attrs: TypedArray
    ) {
        fun color(@StyleableRes index: Int, styleType: Int) {
            extractPrimitive(
                index, styleType,
                ::PackedInt,
                extract = { getColor(index, 0) }
            )
        }

        fun dimension(@StyleableRes index: Int, styleType: Int) {
            extractPrimitive(
                index, styleType,
                ::PackedInt,
                extract = { getDimension(index, 0f) }
            )
        }

        fun int(@StyleableRes index: Int, styleType: Int) {
            extractPrimitive(
                index, styleType,
                ::PackedInt,
                extract = { getInteger(index, 0) }
            )
        }

        fun boolean(@StyleableRes index: Int, styleType: Int) {
            extractPrimitive(
                index, styleType,
                ::PackedInt,
                extract = { getBoolean(index, false) }
            )
        }

        inline fun color(
            @StyleableRes index: Int,
            getStyle: GetCalendarStyleProp
        ) = color(index, getStyle(RangeCalendarStyleData.Companion))

        inline fun dimension(
            @StyleableRes index: Int,
            getStyle: GetCalendarStyleProp
        ) = dimension(index, getStyle(RangeCalendarStyleData.Companion))

        inline fun int(
            @StyleableRes index: Int,
            getStyle: GetCalendarStyleProp
        ) = int(index, getStyle(RangeCalendarStyleData.Companion))

        inline fun boolean(
            @StyleableRes index: Int,
            getStyle: GetCalendarStyleProp
        ) = boolean(index, getStyle(RangeCalendarStyleData.Companion))

        private inline fun <T> extractPrimitive(
            @StyleableRes index: Int,
            styleType: Int,
            createPacked: (T) -> PackedInt,
            extract: TypedArray.() -> T,
        ) {
            if (attrs.hasValue(index)) {
                val attrValue = createPacked(attrs.extract())

                calendarView.adapter.setStylePacked(styleType, attrValue, notify = false)
            }
        }
    }

    private val pager: ViewPager2
    private val prevButton: ImageButton
    private val nextOrClearButton: ImageButton
    internal val infoView: TextView

    private var _timeZone: TimeZone

    private var _minDate = PackedDate.MIN_DATE
    private var _maxDate = PackedDate.MAX_DATE
    private var _infoPattern: String
    private var originInfoFormat: String = DEFAULT_INFO_FORMAT
    private var _infoFormatter: InfoFormatter

    private var currentCalendarYm = YearMonth(0)

    @JvmField // used in tests
    internal val adapter: RangeCalendarPagerAdapter

    private val buttonSize: Int
    private val hPadding: Int
    private val topContainerMarginBottom: Int

    private val toolbarManager: CalendarToolbarManager

    private var isFirstDaySunday = false
    private var currentLocale: Locale? = null

    private val layoutRect = Rect()
    private val layoutOutRect = Rect()

    init {
        val res = context.resources

        buttonSize = res.getDimensionPixelSize(R.dimen.rangeCalendar_actionButtonSize)
        topContainerMarginBottom =
            res.getDimensionPixelOffset(R.dimen.rangeCalendar_topContainerMarginBottom)

        val selectableBg = context.getSelectableItemBackground()
        val cr = CalendarResources(context)

        hPadding = cr.hPadding.toInt()

        val currentTimeZone = TimeZone.getDefault()
        _timeZone = currentTimeZone

        val today = PackedDate.today(currentTimeZone)

        adapter = RangeCalendarPagerAdapter(cr).apply {
            setToday(today)
            setStyleObject(
                { CELL_ACCESSIBILITY_INFO_PROVIDER },
                DefaultRangeCalendarCellAccessibilityInfoProvider(context),
                notify = false
            )

            onSelectionListener = createOnSelectionListener()
        }

        prevButton = AppCompatImageButton(context).apply {
            setOnClickListener { moveToPreviousMonth() }
        }

        nextOrClearButton = AppCompatImageButton(context).apply {
            setOnClickListener(createNextButtonClickListener())
        }

        if (selectableBg != null) {
            prevButton.setBackground(selectableBg)
            nextOrClearButton.setBackground(selectableBg.constantState!!.newDrawable(res))
        }

        infoView = AppCompatTextView(context).apply {
            setTextColor(cr.textColor)
            setOnClickListener {
                val (year, month) = currentCalendarYm

                selectMonth(year, month)
            }
        }

        toolbarManager = CalendarToolbarManager(
            context,
            cr.colorControlNormal,
            prevButton, nextOrClearButton, infoView
        )

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
                    toolbarManager.onPageScrolled(positionOffset)
                }

                override fun onPageSelected(position: Int) {
                    val ym = this@RangeCalendarView.adapter.getYearMonthForCalendar(position)

                    currentCalendarYm = ym
                    setInfoViewYearMonth(ym)

                    toolbarManager.restoreButtonsAlpha()
                    updateMoveButtons()

                    onPageChangeListener?.onPageChanged(ym.year, ym.month)
                }

                override fun onPageScrollStateChanged(state: Int) {
                    val adapter = this@RangeCalendarView.adapter

                    if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                        adapter.clearHoverAt(currentCalendarYm)
                    }
                }
            })
        }

        addView(pager)
        addView(prevButton)
        addView(nextOrClearButton)
        addView(infoView)

        // It should be called after the toolbarManager is initialized.
        initLocaleDependentValues()

        _infoPattern = getBestDatePatternCompat(currentLocale!!, DEFAULT_INFO_FORMAT)
        _infoFormatter = DefaultLocalizedInfoFormatter(context, _infoPattern)

        // It should be called after _infoFormatter is initialized
        setYearAndMonthInternal(YearMonth.forDate(today), false)

        attrs?.let { initFromAttributes(context, it, defStyleAttr) }
    }

    fun createNextButtonClickListener() = OnClickListener {
        if (toolbarManager.isNextButtonActClear) {
            clearSelection()
        } else {
            moveToNextMonth()
        }
    }

    fun createOnSelectionListener() = object : OnSelectionListener {
        override fun onSelectionCleared() {
            toolbarManager.onSelectionCleared()

            onSelectionListener?.onSelectionCleared()
        }

        override fun onSelection(
            startYear: Int,
            startMonth: Int,
            startDay: Int,
            endYear: Int,
            endMonth: Int,
            endDay: Int
        ) {
            toolbarManager.onSelection()
            onSelectionListener?.onSelection(
                startYear, startMonth, startDay,
                endYear, endMonth, endDay
            )
        }
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

                color(R.styleable.RangeCalendarView_rangeCalendar_inMonthDayNumberColor) { IN_MONTH_TEXT_COLOR }
                color(R.styleable.RangeCalendarView_rangeCalendar_outMonthDayNumberColor) { OUT_MONTH_TEXT_COLOR }
                color(R.styleable.RangeCalendarView_rangeCalendar_disabledDayNumberColor) { DISABLED_TEXT_COLOR }
                color(R.styleable.RangeCalendarView_rangeCalendar_todayColor) { TODAY_TEXT_COLOR }
                color(R.styleable.RangeCalendarView_rangeCalendar_weekdayColor) { WEEKDAY_TEXT_COLOR }
                color(R.styleable.RangeCalendarView_rangeCalendar_hoverColor) { HOVER_COLOR }
                color(R.styleable.RangeCalendarView_rangeCalendar_hoverOnSelectionColor) { HOVER_ON_SELECTION_COLOR }
                dimension(R.styleable.RangeCalendarView_rangeCalendar_dayNumberTextSize) { DAY_NUMBER_TEXT_SIZE }
                dimension(R.styleable.RangeCalendarView_rangeCalendar_weekdayTextSize) { WEEKDAY_TEXT_SIZE }
                int(R.styleable.RangeCalendarView_rangeCalendar_weekdayType) { WEEKDAY_TYPE }
                int(R.styleable.RangeCalendarView_rangeCalendar_clickOnCellSelectionBehavior) { CLICK_ON_CELL_SELECTION_BEHAVIOR }
                dimension(R.styleable.RangeCalendarView_rangeCalendar_cellRoundRadius) { CELL_ROUND_RADIUS }
                boolean(R.styleable.RangeCalendarView_rangeCalendar_vibrateOnSelectingCustomRange) { VIBRATE_ON_SELECTING_RANGE }
                int(R.styleable.RangeCalendarView_rangeCalendar_selectionFillGradientBoundsType) { SELECTION_FILL_GRADIENT_BOUNDS_TYPE }
                int(R.styleable.RangeCalendarView_rangeCalendar_cellAnimationType) { CELL_ANIMATION_TYPE }
                boolean(R.styleable.RangeCalendarView_rangeCalendar_showAdjacentMonths) { SHOW_ADJACENT_MONTHS }
                boolean(R.styleable.RangeCalendarView_rangeCalendar_isSelectionAnimatedByDefault) { IS_SELECTION_ANIMATED_BY_DEFAULT }
                boolean(R.styleable.RangeCalendarView_rangeCalendar_isHoverAnimationEnabled) { IS_HOVER_ANIMATION_ENABLED }

                // cellSize, cellWidth, cellHeight require special logic.
                // If cellSize exists, it's written to both cellWidth and cellHeight, but
                // if either of cellWidth or cellHeight exist, they take precedence over cellSize.
                var cellWidth = a.getDimension(
                    R.styleable.RangeCalendarView_rangeCalendar_cellWidth,
                    Float.NaN
                )
                var cellHeight = a.getDimension(
                    R.styleable.RangeCalendarView_rangeCalendar_cellHeight,
                    Float.NaN
                )

                val cellSize =
                    a.getDimension(R.styleable.RangeCalendarView_rangeCalendar_cellSize, Float.NaN)
                if (!cellSize.isNaN()) {
                    if (cellWidth.isNaN()) cellWidth = cellSize
                    if (cellHeight.isNaN()) cellHeight = cellSize
                }

                if (cellWidth == cellHeight) {
                    // Both cellWidth and cellHeight are not NaN because
                    // comparison with NaN always gives false.
                    adapter.setCellSize(cellWidth)
                } else {
                    if (!cellWidth.isNaN()) {
                        adapter.setStyleFloat({ CELL_WIDTH }, cellWidth)
                    }

                    if (!cellHeight.isNaN()) {
                        adapter.setStyleFloat({ CELL_HEIGHT }, cellHeight)
                    }
                }

                // weekdays should be set via setter because additional checks should be made.
                if (a.hasValue(R.styleable.RangeCalendarView_rangeCalendar_weekdays)) {
                    val resId =
                        a.getResourceId(R.styleable.RangeCalendarView_rangeCalendar_weekdays, 0)

                    weekdays = resources.getStringArray(resId)
                }

                useBestPatternForInfoPattern = a.getBoolean(
                    R.styleable.RangeCalendarView_rangeCalendar_useBestPatternForInfoPattern,
                    true
                )

                a.getString(R.styleable.RangeCalendarView_rangeCalendar_infoPattern)?.also { infoPattern = it }
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

    private fun initLocaleDependentValues() {
        // infoFormatter is initialized on creation. No need in double creating the underlying models.
        refreshLocaleDependentValues(
            newLocale = context.getLocaleCompat(),
            updateInfoFormatter = false
        )
    }

    private fun refreshLocaleDependentValues(newLocale: Locale, updateInfoFormatter: Boolean) {
        currentLocale = newLocale

        refreshIsFirstDaySunday(newLocale)
        if (updateInfoFormatter) {
            _infoFormatter.let {
                if (it is LocalizedInfoFormatter) {
                    it.onLocaleChanged(newLocale)

                    onInfoFormatterOptionsChanged()
                }
            }
        }

        toolbarManager.onLocaleChanged()
    }

    private fun refreshIsFirstDaySunday(locale: Locale) {
        isFirstDaySunday = Calendar
            .getInstance(locale)
            .firstDayOfWeek == Calendar.SUNDAY
    }

    /**
     * Notifies that today's date in specified [timeZone] is changed.
     */
    fun notifyTodayChanged() {
        adapter.setToday(PackedDate.today(_timeZone))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val newLocale = newConfig.getLocaleCompat()
        if (currentLocale != newLocale) {
            // Do a full update.
            refreshLocaleDependentValues(newLocale, updateInfoFormatter = true)
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        return SavedState(super.onSaveInstanceState()!!).apply {
            selectionYm = adapter.selectionYm
            selectionRange = adapter.selectionRange
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
            if (state.selectionRange.isValid) {
                adapter.selectOnRestore(state.selectionYm, state.selectionRange)
            }
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        pager.measure(widthMeasureSpec, heightMeasureSpec)

        val pagerWidth = pager.measuredWidth
        val buttonSize = buttonSize

        val buttonSpec = MeasureSpec.makeMeasureSpec(buttonSize, MeasureSpec.EXACTLY)

        val maxInfoWidth = pagerWidth - 2 * (hPadding + buttonSize)
        val infoWidthSpec = MeasureSpec.makeMeasureSpec(maxInfoWidth, MeasureSpec.AT_MOST)
        val infoHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)

        infoView.measure(infoWidthSpec, infoHeightSpec)
        prevButton.measure(buttonSpec, buttonSpec)
        nextOrClearButton.measure(buttonSpec, buttonSpec)

        val toolbarHeight = max(infoView.measuredHeight, buttonSize)

        toolbarManager.selectionView?.also { sv ->
            var maxWidth = pagerWidth - 2 * hPadding - buttonSize
            if (!toolbarManager.hasSelectionViewClearButton) {
                maxWidth -= buttonSize
            }

            measureChild(
                sv,
                MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(toolbarHeight, MeasureSpec.AT_MOST)
            )
        }

        setMeasuredDimension(
            pager.measuredWidthAndState,
            resolveSize(
                pager.measuredHeight + toolbarHeight + topContainerMarginBottom,
                heightMeasureSpec
            )
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l

        val hPadding = hPadding
        val buttonSize = buttonSize

        val toolbarManager = toolbarManager
        val prevRight = hPadding + buttonSize
        val nextLeft = width - prevRight

        val infoWidth = infoView.measuredWidth
        val infoHeight = infoView.measuredHeight

        val toolbarHeight = max(buttonSize, infoHeight)

        val infoLeft = (width - infoWidth) / 2
        val infoTop = (toolbarHeight - infoHeight) / 2
        val buttonTop = (toolbarHeight - buttonSize) / 2
        val buttonBottom = buttonTop + buttonSize
        val pagerTop = toolbarHeight + topContainerMarginBottom

        prevButton.layout(hPadding, buttonTop, prevRight, buttonBottom)
        nextOrClearButton.layout(nextLeft, buttonTop, nextLeft + buttonSize, buttonBottom)
        infoView.layout(infoLeft, infoTop, infoLeft + infoWidth, infoTop + infoHeight)

        pager.layout(0, pagerTop, pager.measuredWidth, pagerTop + pager.measuredHeight)

        toolbarManager.selectionView?.also { sv ->
            val lr = layoutRect
            val lrOut = layoutOutRect

            val svLayoutParams = toolbarManager.selectionViewLayoutParams
            val gravity = svLayoutParams.gravity

            // lr's top is always 0
            // lr.top = 0
            lr.bottom = toolbarHeight

            // Detection of whether the gravity is center_horizontal is a little bit complicated.
            // Basically we need to check whether bits AXIS_PULL_BEFORE and AXIS_PULL_AFTER bits are 0.
            val isCenterHorizontal =
                gravity and ((Gravity.AXIS_PULL_BEFORE or Gravity.AXIS_PULL_AFTER) shl Gravity.AXIS_X_SHIFT) == 0

            // If the gravity on x-axis is center, let the view be centered along the whole
            // calendar view (except padding).
            if (isCenterHorizontal) {
                lr.left = hPadding
                lr.right = width - hPadding
            } else {
                lr.left = if (toolbarManager.hasSelectionViewClearButton) hPadding else prevRight
                lr.right = nextLeft
            }

            val absGravity = if (Build.VERSION.SDK_INT >= 17) {
                Gravity.getAbsoluteGravity(gravity, layoutDirection)
            } else {
                // Strip off relative bits to get left/right in case we have no layoutDirection data.
                gravity and Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK.inv()
            }

            Gravity.apply(absGravity, sv.measuredWidth, sv.measuredHeight, lr, lrOut)

            sv.layout(
                lrOut.left, lrOut.top,
                lrOut.right, lrOut.bottom
            )
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
        get() = toolbarManager.selectionView
        set(value) {
            val oldSelectionView = toolbarManager.selectionView

            if (oldSelectionView != null) {
                toolbarManager.hideSelectionView()

                // selection view is always the last one.
                removeViewAt(childCount - 1)
            }

            toolbarManager.selectionView = value

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
        get() = toolbarManager.selectionViewTransitionDuration
        set(duration) {
            validateAnimationDuration(duration)

            toolbarManager.selectionViewTransitionDuration = duration
        }

    /**
     * Gets or sets layout params for selection view
     */
    var selectionViewLayoutParams: CalendarSelectionViewLayoutParams
        get() = toolbarManager.selectionViewLayoutParams
        set(layoutParams) {
            toolbarManager.selectionViewLayoutParams = layoutParams
            requestLayout()
        }

    var selectionViewTransitionInterpolator: TimeInterpolator
        get() = toolbarManager.selectionViewTransitionInterpolator
        set(value) {
            toolbarManager.selectionViewTransitionInterpolator = value
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
        get() = toolbarManager.hasSelectionViewClearButton
        set(value) {
            if (toolbarManager.hasSelectionViewClearButton == value) {
                return
            }

            toolbarManager.hasSelectionViewClearButton = value
            requestLayout()
        }

    /**
     * Gets or sets formatter used for creating a text for info text view (where current year and month is shown).
     * If you changed it to another formatter and want to use default one, call [setDefaultInfoFormatter].
     */
    var infoFormatter: InfoFormatter
        get() = _infoFormatter
        set(value) {
            _infoFormatter = value

            onInfoFormatterOptionsChanged()
        }

    /**
     * Gets or sets date-time pattern for [infoFormatter]. The pattern should be suitable with [java.text.SimpleDateFormat].
     * It actually changes the format only if [infoFormatter] is a default one.
     * Although, if it's not, it won't throw an exception and will save the value in case [setDefaultInfoFormatter] is called.
     *
     * The pattern is affected by [useBestPatternForInfoPattern] that specifies whether [android.text.format.DateFormat.getBestDateTimePattern] should be called on given pattern.
     * By default, [useBestPatternForInfoPattern] is true. The motivation for that is described in [useBestPatternForInfoPattern] documentation.
     *
     * The only important components of the pattern are year and month ones.
     * Anyway, the datetime passed to the formatter is `01.mm.yyyy 00:00:00.000` where `mm` is month value and `yyyy` is year value.
     */
    var infoPattern: String
        get() = _infoPattern
        set(value) {
            setInfoPatternInternal(value)
        }

    /**
     * Gets or sets whether [DateFormat.getBestDateTimePattern] should be called on patterns set via [infoPattern].
     *
     * As noted above, the property depends on [DateFormat.getBestDateTimePattern] that is available from API level 18. On older versions, this property changes nothing.
     *
     * This behaviour is enabled by default because of locale changes that directly affect the resulting string and it's better to use the best format for locale when possible.
     * When the value is `true`, it may lead to unexpected effects when [infoPattern] is set to the one value and getter returns another value (processed by [DateFormat.getBestDateTimePattern])
     *
     * If the [infoPattern] is set when [useBestPatternForInfoPattern] is `true` and then [useBestPatternForInfoPattern] is changed to `false`, the final format will be value set to [infoPattern] without additional processing.
     */
    var useBestPatternForInfoPattern: Boolean = true
        set(value) {
            field = value

            setInfoPatternInternal(originInfoFormat)
        }

    /**
     * Sets [infoFormatter] to the default one.
     */
    fun setDefaultInfoFormatter() {
        infoFormatter = DefaultLocalizedInfoFormatter(context, _infoPattern)
    }

    private fun setInfoPatternInternal(pattern: String) {
        _infoPattern = if (useBestPatternForInfoPattern) {
            getBestDatePatternCompat(currentLocale!!, pattern)
        } else {
            pattern
        }

        originInfoFormat = pattern

        infoFormatter.let {
            if (it is DefaultLocalizedInfoFormatter) {
                it.dateFormatter.pattern = _infoPattern

                onInfoFormatterOptionsChanged()
            }
        }
    }

    private fun onInfoFormatterOptionsChanged() {
        setInfoViewYearMonth(currentCalendarYm)
    }

    private fun setInfoViewYearMonth(ym: YearMonth) {
        infoView.text = _infoFormatter.format(ym.year, ym.month)
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
            val packedMin = PackedDate.fromLocalDate(value)

            if (_maxDate > packedMin) {
                throwInvalidMinMax()
            }

            _minDate = packedMin
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
            val packedMax = PackedDate.fromLocalDate(value)

            if (packedMax > _minDate) {
                throwInvalidMinMax()
            }

            _maxDate = packedMax

            onMinMaxChanged()
        }

    private fun throwInvalidMinMax(): Nothing {
        throw IllegalStateException("Minimum date is greater than maximum one")
    }

    private fun onMinMaxChanged() {
        adapter.setRange(_minDate, _maxDate)

        setYearAndMonthInternal(currentCalendarYm, smoothScroll = false)
    }

    /**
     * Gets or sets calendar's time zone. By default, it's default system time zone ([TimeZone.getDefault]).
     *
     * Calendar's time zone affects to "today" cell recognition.
     * When new time zone is set, "today" cell is updated.
     */
    var timeZone: TimeZone
        get() = _timeZone
        set(value) {
            _timeZone = value

            notifyTodayChanged()
        }

    /**
     * Gets or sets a listener which responds to selection changes
     */
    var onSelectionListener: OnSelectionListener? = null

    /**
     * Gets or sets a selection "gate" which determines whether to allow selection or not.
     */
    var selectionGate: SelectionGate?
        get() = adapter.selectionGate
        set(value) {
            adapter.selectionGate = value
        }

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
     * by default it's solid color which color is extracted from [androidx.appcompat.R.attr.colorPrimary]
     */
    var selectionFill: Fill
        get() = adapter.getStyleObject { SELECTION_FILL }
        set(value) {
            adapter.setStyleObject({ SELECTION_FILL }, value)
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
     * Gets or sets the way of determining bounds of selection. It only matters when selection fill is gradient-like.
     */
    var selectionFillGradientBoundsType: SelectionFillGradientBoundsType
        get() = adapter.getStyleEnum(
            { SELECTION_FILL_GRADIENT_BOUNDS_TYPE },
            SelectionFillGradientBoundsType::ofOrdinal
        )
        set(value) {
            adapter.setStyleEnum({ SELECTION_FILL_GRADIENT_BOUNDS_TYPE }, value)
        }

    /**
     * Gets or sets a text size of day number, in pixels
     */
    var dayNumberTextSize: Float
        get() = adapter.getStyleFloat { DAY_NUMBER_TEXT_SIZE }
        set(size) {
            validateTextSize(size)

            adapter.setStyleFloat({ DAY_NUMBER_TEXT_SIZE }, size)
        }

    /**
     * Gets or sets color of day which is in range of selected calendar's month.
     */
    @get:ColorInt
    var inMonthDayNumberColor: Int
        get() = adapter.getStyleInt { IN_MONTH_TEXT_COLOR }
        set(@ColorInt color) {
            adapter.setStyleInt({ IN_MONTH_TEXT_COLOR }, color)
        }

    /**
     * Gets or sets color of day which is out of selected calendar's month.
     */
    @get:ColorInt
    var outMonthDayNumberColor: Int
        get() = adapter.getStyleInt { OUT_MONTH_TEXT_COLOR }
        set(@ColorInt color) {
            adapter.setStyleInt({ OUT_MONTH_TEXT_COLOR }, color)
        }


    /**
     * Gets or sets color of disabled day which is out of enabled range created by [minDate] and [maxDate].
     */
    @get:ColorInt
    var disabledDayNumberColor: Int
        get() = adapter.getStyleInt { DISABLED_TEXT_COLOR }
        set(@ColorInt color) {
            adapter.setStyleInt({ DISABLED_TEXT_COLOR }, color)
        }

    /**
     * Gets or sets a text color of cell which represents today date,
     * by default it's color extracted from [androidx.appcompat.R.attr.colorPrimary].
     */
    @get:ColorInt
    var todayColor: Int
        get() = adapter.getStyleInt { TODAY_TEXT_COLOR }
        set(@ColorInt color) {
            adapter.setStyleInt({ TODAY_TEXT_COLOR }, color)
        }

    /**
     * Gets or sets a text color of weekday,
     * by default it's a color extracted from [androidx.appcompat.R.style.TextAppearance_AppCompat].
     */
    @get:ColorInt
    var weekdayColor: Int
        get() = adapter.getStyleInt { WEEKDAY_TEXT_COLOR }
        set(@ColorInt color) {
            adapter.setStyleInt({ WEEKDAY_TEXT_COLOR }, color)
        }

    /**
     * Gets or sets a text size of weekday, in pixels
     */
    var weekdayTextSize: Float
        get() = adapter.getStyleFloat { WEEKDAY_TEXT_SIZE }
        set(size) {
            validateTextSize(size)

            adapter.setStyleFloat({ WEEKDAY_TEXT_SIZE }, size)
        }

    /**
     * Gets or sets a typeface of weekday. Pass null to clear previous typeface.
     */
    var weekdayTypeface: Typeface?
        get() = adapter.getStyleObject { WEEKDAY_TYPEFACE }
        set(value) {
            adapter.setStyleObject({ WEEKDAY_TYPEFACE }, value)
        }

    /**
     * Gets or sets array of weekdays. Length of the array should be exactly 7. First day of week is expected to be Monday.
     *
     * Note that the getter returns a reference to weekdays, not a copy, thus that array should not be modified in any way.
     * If you want to update weekdays, make a copy with changes and use setter.
     *
     * Pass `null` to use localized weekdays. Note that if you do that, the getter will return localized weekdays, not `null`.
     */
    var weekdays: Array<out String>?
        get() = adapter.getStyleObject { WEEKDAYS }
        set(value) {
            if (value != null && value.size != 7) {
                throw IllegalArgumentException("Length of the array should be 7")
            }

            // Copy the array because the caller might be it later.
            adapter.setStyleObject({ WEEKDAYS }, value?.copyOf())
        }

    /**
     * Gets or sets a background color of cell which appears when user hovers under a cell
     * (when a pointer is registered to be down).
     *
     * @see [hoverOnSelectionColor]
     */
    @get:ColorInt
    var hoverColor: Int
        get() = adapter.getStyleInt { HOVER_COLOR }
        set(color) {
            adapter.setStyleInt({ HOVER_COLOR }, color)
        }

    /**
     * Gets or sets a background color of cell which appears when user hovers under a cell and selection contains the cell.
     * As background of the calendar and selection color are different, common [hoverColor] wouldn't look fine on selection.
     */
    @get:ColorInt
    var hoverOnSelectionColor: Int
        get() = adapter.getStyleInt { HOVER_ON_SELECTION_COLOR }
        set(color) {
            adapter.setStyleInt({ HOVER_ON_SELECTION_COLOR }, color)
        }

    /**
     * Gets or sets round radius of cell shape.
     *
     * Set to 0f if cell shape should be rectangle.
     * Set to [Float.POSITIVE_INFINITY] if cell shape is wanted to be circle regardless the size of it.
     */
    var cellRoundRadius: Float
        get() = adapter.getStyleFloat { CELL_ROUND_RADIUS }
        set(value) {
            require(value >= 0) { "Round radius should be non-negative" }

            adapter.setStyleFloat({ CELL_ROUND_RADIUS }, value)
        }

    /**
     * Gets or sets size of cells, in pixels, should be greater than 0.
     * If [cellWidth] and [cellHeight] are different, returns the maximum of these.
     * In general, the getter should not be used at all, meanwhile the setter will be slightly more
     * efficient than manually setting [cellWidth] and [cellHeight] to the same value.
     */
    var cellSize: Float
        get() = max(cellWidth, cellHeight)
        set(size) {
            require(size > 0) { "Cell size should be positive" }

            adapter.setCellSize(size)
        }

    /**
     * Gets or sets width of cells, in pixels, should be greater than 0.
     */
    var cellWidth: Float
        get() = adapter.getStyleFloat { CELL_WIDTH }
        set(value) {
            require(value > 0) { "Cell width should be positive" }

            adapter.setStyleFloat({ CELL_WIDTH }, value)
        }

    /**
     * Gets or sets height of cells, in pixels, should be greater than 0.
     */
    var cellHeight: Float
        get() = adapter.getStyleFloat { CELL_HEIGHT }
        set(value) {
            require(value > 0) { "Cell height should be positive" }

            adapter.setStyleFloat({ CELL_HEIGHT }, value)
        }

    /**
     * Gets or sets weekday type.
     *
     * If [WeekdayType.SHORT], then weekdays will be: Mo, Tue, Wed, Thu, Fri.
     * If [WeekdayType.NARROW], then weekdays will be: M, T, W, T, F.
     * Note:
     * - Narrow weekdays depend on user locale and are not always one-letter.
     * - **If API level is less than 24, [WeekdayType.NARROW] won't work.**
     *
     * Changing weekday type when custom weekdays array was set (via [weekdays]) won't have any effect, even though when
     * `null` value is passed to the setter of [weekdays], the latest value of [weekdayType] will be used.
     *
     * @throws IllegalArgumentException if type is not one of [WeekdayType] constants
     */
    var weekdayType: WeekdayType
        get() = adapter.getStyleEnum({ WEEKDAY_TYPE }, WeekdayType::ofOrdinal)
        set(type) {
            adapter.setStyleEnum({ WEEKDAY_TYPE }, type)
        }

    /**
     * Gets or sets behavior of what to do when user clicks on already selected cell.
     */
    var clickOnCellSelectionBehavior: ClickOnCellSelectionBehavior
        get() {
            return adapter.getStyleEnum(
                { CLICK_ON_CELL_SELECTION_BEHAVIOR }, ClickOnCellSelectionBehavior::ofOrdinal
            )
        }
        set(value) {
            adapter.setStyleEnum({ CLICK_ON_CELL_SELECTION_BEHAVIOR }, value)
        }

    /**
     * Gets or sets duration (in ms) of common calendar animations like selecting cell, week, month, clearing selection etc.
     * In other words, it's duration of all animations except hover animation.
     *
     * @throws IllegalArgumentException if duration is negative
     */
    var commonAnimationDuration: Int
        get() = adapter.getStyleInt { COMMON_ANIMATION_DURATION }
        set(duration) {
            validateAnimationDuration(duration)

            adapter.setStyleInt({ COMMON_ANIMATION_DURATION }, duration)
        }

    /**
     * Gets or sets time interpolator of common calendar animations.
     * By default, the interpolator is linear.
     */
    var commonAnimationInterpolator: TimeInterpolator
        get() = adapter.getStyleObject { COMMON_ANIMATION_INTERPOLATOR }
        set(value) {
            adapter.setStyleObject({ COMMON_ANIMATION_INTERPOLATOR }, value)
        }

    /**
     * Gets or sets duration (in ms) of hover animation.
     *
     * @throws IllegalArgumentException if duration is negative
     */
    var hoverAnimationDuration: Int
        get() = adapter.getStyleInt { HOVER_ANIMATION_DURATION }
        set(duration) {
            validateAnimationDuration(duration)

            adapter.setStyleInt({ HOVER_ANIMATION_DURATION }, duration)
        }

    /**
     * Gets or sets time interpolator of hover animation.
     */
    var hoverAnimationInterpolator: TimeInterpolator
        get() = adapter.getStyleObject { HOVER_ANIMATION_INTERPOLATOR }
        set(value) {
            adapter.setStyleObject({ HOVER_ANIMATION_INTERPOLATOR }, value)
        }

    /**
     * Gets or sets whether device should vibrate when user starts to select custom range.
     */
    var vibrateOnSelectingCustomRange: Boolean
        get() = adapter.getStyleBool { VIBRATE_ON_SELECTING_RANGE }
        set(state) {
            adapter.setStyleBool({ VIBRATE_ON_SELECTING_RANGE }, state)
        }

    /**
     * Gets or sets animation type for cells.
     */
    var cellAnimationType: CellAnimationType
        get() = adapter.getStyleEnum({ CELL_ANIMATION_TYPE }, CellAnimationType::ofOrdinal)
        set(type) {
            adapter.setStyleEnum({ CELL_ANIMATION_TYPE }, type)
        }

    /**
     * Gets or sets whether adjacent month should be shown on the calendar page. By default, it's `true`.
     */
    var showAdjacentMonths: Boolean
        get() = adapter.getStyleBool { SHOW_ADJACENT_MONTHS }
        set(value) {
            adapter.setStyleBool({ SHOW_ADJACENT_MONTHS }, value)
        }

    /**
     * Gets or sets content description for the 'previous month' button.
     */
    var previousMonthButtonContentDescription: CharSequence
        get() = toolbarManager.prevMonthDescription
        set(value) {
            toolbarManager.prevMonthDescription = value
        }

    /**
     * Gets or sets content description for the 'next month' button.
     */
    var nextMonthButtonContentDescription: CharSequence
        get() = toolbarManager.nextMonthDescription
        set(value) {
            toolbarManager.nextMonthDescription = value
        }

    /**
     * Gets or sets content description for the 'clear selection' button.
     */
    var clearSelectionButtonContentDescription: CharSequence
        get() = toolbarManager.clearSelectionDescription
        set(value) {
            toolbarManager.clearSelectionDescription = value
        }

    /**
     * Gets or sets current [RangeCalendarCellAccessibilityInfoProvider] that is
     * responsible of providing accessibility-related information about cells in the calendar.
     */
    var cellAccessibilityInfoProvider: RangeCalendarCellAccessibilityInfoProvider
        get() = adapter.getStyleObject { CELL_ACCESSIBILITY_INFO_PROVIDER }
        set(value) {
            adapter.setStyleObject({ CELL_ACCESSIBILITY_INFO_PROVIDER }, value)
        }

    /**
     * Gets or sets whether selection animations is enabled by default.
     * There's some cases when it can't really be controlled to animate selection or not, for example, selection by user.
     * This property specifies whether to animate selection in such situations.
     *
     * It is also a default value of `withAnimation` parameter of 'select' methods: [selectDay], [selectWeek], [selectMonth], [selectRange]
     */
    var isSelectionAnimatedByDefault: Boolean
        get() = adapter.getStyleBool { IS_SELECTION_ANIMATED_BY_DEFAULT }
        set(value) {
            adapter.setStyleBool({ IS_SELECTION_ANIMATED_BY_DEFAULT }, value)
        }

    /**
     * Gets or sets whether hover animations is enabled.
     */
    var isHoverAnimationEnabled: Boolean
        get() = adapter.getStyleBool { IS_HOVER_ANIMATION_ENABLED }
        set(value) {
            adapter.setStyleBool({ IS_HOVER_ANIMATION_ENABLED }, value)
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
     * @param year  year, should be in range `[0; 65535]`
     * @param month month, 1-based
     * @param smoothScroll whether to do it with slide animation or not
     * @throws IllegalArgumentException if year and month are out of their valid ranges
     */
    @JvmOverloads
    fun setYearAndMonth(year: Int, month: Int, smoothScroll: Boolean = true) {
        validateYearMonth(year, month)

        setYearAndMonthInternal(YearMonth(year, month), smoothScroll)
    }

    private fun setYearAndMonthInternal(ym: YearMonth, smoothScroll: Boolean) {
        currentCalendarYm = ym

        val position = adapter.getItemPositionForYearMonth(ym)

        pager.setCurrentItem(position, smoothScroll)
        updateMoveButtons()
    }

    /**
     * Selects a date.
     *
     * @param date a date to be selected
     * @param selectionRequestRejectedBehaviour specifies what behaviour is expected when a selection request, sent by this method, is rejected
     * @param withAnimation whether to do it with animation or not. Default value is [isSelectionAnimatedByDefault]
     */
    @JvmOverloads
    fun selectDay(
        date: LocalDate,
        selectionRequestRejectedBehaviour: SelectionRequestRejectedBehaviour = SelectionRequestRejectedBehaviour.PRESERVE_CURRENT_SELECTION,
        withAnimation: Boolean = isSelectionAnimatedByDefault
    ) {
        // PackedDateRange.fromSingleDate will check whether date meets requirements
        selectRangeInternal(
            YearMonth(date.year, date.monthValue),
            PackedDateRange.fromSingleDate(date),
            selectionRequestRejectedBehaviour,
            withAnimation
        )
    }

    /**
     * Selects a week.
     *
     * @param year          year, should be in range `[0; 65535]`
     * @param month         month, 1-based
     * @param weekIndex     index of week, 0-based
     * @param selectionRequestRejectedBehaviour specifies what behaviour is expected when a selection request, sent by this method, is rejected
     * @param withAnimation whether to do it with animation or not. Default value is [isSelectionAnimatedByDefault]
     */
    @JvmOverloads
    fun selectWeek(
        year: Int, month: Int,
        weekIndex: Int,
        selectionRequestRejectedBehaviour: SelectionRequestRejectedBehaviour = SelectionRequestRejectedBehaviour.PRESERVE_CURRENT_SELECTION,
        withAnimation: Boolean = isSelectionAnimatedByDefault
    ) {
        validateYearMonth(year, month)
        require(weekIndex in 0 until 5) { "Invalid week index" }

        selectRangeInternal(
            ym = YearMonth(year, month),
            range = PackedDateRange.week(year, month, weekIndex),
            selectionRequestRejectedBehaviour,
            withAnimation
        )
    }

    /**
     * Selects a month.
     *
     * @param year          year, should be in range `[0; 65535]`
     * @param month         month, 1-based
     * @param selectionRequestRejectedBehaviour specifies what behaviour is expected when a selection request, sent by this method, is rejected
     * @param withAnimation whether to do it with animation or not. Default value is [isSelectionAnimatedByDefault]
     */
    @JvmOverloads
    fun selectMonth(
        year: Int, month: Int,
        selectionRequestRejectedBehaviour: SelectionRequestRejectedBehaviour = SelectionRequestRejectedBehaviour.PRESERVE_CURRENT_SELECTION,
        withAnimation: Boolean = isSelectionAnimatedByDefault
    ) {
        validateYearMonth(year, month)

        selectRangeInternal(
            YearMonth(year, month),
            range = PackedDateRange.month(year, month),
            selectionRequestRejectedBehaviour,
            withAnimation
        )
    }

    /**
     * Selects a date range.
     *
     * @param startDate start of the range, inclusive
     * @param endDate end of the range, inclusive
     * @param selectionRequestRejectedBehaviour specifies what behaviour is expected when a selection request, sent by this method, is rejected
     * @param withAnimation whether to do it with animation of not. Default value is [isSelectionAnimatedByDefault]
     */
    @JvmOverloads
    fun selectRange(
        startDate: LocalDate,
        endDate: LocalDate,
        selectionRequestRejectedBehaviour: SelectionRequestRejectedBehaviour = SelectionRequestRejectedBehaviour.PRESERVE_CURRENT_SELECTION,
        withAnimation: Boolean = isSelectionAnimatedByDefault
    ) {
        validateDateRange(startDate, endDate)

        selectRangeInternal(
            YearMonth(startDate.year, startDate.monthValue),
            PackedDateRange.fromLocalDates(startDate, endDate),
            selectionRequestRejectedBehaviour,
            withAnimation
        )
    }

    private fun selectRangeInternal(
        ym: YearMonth,
        range: PackedDateRange,
        requestRejectedBehaviour: SelectionRequestRejectedBehaviour,
        withAnimation: Boolean
    ) {
        val actuallySelected = adapter.selectRange(ym, range, requestRejectedBehaviour, withAnimation)

        if (actuallySelected) {
            val position = adapter.getItemPositionForYearMonth(ym)

            pager.setCurrentItem(position, withAnimation)
        }
    }

    /**
     * Clears a selection.
     *
     * @param withAnimation whether to clear selection with animation of not. Default value is [isSelectionAnimatedByDefault]
     */
    @JvmOverloads
    fun clearSelection(withAnimation: Boolean = isSelectionAnimatedByDefault) {
        adapter.clearSelection(withAnimation)
    }

    /**
     * Sets custom implementation of selection manager. If you want to use default one, pass null as an argument.
     */
    fun setSelectionManager(selectionManager: SelectionManager?) {
        adapter.setStyleObject({ SELECTION_MANAGER }, selectionManager)
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
        adapter.setStyleObject({ DECOR_DEFAULT_LAYOUT_OPTIONS }, options)
    }

    private fun updateMoveButtons() {
        val position = pager.currentItem
        val count = adapter.itemCount

        prevButton.isEnabled = position != 0
        nextOrClearButton.isEnabled = toolbarManager.isNextButtonActClear || position != count - 1
    }

    companion object {
        private val TAG = RangeCalendarView::class.java.simpleName

        private const val DEFAULT_INFO_FORMAT = "MMMM y"

        private const val INVALID_DURATION_MSG = "Duration should be non-negative"

        private fun validateDateRange(start: LocalDate, end: LocalDate) {
            require(start.year == end.year && start.monthValue == end.monthValue) { "Date range should have same year and month" }
            require(start <= end) { "Start date is greater than end date" }
        }

        private fun validateTextSize(value: Float) {
            require(value > 0) { "Text size should be positive" }
        }

        private fun validateAnimationDuration(value: Int) {
            require(value >= 0) { INVALID_DURATION_MSG }
        }

        private fun validateAnimationDuration(value: Long) {
            require(value >= 0) { INVALID_DURATION_MSG }
        }

        private fun validateYearMonth(year: Int, month: Int) {
            PackedDate.checkYear(year)
            PackedDate.checkMonth(month)
        }
    }
}