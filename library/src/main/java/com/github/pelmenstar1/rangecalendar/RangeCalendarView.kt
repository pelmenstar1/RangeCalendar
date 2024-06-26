package com.github.pelmenstar1.rangecalendar

import android.animation.TimeInterpolator
import android.content.Context
import android.content.res.Configuration
import android.content.res.TypedArray
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.os.Parcelable
import android.text.format.DateFormat
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
import com.github.pelmenstar1.rangecalendar.gesture.RangeCalendarGestureConfiguration
import com.github.pelmenstar1.rangecalendar.gesture.RangeCalendarGestureDetector
import com.github.pelmenstar1.rangecalendar.gesture.RangeCalendarGestureDetectorFactory
import com.github.pelmenstar1.rangecalendar.selection.CellAnimationType
import com.github.pelmenstar1.rangecalendar.selection.SelectionManager
import com.github.pelmenstar1.rangecalendar.utils.getBestDatePatternCompat
import com.github.pelmenstar1.rangecalendar.utils.getFirstDayOfWeek
import com.github.pelmenstar1.rangecalendar.utils.getLocaleCompat
import com.github.pelmenstar1.rangecalendar.utils.getSelectableItemBackground
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
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
        /**
         * Returns whether the selection range is allowed or not. The start date of the selection specified by [startYear], [startMonth], [startDay] is inclusive.
         * The end date specified by [endYear], [endMonth], [endDay] is inclusive as well, thus if a single cell is selected, the start date is equal to the end date.
         *
         * @param startYear year of the start date of the range
         * @param startMonth month of the start date of the range, 1-based
         * @param startDay year of the start date of the range, 1-based
         * @param endYear year of the end date of the range
         * @param endMonth month of the end date of the range, 1-based
         * @param endDay year of the end date of the range, 1-based
         */
        fun accept(
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
         * Fires when on date range selection. The start date of the selection specified by [startYear], [startMonth], [startDay] is inclusive.
         * End date specified by [endYear], [endMonth], [endDay] is inclusive as well, thus if a single cell is selected, the start date is equal to the end date.
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
        locale: Locale,
        pattern: String
    ) : LocalizedInfoFormatter {
        val dateFormatter = CompatDateFormatter(locale, pattern)

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

    /**
     * Gets the [TextView] instance on which information about year and month of selected page is shown.
     *
     * In general, you can change any of its properties except the text. It will be overwritten any time it's necessary, for example, when the page is changed.
     * If you want to customize the text, use [infoFormatter].
     *
     * There are some properties that you may change but it's undesirable:
     * - On-click-listener. If you change it, clicks on the view won't select current month.
     * Though, it can be implemented even without the library support by using [selectMonth] and [selectedCalendarYear], [selectedCalendarMonth].
     * - Translation Y. It's used for 'selection view' transitions. Changing it to a custom value may lead to unexpected results if [selectionView] is used.
     *
     * The [RangeCalendarView] uses a custom layout to reduce usage of additional containers. Layout params of the [infoTextView] doesn't affect the layout.
     */
    val infoTextView: TextView

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
    private val topContainerMarginBottom: Int

    private val toolbarManager: CalendarToolbarManager

    private var isCustomFirstDayOfWeek = false
    private var _firstDayOfWeek = CompatDayOfWeek.Monday

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

        val currentTimeZone = TimeZone.getDefault()
        _timeZone = currentTimeZone

        val locale = context.getLocaleCompat()

        val today = PackedDate.today(currentTimeZone)

        adapter = RangeCalendarPagerAdapter(cr).apply {
            setToday(today)
            setStyleObject(
                { CELL_ACCESSIBILITY_INFO_PROVIDER },
                DefaultRangeCalendarCellAccessibilityInfoProvider(locale),
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

        infoTextView = AppCompatTextView(context).apply {
            setTextColor(cr.textColor)
            setOnClickListener {
                val (year, month) = currentCalendarYm

                selectMonth(year, month)
            }
        }

        toolbarManager = CalendarToolbarManager(
            context,
            cr.colorControlNormal,
            prevButton, nextOrClearButton, infoTextView
        )

        pager = ViewPager2(context).apply {
            adapter = this@RangeCalendarView.adapter

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
        addView(infoTextView)

        // It should be called after the toolbarManager is initialized.
        initLocaleDependentValues(locale)

        _infoPattern = getBestDatePatternCompat(locale, DEFAULT_INFO_FORMAT)
        _infoFormatter = DefaultLocalizedInfoFormatter(locale, _infoPattern)

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

                int(R.styleable.RangeCalendarView_rangeCalendar_weekdayType) { WEEKDAY_TYPE }
                int(R.styleable.RangeCalendarView_rangeCalendar_clickOnCellSelectionBehavior) { CLICK_ON_CELL_SELECTION_BEHAVIOR }

                dimension(R.styleable.RangeCalendarView_rangeCalendar_dayNumberTextSize) { DAY_NUMBER_TEXT_SIZE }
                dimension(R.styleable.RangeCalendarView_rangeCalendar_weekdayTextSize) { WEEKDAY_TEXT_SIZE }
                dimension(R.styleable.RangeCalendarView_rangeCalendar_cellRoundRadius) { CELL_ROUND_RADIUS }

                int(R.styleable.RangeCalendarView_rangeCalendar_selectionFillGradientBoundsType) { SELECTION_FILL_GRADIENT_BOUNDS_TYPE }
                int(R.styleable.RangeCalendarView_rangeCalendar_cellAnimationType) { CELL_ANIMATION_TYPE }

                boolean(R.styleable.RangeCalendarView_rangeCalendar_showAdjacentMonths) { SHOW_ADJACENT_MONTHS }
                boolean(R.styleable.RangeCalendarView_rangeCalendar_vibrateOnSelectingCustomRange) { VIBRATE_ON_SELECTING_RANGE }
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

                a.getDimension(R.styleable.RangeCalendarView_rangeCalendar_infoTextSize, Float.NaN).also {
                    if (!it.isNaN()) {
                        infoTextSize = it
                    }
                }

                a.getInt(R.styleable.RangeCalendarView_rangeCalendar_firstDayOfWeek, -1).also {
                    if (it >= 0) {
                        val firstDow = CompatDayOfWeek(it)

                        setFirstDayOfWeekInternal(firstDow, isCustom = true)
                    }
                }

                a.getFloat(R.styleable.RangeCalendarView_rangeCalendar_hoverAlpha, Float.NaN).also {
                    if (!it.isNaN()) {
                        hoverAlpha = it
                    }
                }
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

    private fun initLocaleDependentValues(locale: Locale) {
        // infoFormatter is initialized on creation. No need in double creating the underlying models.
        refreshLocaleDependentValues(
            newLocale = locale,
            updateInfoFormatter = false
        )
    }

    private fun refreshLocaleDependentValues(newLocale: Locale, updateInfoFormatter: Boolean) {
        currentLocale = newLocale

        if (!isCustomFirstDayOfWeek) {
            val firstDow = newLocale.getFirstDayOfWeek()

            setFirstDayOfWeekInternal(firstDow, isCustom = false)
        }

        if (updateInfoFormatter) {
            _infoFormatter.let {
                if (it is LocalizedInfoFormatter) {
                    it.onLocaleChanged(newLocale)

                    // If format is the default one, we should call updateInfoPattern() as it finds best-pattern if
                    // useBestPatternForInfoPattern is true. Otherwise, simply update infoTextView's text by using
                    // onInfoFormatterOptionsChanged() as changing locale probably changes the text.
                    if (it is DefaultLocalizedInfoFormatter) {
                        updateInfoPattern()
                    } else {
                        onInfoFormatterOptionsChanged()
                    }
                }
            }
        }

        toolbarManager.onLocaleChanged()
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
            selectionRange = adapter.selectedRange
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

            val selRange = state.selectionRange

            if (selRange.isValid) {
                adapter.selectRange(
                    selRange,
                    requestRejectedBehaviour = SelectionRequestRejectedBehaviour.PRESERVE_CURRENT_SELECTION,
                    withAnimation = false
                )
            }
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        pager.measure(widthMeasureSpec, heightMeasureSpec)

        val pagerWidth = pager.measuredWidth
        val buttonSize = buttonSize

        val maxInfoWidth = pagerWidth - 2 * buttonSize

        infoTextView.measure(
            MeasureSpec.makeMeasureSpec(maxInfoWidth, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )

        val buttonSpec = MeasureSpec.makeMeasureSpec(buttonSize, MeasureSpec.EXACTLY)

        prevButton.measure(buttonSpec, buttonSpec)
        nextOrClearButton.measure(buttonSpec, buttonSpec)

        val toolbarHeight = max(infoTextView.measuredHeight, buttonSize)

        toolbarManager.selectionView?.also { sv ->
            var maxWidth = pagerWidth - buttonSize
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

        val buttonSize = buttonSize

        val toolbarManager = toolbarManager
        val nextLeft = width - buttonSize

        val infoWidth = infoTextView.measuredWidth
        val infoHeight = infoTextView.measuredHeight

        val toolbarHeight = max(buttonSize, infoHeight)

        val infoLeft = (width - infoWidth) / 2
        val infoTop = (toolbarHeight - infoHeight) / 2
        val buttonTop = (toolbarHeight - buttonSize) / 2
        val buttonBottom = buttonTop + buttonSize
        val pagerTop = toolbarHeight + topContainerMarginBottom

        prevButton.layout(0, buttonTop, buttonSize, buttonBottom)
        nextOrClearButton.layout(nextLeft, buttonTop, nextLeft + buttonSize, buttonBottom)
        infoTextView.layout(infoLeft, infoTop, infoLeft + infoWidth, infoTop + infoHeight)

        pager.layout(0, pagerTop, pager.measuredWidth, pagerTop + pager.measuredHeight)

        toolbarManager.selectionView?.also { sv ->
            val lr = layoutRect
            val lrOut = layoutOutRect

            val svLayoutParams = toolbarManager.selectionViewLayoutParams
            val gravity = svLayoutParams.gravity

            // lr's top is always 0
            lr.bottom = toolbarHeight

            // Detection of whether the gravity is center_horizontal is a little bit complicated.
            // Basically we need to check whether bits AXIS_PULL_BEFORE and AXIS_PULL_AFTER bits are 0.
            val isCenterHorizontal =
                gravity and ((Gravity.AXIS_PULL_BEFORE or Gravity.AXIS_PULL_AFTER) shl Gravity.AXIS_X_SHIFT) == 0

            // If the gravity on x-axis is center, let the view be centered along the whole
            // calendar view (except padding).
            if (isCenterHorizontal) {
                lr.left = 0
                lr.right = width
            } else {
                lr.left = if (toolbarManager.hasSelectionViewClearButton) 0 else buttonSize
                lr.right = nextLeft
            }

            val absGravity = Gravity.getAbsoluteGravity(gravity, layoutDirection)
            Gravity.apply(absGravity, sv.measuredWidth, sv.measuredHeight, lr, lrOut)

            sv.layout(lrOut.left, lrOut.top, lrOut.right, lrOut.bottom)
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
     * The pattern is affected by [useBestPatternForInfoPattern] that specifies whether [DateFormat.getBestDateTimePattern] should be called on given pattern.
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

            updateInfoPattern()
        }

    /**
     * Sets [infoFormatter] to the default one.
     */
    fun setDefaultInfoFormatter() {
        infoFormatter = DefaultLocalizedInfoFormatter(currentLocale!!, _infoPattern)
    }

    private fun updateInfoPattern() {
        setInfoPatternInternal(originInfoFormat)
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
        infoTextView.text = _infoFormatter.format(ym.year, ym.month)
    }

    /**
     * Gets or sets text size (in pixels) of [infoTextView].
     */
    var infoTextSize: Float
        get() = infoTextView.textSize
        set(value) {
            infoTextView.textSize = value
        }

    /**
     * Gets or sets typeface of [infoTextView].
     */
    var infoTypeface: Typeface?
        get() = infoTextView.typeface
        set(value) {
            infoTextView.typeface = value
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
            setMinDate(PackedDate.fromLocalDate(value))
        }

    /**
     * Sets minimum date. The minimum date should be before or equal to current maximum date.
     *
     * @param year year of the date, should be in range `[0..65535]`
     * @param month month of the date, 1-based
     * @param dayOfMonth day of the month of the date, 1-based
     */
    fun setMinDate(year: Int, month: Int, dayOfMonth: Int) {
        setMinDate(PackedDate(year, month, dayOfMonth))
    }

    /**
     * Sets minimum date using [Calendar] instance. The minimum date should be before or equal to current maximum date.
     * Year of the calendar is expected to be in range `[0..65535]`
     */
    fun setMinDate(calendar: Calendar) {
        setMinDate(PackedDate.fromCalendar(calendar))
    }

    /**
     * Sets current minimum date to the specified [calendar].
     * Only year, month, and day of month properties are changed.
     */
    fun getMinDate(calendar: Calendar) {
        _minDate.toCalendar(calendar)
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
            setMaxDate(PackedDate.fromLocalDate(value))
        }

    /**
     * Sets maximum date. The minimum date should be before or equal to current maximum date.
     *
     * @param year year of the date, should be in range `[0..65535]`
     * @param month month of the date, 1-based
     * @param dayOfMonth day of the month of the date, 1-based
     */
    fun setMaxDate(year: Int, month: Int, dayOfMonth: Int) {
        setMaxDate(PackedDate(year, month, dayOfMonth))
    }

    /**
     * Sets maximum date using [Calendar] instance. The maximum date should be after or equal to current minimum date.
     * Year of the calendar is expected to be in range `[0..65535]`
     */
    fun setMaxDate(calendar: Calendar) {
        setMaxDate(PackedDate.fromCalendar(calendar))
    }

    /**
     * Sets current maximum date to the specified [calendar].
     * Only year, month, and day of month properties are changed.
     */
    fun getMaxDate(calendar: Calendar) {
        _maxDate.toCalendar(calendar)
    }

    /**
     * Sets calendar's minimum and maximum dates. [minDate] should be before or equal to [maxDate].
     * Year of the both dates should be in range `[0..65535]`.
     *
     * This is more efficient than two calls to [setMinDate] and [setMaxDate].
     */
    fun setMinMaxDate(minDate: LocalDate, maxDate: LocalDate) {
        setMinMaxDate(PackedDate.fromLocalDate(minDate), PackedDate.fromLocalDate(maxDate))
    }

    /**
     * Sets calendar's minimum and maximum dates using [minDate] and [maxDate] calendars respectively.
     * [minDate] should be before or equal to [maxDate].
     * Year of the both dates should be in range `[0..65535]`.
     *
     * This is more efficient than two calls to [setMinDate] and [setMaxDate].
     */
    fun setMinMaxDate(minDate: Calendar, maxDate: Calendar) {
        setMinMaxDate(PackedDate.fromCalendar(minDate), PackedDate.fromCalendar(maxDate))
    }

    /**
     * Sets calendar's minimum and maximum dates.
     * Minimum date, specified by [minYear], [minMonth], [minDay], should be before or equal to the maximum date, specified by [minYear], [minMonth], [minDay].
     *
     * This is more efficient than two calls to [setMinDate] and [setMaxDate].
     *
     * @param minYear year of the minimum date, it should be in range `[0..65535]`
     * @param minMonth month of the minimum date, 1-based
     * @param minDay day of the month of the minimum date, 1-based
     * @param maxYear year of the maximum date, it should be in range `[0..65535]`
     * @param maxMonth month of the maximum date, 1-based
     * @param maxDay day of the month of the maximum date, 1-based
     */
    fun setMinMaxDate(
        minYear: Int, minMonth: Int, minDay: Int,
        maxYear: Int, maxMonth: Int, maxDay: Int,
    ) {
        setMinMaxDate(PackedDate(minYear, minMonth, minDay), PackedDate(maxYear, maxMonth, maxDay))
    }

    private fun setMinDate(date: PackedDate) {
        validateMinMaxDateRange(date, _maxDate)

        _minDate = date
        onMinMaxChanged()
    }

    private fun setMaxDate(date: PackedDate) {
        validateMinMaxDateRange(_minDate, date)

        _maxDate = date
        onMinMaxChanged()
    }

    private fun setMinMaxDate(newMinDate: PackedDate, newMaxDate: PackedDate) {
        validateMinMaxDateRange(newMinDate, newMaxDate)

        _minDate = newMinDate
        _maxDate = newMaxDate

        onMinMaxChanged()
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
     * Gets or sets an alpha value of selection or its part that is not inside current month's range.
     * The alpha is a float in range `[0..1]`. To pass integer alpha in range `[0..255]`, divide the value by `255`
     *
     * This can be used to accent the selection or its part is not for the current month but for the adjacent one.
     * The default value is `1`, which means selection on all grid is rendered with the same alpha.
     *
     * **Drawable fills are currently not supported**
     */
    var outMonthSelectionAlpha: Float
        get() = adapter.getStyleFloat { OUT_MONTH_SELECTION_ALPHA }
        set(value) {
            validateFloatAlpha(value)

            adapter.setStyleFloat({ OUT_MONTH_SELECTION_ALPHA }, value)
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
     * Gets or sets the [Border] instance, that specifies a line around the selection shape.
     * If `null`, it means that the selection shape has no border (the default behaviour).
     */
    var selectionBorder: Border?
        get() = adapter.getStyleObject { SELECTION_BORDER }
        set(value) {
            adapter.setStyleObject({ SELECTION_BORDER }, value)
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
     * Pass `null` to use localized weekdays.
     */
    var weekdays: Array<out String>?
        get() = adapter.getStyleObject { WEEKDAYS }
        set(value) {
            if (value != null && value.size != GridConstants.COLUMN_COUNT) {
                throw IllegalArgumentException("Length of the array should be ${GridConstants.COLUMN_COUNT}")
            }

            // Copy the array because the caller might be it later.
            adapter.setStyleObject({ WEEKDAYS }, value?.copyOf())
        }

    /**
     * Gets or sets first day of week using [DayOfWeek] enum.
     *
     * By default, information about first day of week is extracted from current locale's data. If locale is changed, first day of week is re-computed.
     * However, if you change value of the property (even to the same value), first day of week will no longer be updated from locale's data.
     * It will also clear selection, if one is present.
     */
    var firstDayOfWeek: DayOfWeek
        get() = _firstDayOfWeek.toEnumValue()
        set(value) {
            setFirstDayOfWeekInternal(CompatDayOfWeek.fromEnumValue(value), isCustom = true)
        }

    /**
     * Gets or sets first day of week using int values from [Calendar]. For example, [Calendar.MONDAY] or [Calendar.SUNDAY]
     *
     * By default, information about first day of week is extracted from current locale's data. If locale is changed, first day of week is re-computed.
     * However, if you change value of the property (even to the same value), first day of week will no longer be updated from locale's data.
     * It will also clear selection, if one is present.
     */
    var intFirstDayOfWeek: Int
        get() = _firstDayOfWeek.toCalendarValue()
        set(value) {
            if (value !in Calendar.SUNDAY..Calendar.SATURDAY) {
                throw IllegalArgumentException("Invalid day of week")
            }

            setFirstDayOfWeekInternal(CompatDayOfWeek.fromCalendarDayOfWeek(value), isCustom = true)
        }

    private fun setFirstDayOfWeekInternal(value: CompatDayOfWeek, isCustom: Boolean) {
        _firstDayOfWeek = value
        isCustomFirstDayOfWeek = isCustom

        adapter.setFirstDayOfWeek(_firstDayOfWeek)
    }

    /**
     * Gets or sets alpha channel value of a black color that is drawn under the cell when the cell is in hovered state.
     * The alpha is represented as a float in range `0..1`.
     */
    var hoverAlpha: Float
        get() = adapter.getStyleFloat { HOVER_ALPHA }
        set(value) {
            validateFloatAlpha(value)

            adapter.setStyleFloat({ HOVER_ALPHA }, value)
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
     * Changing weekday type when custom weekdays array was set (via [weekdays]) won't have any effect. Though, when
     * `null` value is passed to the setter of [weekdays], the latest value of [weekdayType] will be used.
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
     * Gets or sets current gesture detector factory.
     *
     * Changing the factory causes creation of [RangeCalendarGestureDetector] instances on all available calendar pages.
     * All the pages will use these gesture detectors.
     */
    var gestureDetectorFactory: RangeCalendarGestureDetectorFactory
        get() = adapter.getStyleObject { GESTURE_DETECTOR_FACTORY }
        set(value) {
            adapter.setStyleObject({ GESTURE_DETECTOR_FACTORY }, value)
        }

    /**
     * Gets or sets current configuration for gesture detectors.
     */
    var gestureConfiguration: RangeCalendarGestureConfiguration
        get() = adapter.getStyleObject { GESTURE_CONFIGURATION }
        set(value) {
            adapter.setStyleObject({ GESTURE_CONFIGURATION }, value)
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
        selectDayInternal(PackedDate.fromLocalDate(date), selectionRequestRejectedBehaviour, withAnimation)
    }

    /**
     * Selects a date.
     *
     * @param calendar specifies a date to be selected
     * @param selectionRequestRejectedBehaviour specifies what behaviour is expected when a selection request, sent by this method, is rejected
     * @param withAnimation whether to do it with animation or not. Default value is [isSelectionAnimatedByDefault]
     */
    @JvmOverloads
    fun selectDay(
        calendar: Calendar,
        selectionRequestRejectedBehaviour: SelectionRequestRejectedBehaviour = SelectionRequestRejectedBehaviour.PRESERVE_CURRENT_SELECTION,
        withAnimation: Boolean = isSelectionAnimatedByDefault
    ) {
        selectDayInternal(PackedDate.fromCalendar(calendar), selectionRequestRejectedBehaviour, withAnimation)
    }

    /**
     * Selects a date.
     *
     * @param year year of the date to be selected, should be in range `[0; 65535]`
     * @param month month of the date, 1-based
     * @param dayOfMonth day of the month of the date, 1-based.
     * @param selectionRequestRejectedBehaviour specifies what behaviour is expected when a selection request, sent by this method, is rejected
     * @param withAnimation whether to do it with animation or not. Default value is [isSelectionAnimatedByDefault]
     */
    @JvmOverloads
    fun selectDay(
        year: Int, month: Int, dayOfMonth: Int,
        selectionRequestRejectedBehaviour: SelectionRequestRejectedBehaviour = SelectionRequestRejectedBehaviour.PRESERVE_CURRENT_SELECTION,
        withAnimation: Boolean = isSelectionAnimatedByDefault
    ) {
        selectDayInternal(PackedDate(year, month, dayOfMonth), selectionRequestRejectedBehaviour, withAnimation)
    }

    private fun selectDayInternal(
        date: PackedDate,
        selectionRequestRejectedBehaviour: SelectionRequestRejectedBehaviour,
        withAnimation: Boolean
    ) {
        selectRangeInternal(
            PackedDateRange(date, date),
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
        require(weekIndex in 0..5) { "Invalid week index" }

        selectRangeInternal(
            PackedDateRange.week(year, month, weekIndex, _firstDayOfWeek),
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
            PackedDateRange.month(year, month),
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
        selectedCustomRangeInternal(
            PackedDate.fromLocalDate(startDate),
            PackedDate.fromLocalDate(endDate),
            selectionRequestRejectedBehaviour,
            withAnimation
        )
    }

    /**
     * Selects a date range.
     *
     * @param start start of the range, inclusive
     * @param end end of the range, inclusive
     * @param selectionRequestRejectedBehaviour specifies what behaviour is expected when a selection request, sent by this method, is rejected
     * @param withAnimation whether to do it with animation of not. Default value is [isSelectionAnimatedByDefault]
     */
    @JvmOverloads
    fun selectRange(
        start: Calendar,
        end: Calendar,
        selectionRequestRejectedBehaviour: SelectionRequestRejectedBehaviour = SelectionRequestRejectedBehaviour.PRESERVE_CURRENT_SELECTION,
        withAnimation: Boolean = isSelectionAnimatedByDefault
    ) {
        selectedCustomRangeInternal(
            PackedDate.fromCalendar(start),
            PackedDate.fromCalendar(end),
            selectionRequestRejectedBehaviour,
            withAnimation
        )
    }

    /**
     * Selects a date range.
     *
     * @param startYear year of the start of the range
     * @param startMonth month of the start of the range, 1-based
     * @param startDay day of the month of the start of the range, 1-based
     * @param endYear year of the end of the range
     * @param endMonth month of the end of the range, 1-based
     * @param endDay day of the month of the end of the range, 1-based
     * @param selectionRequestRejectedBehaviour specifies what behaviour is expected when a selection request, sent by this method, is rejected
     * @param withAnimation whether to do it with animation of not. Default value is [isSelectionAnimatedByDefault]
     */
    @JvmOverloads
    fun selectRange(
        startYear: Int, startMonth: Int, startDay: Int,
        endYear: Int, endMonth: Int, endDay: Int,
        selectionRequestRejectedBehaviour: SelectionRequestRejectedBehaviour = SelectionRequestRejectedBehaviour.PRESERVE_CURRENT_SELECTION,
        withAnimation: Boolean = isSelectionAnimatedByDefault
    ) {
        selectedCustomRangeInternal(
            PackedDate(startYear, startMonth, startDay),
            PackedDate(endYear, endMonth, endDay),
            selectionRequestRejectedBehaviour,
            withAnimation
        )
    }

    private fun selectedCustomRangeInternal(
        startDate: PackedDate,
        endDate: PackedDate,
        requestRejectedBehaviour: SelectionRequestRejectedBehaviour,
        withAnimation: Boolean
    ) {
        if (startDate > endDate) {
            throw IllegalArgumentException("Start date cannot be after end date")
        }

        selectRangeInternal(
            PackedDateRange(startDate, endDate),
            requestRejectedBehaviour,
            withAnimation
        )
    }

    private fun selectRangeInternal(
        range: PackedDateRange,
        requestRejectedBehaviour: SelectionRequestRejectedBehaviour,
        withAnimation: Boolean
    ) {
        val actuallySelected = adapter.selectRange(range, requestRejectedBehaviour, withAnimation)

        if (actuallySelected) {
            val position = adapter.getItemPositionForDate(range.start)

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
     * Adds multiple decorations to the cell. Note that, only one type of decorations can exist in one cell.
     *
     * @param decors array of decorations to be added. The array should not be empty.
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
     * Inserts multiple decorations to the cell. Note that, only one type of decorations can exist in one cell.
     *
     * @param indexInCell decoration index in specified cell at which insert decorations
     * @param decors array of decorations to be added. The array should not be empty.
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

        private const val DEFAULT_INFO_FORMAT = "LLLL y"

        private const val INVALID_DURATION_MSG = "Duration should be non-negative"

        private fun validateFloatAlpha(alpha: Float) {
            require(alpha in 0f..1f) { "Alpha value should be in range [0, 1]" }
        }

        private fun validateMinMaxDateRange(minDate: PackedDate, maxDate: PackedDate) {
            if (minDate > maxDate) {
                throw IllegalArgumentException("Maximum date cannot be before minimum date")
            }
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