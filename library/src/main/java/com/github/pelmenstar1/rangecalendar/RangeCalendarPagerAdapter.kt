package com.github.pelmenstar1.rangecalendar

import android.util.SparseArray
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.pelmenstar1.rangecalendar.decoration.CellDecor
import com.github.pelmenstar1.rangecalendar.decoration.DecorAnimationFractionInterpolator
import com.github.pelmenstar1.rangecalendar.decoration.DecorGroupedList
import com.github.pelmenstar1.rangecalendar.decoration.DecorLayoutOptions
import com.github.pelmenstar1.rangecalendar.selection.*

internal class RangeCalendarPagerAdapter(
    private val cr: CalendarResources,
    private val isFirstDaySunday: Boolean
) : RecyclerView.Adapter<RangeCalendarPagerAdapter.ViewHolder>() {
    class ViewHolder(val calendar: RangeCalendarGridView) : RecyclerView.ViewHolder(calendar)

    private class CalendarInfo {
        var ym = YearMonth(0)
        var start = 0
        var daysInMonth = 0

        fun set(year: Int, month: Int) {
            this.ym = YearMonth(year, month)

            setInternal(year, month)
        }

        fun set(ym: YearMonth) {
            this.ym = ym

            setInternal(ym.year, ym.month)
        }

        private fun setInternal(year: Int, month: Int) {
            daysInMonth = TimeUtils.getDaysInMonth(year, month)
            start = PackedDate(year, month, 1).dayOfWeek - 1
        }
    }

    private class Payload(
        val type: Int,
        val arg1: Long = 0L,
        val arg2: Long = 0L,
        val arg3: Long = 0L,
        val obj1: Any? = null,
        val obj2: Any? = null,
    ) {
        companion object {
            const val UPDATE_ENABLED_RANGE = 0
            const val SELECT = 1
            const val UPDATE_TODAY_INDEX = 2
            const val UPDATE_STYLE = 3
            const val UPDATE_CELL_SIZE = 4
            const val CLEAR_HOVER = 5
            const val CLEAR_SELECTION = 6
            const val ON_DECOR_ADDED = 7
            const val ON_DECOR_REMOVED = 8
            const val SET_DECOR_LAYOUT_OPTIONS = 9

            private val CLEAR_HOVER_PAYLOAD = Payload(CLEAR_HOVER)
            private val UPDATE_ENABLED_RANGE_PAYLOAD = Payload(UPDATE_ENABLED_RANGE)
            private val UPDATE_TODAY_INDEX_PAYLOAD = Payload(UPDATE_TODAY_INDEX)
            private val SELECT_PAYLOAD = Payload(SELECT)

            fun clearHover() = CLEAR_HOVER_PAYLOAD

            fun updateEnabledRange() = UPDATE_ENABLED_RANGE_PAYLOAD
            fun updateTodayIndex() = UPDATE_TODAY_INDEX_PAYLOAD

            fun updateStyle(type: Int, data: PackedInt): Payload {
                return Payload(UPDATE_STYLE, arg1 = type.toLong(), arg2 = data.value.toLong())
            }

            fun updateStyle(type: Int, obj: Any?): Payload {
                return Payload(UPDATE_STYLE, type.toLong(), arg2 = 0, obj1 = obj)
            }

            fun updateCellSize(valueBits: Int): Payload {
                return Payload(UPDATE_CELL_SIZE, arg1 = valueBits.toLong())
            }

            fun clearSelection(withAnimation: Boolean): Payload {
                return Payload(CLEAR_SELECTION, arg1 = if (withAnimation) 1 else 0)
            }

            fun select(
                range: CellRange,
                requestRejectedBehaviour: SelectionRequestRejectedBehaviour,
                withAnimation: Boolean
            ): Payload {
                return Payload(
                    type = SELECT,
                    arg1 = range.bits.toLong(),
                    arg2 = requestRejectedBehaviour.ordinal.toLong(),
                    arg3 = if (withAnimation) 1 else 0
                )
            }

            fun onDecorAdded(
                newDecorRange: PackedIntRange,
                affectedRange: PackedIntRange,
                fractionInterpolator: DecorAnimationFractionInterpolator?
            ): Payload {
                return Payload(
                    ON_DECOR_ADDED,
                    arg1 = newDecorRange.bits,
                    arg2 = affectedRange.bits,
                    obj1 = fractionInterpolator
                )
            }

            fun onDecorRemoved(
                newDecorRange: PackedIntRange,
                affectedRange: PackedIntRange,
                cell: Cell,
                visual: CellDecor.Visual,
                fractionInterpolator: DecorAnimationFractionInterpolator?
            ): Payload {
                return Payload(
                    ON_DECOR_REMOVED,
                    arg1 = newDecorRange.bits,
                    arg2 = affectedRange.bits,
                    arg3 = cell.index.toLong(),
                    obj1 = fractionInterpolator,
                    obj2 = visual
                )
            }

            fun setDecorLayoutOptions(
                cell: Cell,
                options: DecorLayoutOptions,
                withAnimation: Boolean
            ): Payload {
                return Payload(
                    SET_DECOR_LAYOUT_OPTIONS,
                    arg1 = cell.index.toLong(),
                    arg2 = if (withAnimation) 1 else 0,
                    obj1 = options
                )
            }
        }
    }

    private var count = PAGES_BETWEEN_ABS_MIN_MAX

    private var minDate = PackedDate.MIN_DATE
    private var maxDate = PackedDate.MAX_DATE
    private var minDateEpoch = PackedDate.MIN_DATE_EPOCH
    private var maxDateEpoch = PackedDate.MAX_DATE_EPOCH

    var selectionRange = CellRange.Invalid

    // We need to save year-month of selection despite the fact we can compute it from selectionData
    // because computed position will point to the page where the position is in currentMonthRange
    // and this can lead to bugs when we mutate the wrong page.
    var selectionYm = YearMonth(0)

    // used in tests
    internal var today = PackedDate(0)

    private val calendarInfo = CalendarInfo()
    private val styleData = IntArray(20)
    private val styleObjData = arrayOfNulls<Any>(7)

    private var onSelectionListener: RangeCalendarView.OnSelectionListener? = null
    private var selectionGate: RangeCalendarView.SelectionGate? = null

    private val decorations = DecorGroupedList()
    private val decorLayoutOptionsMap = SparseArray<DecorLayoutOptions>()

    init {
        // colors
        initStyle(STYLE_IN_MONTH_TEXT_COLOR, cr.textColor)
        initStyle(STYLE_OUT_MONTH_TEXT_COLOR, cr.outMonthTextColor)
        initStyle(STYLE_DISABLED_TEXT_COLOR, cr.disabledTextColor)
        initStyle(STYLE_TODAY_TEXT_COLOR, cr.colorPrimary)
        initStyle(STYLE_WEEKDAY_TEXT_COLOR, cr.textColor)
        initStyle(STYLE_HOVER_COLOR, cr.hoverColor)
        initStyle(STYLE_HOVER_ON_SELECTION_COLOR, cr.colorPrimaryDark)

        // sizes
        initStyle(STYLE_DAY_NUMBER_TEXT_SIZE, cr.dayNumberTextSize)
        initStyle(STYLE_WEEKDAY_TEXT_SIZE, cr.weekdayTextSize)
        initStyle(STYLE_WEEKDAY_TYPE, WeekdayType.SHORT)
        initStyle(STYLE_CELL_RR_RADIUS, Float.POSITIVE_INFINITY)
        initStyle(STYLE_CELL_WIDTH, cr.cellSize)
        initStyle(STYLE_CELL_HEIGHT, cr.cellSize)
        initStyle(STYLE_CLICK_ON_CELL_SELECTION_BEHAVIOR, ClickOnCellSelectionBehavior.NONE)

        // animations
        initStyle(
            STYLE_COMMON_ANIMATION_DURATION,
            RangeCalendarGridView.DEFAULT_COMMON_ANIM_DURATION
        )
        initStyle(
            STYLE_SELECTION_FILL_GRADIENT_BOUNDS_TYPE,
            SelectionFillGradientBoundsType.GRID.ordinal
        )

        initStyle(STYLE_COMMON_ANIMATION_INTERPOLATOR, LINEAR_INTERPOLATOR)
        initStyle(STYLE_HOVER_ANIMATION_DURATION, RangeCalendarGridView.DEFAULT_HOVER_ANIM_DURATION)
        initStyle(STYLE_CELL_ANIMATION_TYPE, CellAnimationType.ALPHA)
        initStyle(STYLE_HOVER_ANIMATION_INTERPOLATOR, LINEAR_INTERPOLATOR)

        // selection
        initStyle(STYLE_SELECTION_FILL, Fill.solid(cr.colorPrimary))
        initStyle(STYLE_SELECTION_MANAGER, DefaultSelectionManager())

        // other stuff
        initStyle(STYLE_VIBRATE_ON_SELECTING_CUSTOM_RANGE, true)
        initStyle(STYLE_SHOW_ADJACENT_MONTHS, true)
    }

    private fun initStyle(type: Int, data: Boolean) {
        styleData[type] = if (data) 1 else 0
    }

    private fun initStyle(type: Int, data: Int) {
        styleData[type] = data
    }

    private fun initStyle(type: Int, data: Float) {
        initStyle(type, data.toBits())
    }

    private fun <T : Enum<T>> initStyle(type: Int, data: T) {
        initStyle(type, data.ordinal)
    }

    private fun initStyle(type: Int, data: Any) {
        styleObjData[type - STYLE_OBJ_START] = data
    }

    fun setOnSelectionListener(value: RangeCalendarView.OnSelectionListener) {
        onSelectionListener = value
    }

    fun setSelectionGate(value: RangeCalendarView.SelectionGate) {
        selectionGate = value
    }

    fun setToday(date: PackedDate) {
        today = date

        val position = getItemPositionForDate(date)

        if (position in 0 until count) {
            notifyItemChanged(position, Payload.updateTodayIndex())
        }
    }

    private fun getStylePacked(type: Int) = PackedInt(styleData[type])

    fun getStyleInt(type: Int) = styleData[type]
    fun getStyleBool(type: Int) = getStylePacked(type).boolean()
    fun getStyleFloat(type: Int) = getStylePacked(type).float()

    @Suppress("UNCHECKED_CAST")
    fun <T> getStyleObject(type: Int): T {
        return styleObjData[type - STYLE_OBJ_START] as T
    }

    inline fun <T : Enum<T>> getStyleEnum(getType: Companion.() -> Int, fromInt: (Int) -> T) =
        getStylePacked(Companion.getType()).enum(fromInt)

    inline fun getStyleInt(getType: Companion.() -> Int) = getStyleInt(Companion.getType())
    inline fun getStyleBool(getType: Companion.() -> Int) = getStyleBool(Companion.getType())
    inline fun getStyleFloat(getType: Companion.() -> Int) = getStyleFloat(Companion.getType())
    inline fun <T> getStyleObject(getType: Companion.() -> Int): T =
        getStyleObject(Companion.getType())

    private fun setStylePacked(type: Int, packed: PackedInt, notify: Boolean) {
        if (styleData[type] != packed.value) {
            styleData[type] = packed.value

            if (notify) {
                notifyItemRangeChanged(0, count, Payload.updateStyle(type, packed))
            }
        }
    }

    fun setStyleInt(type: Int, value: Int, notify: Boolean = true) {
        setStylePacked(type, PackedInt(value), notify)
    }

    fun <T : Enum<T>> setStyleEnum(type: Int, value: T, notify: Boolean = true) {
        setStylePacked(type, PackedInt(value), notify)
    }

    fun setStyleBool(type: Int, value: Boolean, notify: Boolean = true) {
        setStylePacked(type, PackedInt(value), notify)
    }

    fun setStyleFloat(type: Int, value: Float, notify: Boolean = true) {
        setStylePacked(type, PackedInt(value), notify)
    }

    fun setStyleObject(type: Int, data: Any?, notify: Boolean = true) {
        styleObjData[type - STYLE_OBJ_START] = data

        if (notify) {
            notifyItemRangeChanged(0, count, Payload.updateStyle(type, data))
        }
    }

    inline fun setStyleInt(getType: Companion.() -> Int, value: Int, notify: Boolean = true) {
        setStyleInt(Companion.getType(), value, notify)
    }

    inline fun setStyleFloat(getType: Companion.() -> Int, value: Float, notify: Boolean = true) {
        setStyleFloat(Companion.getType(), value, notify)
    }

    inline fun setStyleBool(getType: Companion.() -> Int, value: Boolean, notify: Boolean = true) {
        setStyleBool(Companion.getType(), value, notify)
    }

    fun <T : Enum<T>> setStyleEnum(getType: Companion.() -> Int, value: T, notify: Boolean = true) {
        setStylePacked(Companion.getType(), PackedInt(value), notify)
    }

    inline fun setStyleObject(getType: Companion.() -> Int, data: Any?, notify: Boolean = true) {
        setStyleObject(Companion.getType(), data, notify)
    }

    fun setCellSize(value: Float) {
        val valueBits = value.toBits()

        styleData[STYLE_CELL_WIDTH] = valueBits
        styleData[STYLE_CELL_HEIGHT] = valueBits

        notifyItemRangeChanged(0, count, Payload.updateCellSize(valueBits))
    }

    private fun updateStyle(
        gridView: RangeCalendarGridView,
        type: Int, data: PackedInt
    ) {
        when (type) {
            // colors
            STYLE_IN_MONTH_TEXT_COLOR -> gridView.setInMonthTextColor(data.value)
            STYLE_OUT_MONTH_TEXT_COLOR -> gridView.setOutMonthTextColor(data.value)
            STYLE_DISABLED_TEXT_COLOR -> gridView.setDisabledCellTextColor(data.value)
            STYLE_TODAY_TEXT_COLOR -> gridView.setTodayCellColor(data.value)
            STYLE_WEEKDAY_TEXT_COLOR -> gridView.setWeekdayTextColor(data.value)
            STYLE_HOVER_COLOR -> gridView.setHoverColor(data.value)
            STYLE_HOVER_ON_SELECTION_COLOR -> gridView.setHoverOnSelectionColor(data.value)

            // sizes
            STYLE_DAY_NUMBER_TEXT_SIZE ->
                gridView.setDayNumberTextSize(data.float())

            STYLE_WEEKDAY_TEXT_SIZE ->
                gridView.setWeekdayTextSize(data.float())

            STYLE_CELL_RR_RADIUS ->
                gridView.setCellRoundRadius(data.float())

            STYLE_CELL_WIDTH ->
                gridView.setCellWidth(data.float())

            STYLE_CELL_HEIGHT ->
                gridView.setCellHeight(data.float())

            // preferences
            STYLE_WEEKDAY_TYPE ->
                gridView.setWeekdayType(data.enum(WeekdayType::ofOrdinal))

            STYLE_CLICK_ON_CELL_SELECTION_BEHAVIOR ->
                gridView.clickOnCellSelectionBehavior =
                    data.enum(ClickOnCellSelectionBehavior::ofOrdinal)

            // animations
            STYLE_COMMON_ANIMATION_DURATION ->
                gridView.commonAnimationDuration = data.value

            STYLE_HOVER_ANIMATION_DURATION ->
                gridView.hoverAnimationDuration = data.value

            STYLE_SELECTION_FILL_GRADIENT_BOUNDS_TYPE -> gridView.setSelectionFillGradientBoundsType(
                data.enum(SelectionFillGradientBoundsType::ofOrdinal)
            )

            STYLE_CELL_ANIMATION_TYPE -> gridView.setCellAnimationType(data.enum(CellAnimationType::ofOrdinal))

            // other stuff
            STYLE_VIBRATE_ON_SELECTING_CUSTOM_RANGE ->
                gridView.vibrateOnSelectingCustomRange = data.boolean()

            STYLE_SHOW_ADJACENT_MONTHS ->
                gridView.setShowAdjacentMonths(data.boolean())
        }
    }

    private fun updateStyle(
        gridView: RangeCalendarGridView,
        type: Int, data: PackedObject
    ) {
        when (type) {
            STYLE_COMMON_ANIMATION_INTERPOLATOR ->
                gridView.commonAnimationInterpolator = data.value()

            STYLE_HOVER_ANIMATION_INTERPOLATOR ->
                gridView.hoverAnimationInterpolator = data.value()

            STYLE_DECOR_DEFAULT_LAYOUT_OPTIONS ->
                gridView.setDecorationDefaultLayoutOptions(data.value())

            STYLE_SELECTION_FILL ->
                gridView.setSelectionFill(data.value())

            STYLE_SELECTION_MANAGER -> {
                gridView.setSelectionManager(data.value())
            }

            STYLE_CELL_ACCESSIBILITY_INFO_PROVIDER -> {
                gridView.setCellAccessibilityInfoProvider(data.value())
            }
        }
    }

    fun setRange(minDate: PackedDate, minDateEpoch: Long, maxDate: PackedDate, maxDateEpoch: Long) {
        this.minDate = minDate
        this.maxDate = maxDate
        this.minDateEpoch = minDateEpoch
        this.maxDateEpoch = maxDateEpoch

        val oldCount = count

        count = (YearMonth.forDate(maxDate) - YearMonth.forDate(minDate) + 1).totalMonths

        if (oldCount == count) {
            notifyItemRangeChanged(0, count, Payload.updateEnabledRange())
        } else {
            notifyDataSetChanged()
        }
    }

    fun getYearMonthForCalendar(position: Int): YearMonth {
        return YearMonth.forDate(minDate) + position
    }

    fun clearHoverAt(position: Int) {
        notifyItemChanged(position, Payload.clearHover())
    }

    private fun createEnabledRange(): CellRange {
        val start = calendarInfo.start
        val prevYm = calendarInfo.ym - 1

        val daysInPrevMonth = TimeUtils.getDaysInMonth(prevYm)

        val startDate = PackedDate(prevYm, daysInPrevMonth - start - 1)
        val startDateEpoch = startDate.toEpochDay()

        val endDateEpoch = startDateEpoch + 42

        val startCell = if (minDateEpoch > startDateEpoch) {
            getCellByDate(minDate)
        } else {
            Cell(0)
        }

        val endCell = if (maxDateEpoch < endDateEpoch) {
            getCellByDate(maxDate, startCell.index)
        } else {
            Cell(42)
        }

        return CellRange(startCell, endCell)
    }

    private fun updateEnabledRange(gridView: RangeCalendarGridView) {
        gridView.setEnabledCellRange(createEnabledRange())
    }

    private fun createInMonthRange(): CellRange {
        var s = calendarInfo.start
        if (isFirstDaySunday) {
            s++
        }

        return CellRange(s, s + calendarInfo.daysInMonth - 1)
    }

    private fun updateInMonthRange(gridView: RangeCalendarGridView) {
        gridView.setInMonthRange(createInMonthRange())
    }

    // TODO: Find a way to optimize this.
    private fun getCellByDate(date: PackedDate, offset: Int = 0): Cell {
        for (i in offset until 42) {
            val cell = Cell(i)

            if (getDateAtCell(cell) == date) {
                return cell
            }
        }

        return Cell.Undefined
    }

    private fun getCellRangeByDateRange(range: PackedDateRange): CellRange {
        val startCell = getCellByDate(range.start)
        val endCell = getCellByDate(range.end, startCell.index)

        return CellRange(startCell, endCell)
    }

    private fun getDateAtCell(cell: Cell): PackedDate {
        val info = calendarInfo
        val index = cell.index

        val ym = info.ym
        var start = info.start
        val daysInMonth = info.daysInMonth
        val monthEnd = start + daysInMonth - 1

        if (isFirstDaySunday) {
            start++
        }

        return when {
            index < start -> {
                val prevYm = ym - 1

                val day = TimeUtils.getDaysInMonth(prevYm) - start + index + 1
                PackedDate(prevYm, day)
            }

            index <= monthEnd -> {
                val day = index - start + 1

                PackedDate(ym, day)
            }

            else -> {
                val day = index - monthEnd

                PackedDate(ym + 1, day)
            }
        }
    }

    private fun getDateRangeByCellRange(range: CellRange): PackedDateRange {
        return PackedDateRange(getDateAtCell(range.start), getDateAtCell(range.end))
    }

    fun getItemPositionForDate(date: PackedDate): Int {
        return getItemPositionForYearMonth(YearMonth.forDate(date))
    }

    fun getItemPositionForYearMonth(ym: YearMonth): Int {
        return ym.totalMonths - YearMonth.forDate(minDate).totalMonths
    }

    private fun updateGrid(gridView: RangeCalendarGridView) {
        val info = calendarInfo

        val ym = info.ym

        var start = info.start
        val daysInMonth = info.daysInMonth

        val daysInPrevMonth = TimeUtils.getDaysInMonth(ym - 1)
        if (isFirstDaySunday) {
            start++
        }

        val cells = gridView.cells

        for (i in 0 until start) {
            val day = daysInPrevMonth - i
            val index = start - i - 1
            cells[index] = day.toByte()
        }

        for (i in 0 until daysInMonth) {
            val index = start + i
            val day = i + 1
            cells[index] = day.toByte()
        }

        val thisMonthEnd = start + daysInMonth
        for (i in 0 until 42 - thisMonthEnd) {
            val index = thisMonthEnd + i
            val day = i + 1
            cells[index] = day.toByte()
        }

        gridView.onGridChanged()
    }

    private fun createRedirectSelectionGate(ym: YearMonth): RangeCalendarGridView.SelectionGate {
        return object : RangeCalendarGridView.SelectionGate {
            override fun range(range: CellRange): Boolean {
                return selectionGate?.let {
                    calendarInfo.set(ym)

                    val (startDate, endDate) = getDateRangeByCellRange(range)

                    it.range(
                        startDate.year, startDate.month, startDate.dayOfMonth,
                        endDate.year, endDate.month, endDate.dayOfMonth
                    )
                } ?: true
            }
        }
    }

    private fun createRedirectSelectionListener(ym: YearMonth): RangeCalendarGridView.OnSelectionListener {
        return object : RangeCalendarGridView.OnSelectionListener {
            override fun onSelectionCleared() {
                discardSelectionValues()

                onSelectionListener?.onSelectionCleared()
            }

            override fun onSelection(range: CellRange) {
                clearSelectionOnAnotherPage(ym)
                setSelectionValues(range, ym)

                onSelectionListener?.let {
                    calendarInfo.set(ym)

                    val (startDate, endDate) = getDateRangeByCellRange(range)

                    it.onSelection(
                        startDate.year, startDate.month, startDate.dayOfMonth,
                        endDate.year, endDate.month, endDate.dayOfMonth
                    )
                }
            }
        }
    }

    private fun setSelectionValues(range: CellRange, ym: YearMonth) {
        selectionRange = range
        selectionYm = ym
    }

    private fun discardSelectionValues() {
        setSelectionValues(CellRange.Invalid, YearMonth(0))
    }

    fun clearSelection(withAnimation: Boolean) {
        clearSelection(fireEvent = true, withAnimation)
    }

    private fun clearSelectionOnAnotherPage(ym: YearMonth) {
        if (selectionYm != ym) {
            clearSelection(fireEvent = false, withAnimation = false)
        }
    }

    private fun clearSelection(fireEvent: Boolean, withAnimation: Boolean) {
        if (selectionRange.isValid) {
            val position = getItemPositionForYearMonth(selectionYm)

            discardSelectionValues()

            if (position in 0 until count) {
                notifyItemChanged(position, Payload.clearSelection(withAnimation))
            }

            if (fireEvent) {
                onSelectionListener?.onSelectionCleared()
            }
        }
    }

    private fun isSelectionAllowed(ym: YearMonth, dateRange: PackedDateRange): Boolean {
        calendarInfo.set(ym)

        val (start, end) = dateRange
        val gate = selectionGate

        return gate == null ||
                gate.range(
                    start.year, start.month, start.dayOfMonth,
                    end.year, end.month, end.dayOfMonth
                )
    }

    fun selectRange(
        ym: YearMonth,
        dateRange: PackedDateRange,
        requestRejectedBehaviour: SelectionRequestRejectedBehaviour,
        withAnimation: Boolean,
    ): Boolean {
        val position = getItemPositionForYearMonth(ym)

        // position can be negative if selection is out of min-max range
        if (position in 0 until count) {
            if (!isSelectionAllowed(ym, dateRange)) {
                return false
            }

            // Clear selection on the page with selection if it's not the page we're changing selection of.
            clearSelectionOnAnotherPage(ym)

            // isSelectionAllowed sets calendarInfo to ym
            val cellRange = getCellRangeByDateRange(dateRange)

            setSelectionValues(cellRange, ym)

            // Notify the page about selection.
            notifyItemChanged(
                position,
                Payload.select(cellRange, requestRejectedBehaviour, withAnimation)
            )

            return true
        }

        return false
    }

    fun selectWeek(
        ym: YearMonth,
        weekIndex: Int,
        requestRejectedBehaviour: SelectionRequestRejectedBehaviour,
        withAnimation: Boolean
    ): Boolean {
        return selectRange(
            ym,
            dateRange = PackedDateRange.week(ym.year, ym.month, weekIndex),
            requestRejectedBehaviour,
            withAnimation
        )
    }

    fun selectMonth(
        ym: YearMonth,
        requestRejectedBehaviour: SelectionRequestRejectedBehaviour,
        withAnimation: Boolean
    ): Boolean {
        return selectRange(
            ym,
            dateRange = PackedDateRange.month(ym.year, ym.month),
            requestRejectedBehaviour,
            withAnimation
        )
    }

    // this is special case for RangeCalendarView.onRestoreInstanceState
    fun selectOnRestore(ym: YearMonth, cellRange: CellRange) {
        val position = getItemPositionForYearMonth(ym)

        if (position in 0 until count) {
            // Restore the selection of the page. Do it without animation because we're restoring things, not setting it.
            val payload = Payload.select(
                cellRange,
                SelectionRequestRejectedBehaviour.PRESERVE_CURRENT_SELECTION,
                withAnimation = false
            )

            notifyItemChanged(position, payload)
        }
    }

    // Expects that the calendarInfo is initialized to the right year-month.
    private fun updateTodayIndex(gridView: RangeCalendarGridView) {
        val cell = getCellByDate(today, offset = 0)

        if (cell.isDefined) {
            gridView.setTodayCell(cell)
        }
    }

    private fun updateDecorations(gridView: RangeCalendarGridView, ym: YearMonth) {
        gridView.decorations = decorations

        val decorRegion = decorations.getRegion(ym)

        // The reason for such tweak is described in RangeCalendarGridView.onDecorInit()
        val defaultLayoutOptions =
            styleObjData[STYLE_DECOR_DEFAULT_LAYOUT_OPTIONS - STYLE_OBJ_START] as DecorLayoutOptions?

        gridView.onDecorInit(decorRegion, defaultLayoutOptions)

        for (i in 0 until decorLayoutOptionsMap.size()) {
            val date = PackedDate(decorLayoutOptionsMap.keyAt(i))

            if (YearMonth.forDate(date) == ym) {
                val cell = getCellByDate(date)
                val options = decorLayoutOptionsMap.valueAt(i)

                gridView.setDecorationLayoutOptions(cell, options, false)
            }
        }
    }

    fun setDecorationLayoutOptions(
        date: PackedDate,
        value: DecorLayoutOptions,
        withAnimation: Boolean
    ) {
        val position = getItemPositionForDate(date)

        if (position in 0 until count) {
            calendarInfo.set(getYearMonthForCalendar(position))
            val cell = getCellByDate(date)

            notifyItemChanged(position, Payload.setDecorLayoutOptions(cell, value, withAnimation))
        }
    }

    private fun checkDecor(decor: CellDecor, ym: YearMonth, cell: Cell) {
        if (decor.cell.isDefined) {
            throw IllegalStateException("Decoration is already added to the calendar")
        }

        val subregion = decorations.getSubregion(ym, cell)

        if (subregion.isDefined) {
            val expectedClass = decorations[subregion.start].javaClass
            val actualClass = decor.javaClass

            if (actualClass != expectedClass) {
                throw IllegalStateException("Only one class of decoration can be in one cell. Expected class: $expectedClass, actual class: $actualClass")
            }
        }
    }

    private fun checkDecors(decors: Array<out CellDecor>, ym: YearMonth, cell: Cell) {
        val subregion = decorations.getSubregion(ym, cell)

        val firstDecorClass = decors[0].javaClass
        for (i in 1 until decors.size) {
            val decor = decors[i]

            require(decor.cell.isUndefined) {
                "One of decorations is already added to the calendar"
            }

            require(firstDecorClass == decor.javaClass) {
                "All decorations should be one class"
            }
        }

        if (subregion.isDefined) {
            val expectedClass = decorations[subregion.start].javaClass

            for (decor in decors) {
                val actualClass = decor.javaClass

                if (actualClass != expectedClass) {
                    throw IllegalStateException("Only one class of decoration can be in one cell. Expected class: $expectedClass, actual class: $actualClass")
                }
            }
        }
    }

    fun addDecoration(
        decor: CellDecor,
        date: PackedDate,
        withAnimation: Boolean
    ) {
        onDecorAddition(
            date,
            if (withAnimation) DecorAnimationFractionInterpolator.Simultaneous else null,
            check = { ym, cell -> checkDecor(decor, ym, cell) },
            init = { cell ->
                decor.cell = cell
                decor.date = date
            },
            op = { add(decor) }
        )
    }

    fun <T : CellDecor> addDecorations(
        decors: Array<out T>,
        date: PackedDate,
        fractionInterpolator: DecorAnimationFractionInterpolator?
    ) {
        onDecorAdditionMany(date, decors, fractionInterpolator) { addAll(decors) }
    }

    fun <T : CellDecor> insertDecorations(
        indexInCell: Int,
        decors: Array<out T>,
        date: PackedDate,
        fractionInterpolator: DecorAnimationFractionInterpolator?
    ) {
        onDecorAdditionMany(date, decors, fractionInterpolator) { insertAll(indexInCell, decors) }
    }

    private inline fun onDecorAdditionMany(
        date: PackedDate,
        decors: Array<out CellDecor>,
        fractionInterpolator: DecorAnimationFractionInterpolator?,
        op: DecorGroupedList.() -> PackedIntRange
    ) {
        onDecorAddition(
            date,
            fractionInterpolator,
            check = { ym, cell -> checkDecors(decors, ym, cell) },
            init = { cell ->
                decors.forEach {
                    it.date = date
                    it.cell = cell
                }
            },
            op = op
        )
    }

    private inline fun onDecorAddition(
        date: PackedDate,
        fractionInterpolator: DecorAnimationFractionInterpolator?,
        check: (YearMonth, Cell) -> Unit,
        init: (Cell) -> Unit,
        op: DecorGroupedList.() -> PackedIntRange
    ) {
        val position = getItemPositionForDate(date)

        if (position in 0 until count) {
            val ym = getYearMonthForCalendar(position)

            calendarInfo.set(ym)
            val cell = getCellByDate(date)

            check(ym, cell)
            init(cell)

            val affectedRange = decorations.op()
            val newDecorRange = decorations.getRegion(ym)

            notifyItemChanged(
                position,
                Payload.onDecorAdded(newDecorRange, affectedRange, fractionInterpolator)
            )
        }
    }

    fun removeDecoration(decor: CellDecor, withAnimation: Boolean) {
        if (decor.cell.isUndefined) {
            return
        }

        val position = getItemPositionForDate(decor.date)

        if (position in 0 until count) {
            val index = decorations.indexOf(decor)

            if (index < 0) {
                return
            }

            removeDecorationRangeInternal(
                position,
                PackedIntRange(index, index),
                if (withAnimation) DecorAnimationFractionInterpolator.Simultaneous else null
            )
        }
    }

    fun removeDecorationRange(
        start: Int, endInclusive: Int,
        date: PackedDate,
        fractionInterpolator: DecorAnimationFractionInterpolator?
    ) {
        val position = getItemPositionForDate(date)

        if (position in 0 until count) {
            val ym = getYearMonthForCalendar(position)

            calendarInfo.set(ym)
            val cell = getCellByDate(date)

            val subregion = decorations.getSubregion(ym, cell)
            if (subregion.isUndefined) {
                return
            }
            val subregionStart = subregion.start

            when {
                start < 0 -> throw IllegalArgumentException("start is negative")
                endInclusive < 0 -> throw IllegalArgumentException("end is negative")
                start > endInclusive -> throw IllegalArgumentException("start is greater than end")
                endInclusive - start > subregion.endInclusive - subregionStart ->
                    throw IllegalArgumentException("Specified range is out of cell range")
            }

            val absoluteRange =
                PackedIntRange(subregionStart + start, subregionStart + endInclusive)
            removeDecorationRangeInternal(position, absoluteRange, fractionInterpolator)
        }
    }

    fun removeAllDecorations(
        date: PackedDate,
        fractionInterpolator: DecorAnimationFractionInterpolator?
    ) {
        val position = getItemPositionForDate(date)

        if (position in 0 until count) {
            val ym = getYearMonthForCalendar(position)

            calendarInfo.set(ym)
            val cell = getCellByDate(date)

            val subregion = decorations.getSubregion(ym, cell)

            removeDecorationRangeInternal(position, subregion, fractionInterpolator)
        }
    }

    private fun removeDecorationRangeInternal(
        position: Int,
        absoluteRange: PackedIntRange,
        fractionInterpolator: DecorAnimationFractionInterpolator?
    ) {
        if (absoluteRange.isUndefined) {
            return
        }

        val rangeStart = absoluteRange.start
        val instance = decorations[rangeStart]
        val date = instance.date

        val ym = getYearMonthForCalendar(position)

        calendarInfo.set(ym)
        val cell = getCellByDate(date)

        val visual = instance.visual()

        for (i in rangeStart..absoluteRange.endInclusive) {
            decorations[i].also {
                it.cell = Cell.Undefined
                it.date = PackedDate(0)
            }
        }

        decorations.removeRange(absoluteRange)
        val newDecorRange = decorations.getRegion(ym)

        notifyItemChanged(
            position,
            Payload.onDecorRemoved(newDecorRange, absoluteRange, cell, visual, fractionInterpolator)
        )
    }

    override fun getItemCount() = count

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(RangeCalendarGridView(parent.context, cr).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.MATCH_PARENT
            )
        })
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ym = getYearMonthForCalendar(position)

        calendarInfo.set(ym)

        val gridView = holder.calendar

        gridView.ym = ym
        gridView.isFirstDaySunday = isFirstDaySunday
        gridView.onSelectionListener = createRedirectSelectionListener(ym)
        gridView.selectionGate = createRedirectSelectionGate(ym)

        updateGrid(gridView)
        updateEnabledRange(gridView)
        updateInMonthRange(gridView)

        for (type in styleData.indices) {
            updateStyle(gridView, type, PackedInt(styleData[type]))
        }

        for (type in styleObjData.indices) {
            val adjustedType = type + STYLE_OBJ_START

            // Special case for decor's default layout options. The options will be set in updateDecorations()
            if (adjustedType != STYLE_DECOR_DEFAULT_LAYOUT_OPTIONS) {
                updateStyle(gridView, type + STYLE_OBJ_START, PackedObject(styleObjData[type]))
            }
        }

        if (getItemPositionForDate(today) == position) {
            updateTodayIndex(gridView)
        }

        if (selectionYm == ym) {
            // Animation should be seen because animation should be started when selection *changed*,
            // but in this case, it's actually *restored*
            gridView.select(
                selectionRange,
                SelectionRequestRejectedBehaviour.PRESERVE_CURRENT_SELECTION,
                withAnimation = false
            )
        } else {
            gridView.clearSelection(fireEvent = false, doAnimation = false)
        }

        updateDecorations(gridView, ym)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        val gridView = holder.calendar
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            val payload = payloads[0] as Payload
            when (payload.type) {
                Payload.UPDATE_ENABLED_RANGE -> {
                    calendarInfo.set(getYearMonthForCalendar(position))

                    updateEnabledRange(gridView)
                }

                Payload.SELECT -> {
                    val range = CellRange(payload.arg1.toInt())
                    val requestRejectedBehaviour =
                        SelectionRequestRejectedBehaviour.fromOrdinal(payload.arg2.toInt())

                    val withAnimation = payload.arg3 == 1L

                    gridView.select(range, requestRejectedBehaviour, withAnimation)
                }

                Payload.UPDATE_TODAY_INDEX -> {
                    calendarInfo.set(getYearMonthForCalendar(position))

                    updateTodayIndex(gridView)
                }

                Payload.UPDATE_STYLE -> {
                    val type = payload.arg1.toInt()
                    val value = payload.arg2.toInt()

                    if (type >= STYLE_OBJ_START) {
                        updateStyle(gridView, type, PackedObject(payload.obj1))
                    } else {
                        updateStyle(gridView, type, PackedInt(value))
                    }
                }

                Payload.UPDATE_CELL_SIZE -> {
                    val value = Float.fromBits(payload.arg1.toInt())

                    gridView.setCellSize(value)
                }

                Payload.CLEAR_HOVER -> {
                    gridView.clearHoverCellWithAnimation()
                }

                Payload.CLEAR_SELECTION -> {
                    val doAnimation = payload.arg1 == 1L

                    // Don't fire event here. If it's needed, it will be fired in clearSelection()
                    gridView.clearSelection(fireEvent = false, doAnimation)
                }

                Payload.ON_DECOR_ADDED -> {
                    val newDecorRange = PackedIntRange(payload.arg1)
                    val affectedRange = PackedIntRange(payload.arg2)
                    val fractionInterpolator = payload.obj1 as DecorAnimationFractionInterpolator?

                    gridView.onDecorAdded(newDecorRange, affectedRange, fractionInterpolator)
                }

                Payload.ON_DECOR_REMOVED -> {
                    val newDecorRange = PackedIntRange(payload.arg1)
                    val affectedRange = PackedIntRange(payload.arg2)
                    val cell = Cell(payload.arg3.toInt())

                    val fractionInterpolator = payload.obj1 as DecorAnimationFractionInterpolator?
                    val visual = payload.obj2 as CellDecor.Visual

                    gridView.onDecorRemoved(
                        newDecorRange,
                        affectedRange,
                        cell,
                        visual,
                        fractionInterpolator
                    )
                }

                Payload.SET_DECOR_LAYOUT_OPTIONS -> {
                    val cell = Cell(payload.arg1.toInt())
                    val options = payload.obj1 as DecorLayoutOptions
                    val withAnimation = payload.arg2 == 1L

                    gridView.setDecorationLayoutOptions(cell, options, withAnimation)
                }
            }
        }
    }

    companion object {
        const val STYLE_DAY_NUMBER_TEXT_SIZE = 0
        const val STYLE_WEEKDAY_TEXT_SIZE = 1
        const val STYLE_CELL_RR_RADIUS = 2
        const val STYLE_CELL_WIDTH = 3
        const val STYLE_CELL_HEIGHT = 4
        const val STYLE_WEEKDAY_TYPE = 5
        const val STYLE_CLICK_ON_CELL_SELECTION_BEHAVIOR = 6
        const val STYLE_COMMON_ANIMATION_DURATION = 7
        const val STYLE_HOVER_ANIMATION_DURATION = 8
        const val STYLE_VIBRATE_ON_SELECTING_CUSTOM_RANGE = 9
        const val STYLE_SELECTION_FILL_GRADIENT_BOUNDS_TYPE = 10
        const val STYLE_CELL_ANIMATION_TYPE = 11
        const val STYLE_IN_MONTH_TEXT_COLOR = 12
        const val STYLE_OUT_MONTH_TEXT_COLOR = 13
        const val STYLE_DISABLED_TEXT_COLOR = 14
        const val STYLE_TODAY_TEXT_COLOR = 15
        const val STYLE_WEEKDAY_TEXT_COLOR = 16
        const val STYLE_HOVER_COLOR = 17
        const val STYLE_HOVER_ON_SELECTION_COLOR = 18
        const val STYLE_SHOW_ADJACENT_MONTHS = 19

        private const val STYLE_OBJ_START = 32
        const val STYLE_COMMON_ANIMATION_INTERPOLATOR = 32
        const val STYLE_HOVER_ANIMATION_INTERPOLATOR = 33
        const val STYLE_DECOR_DEFAULT_LAYOUT_OPTIONS = 34
        const val STYLE_SELECTION_FILL = 35
        const val STYLE_SELECTION_MANAGER = 36
        const val STYLE_CELL_ACCESSIBILITY_INFO_PROVIDER = 37

        // Precomputed value
        private const val PAGES_BETWEEN_ABS_MIN_MAX = 786432
    }
}