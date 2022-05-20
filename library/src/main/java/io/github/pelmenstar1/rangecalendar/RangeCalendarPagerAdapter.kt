package io.github.pelmenstar1.rangecalendar

import android.animation.TimeInterpolator
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.pelmenstar1.rangecalendar.decoration.CellDecor
import io.github.pelmenstar1.rangecalendar.decoration.DecorAnimationMethodInt
import io.github.pelmenstar1.rangecalendar.selection.Cell
import io.github.pelmenstar1.rangecalendar.selection.CellRange

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
            set(YearMonth(year, month))
        }

        fun set(ym: YearMonth) {
            this.ym = ym

            val year = ym.year
            val month = ym.month

            daysInMonth = TimeUtils.getDaysInMonth(year, month)
            start = PackedDate(year, month, 1).dayOfWeek - 1
        }
    }

    private class Payload(
        val type: Int,
        val arg1: Long = 0L,
        val arg2: Long = 0L,
        val obj: Any? = null
    ) {
        constructor(type: Int, arg1: Int) : this(type, arg1.toLong(), 0L)
        constructor(type: Int, arg1: Boolean) : this(type, if (arg1) 1L else 0L, 0L)
        constructor(type: Int, argObj: Any?) : this(type, 0, arg2 = 0, obj = argObj)

        fun boolean() = arg1 == 1L

        companion object {
            const val UPDATE_ENABLED_RANGE = 0
            const val SELECT = 1
            const val UPDATE_TODAY_INDEX = 2
            const val UPDATE_STYLE = 3
            const val CLEAR_HOVER = 4
            const val CLEAR_SELECTION = 5
            const val ADD_DECORATION = 6
            const val ADD_DECORATIONS = 7
            const val INSERT_DECORATIONS = 8
            const val REMOVE_DECORATION = 9
            const val REMOVE_DECORATION_RANGE = 10
            const val REMOVE_ALL_DECORATIONS = 11

            private val CLEAR_HOVER_PAYLOAD = Payload(CLEAR_HOVER)
            private val CLEAR_SELECTION_PAYLOAD = Payload(CLEAR_SELECTION)
            private val UPDATE_ENABLED_RANGE_PAYLOAD = Payload(UPDATE_ENABLED_RANGE)
            private val UPDATE_TODAY_INDEX_PAYLOAD = Payload(UPDATE_TODAY_INDEX)

            fun clearHover() = CLEAR_HOVER_PAYLOAD
            fun clearSelection() = CLEAR_SELECTION_PAYLOAD
            fun updateEnabledRange() = UPDATE_ENABLED_RANGE_PAYLOAD
            fun updateTodayIndex() = UPDATE_TODAY_INDEX_PAYLOAD

            fun updateStyle(type: Int, data: Int): Payload {
                return Payload(UPDATE_STYLE, type.toLong(), data.toLong())
            }

            fun updateStyle(type: Int, obj: Any?): Payload {
                return Payload(UPDATE_STYLE, type.toLong(), arg2 = 0, obj = obj)
            }

            fun select(info: RangeCalendarGridView.SetSelectionInfo): Payload {
                return Payload(SELECT, info.bits)
            }

            fun addDecoration(decor: CellDecor<*>, cell: Cell, withAnimation: Boolean): Payload {
                return Payload(
                    ADD_DECORATION,
                    arg1 = cell.index.toLong(),
                    arg2 = if (withAnimation) 1 else 0,
                    obj = decor
                )
            }

            fun <T : CellDecor<T>> addDecorations(
                decors: Array<out T>,
                cell: Cell,
                animationMethod: Int
            ): Payload {
                return Payload(
                    ADD_DECORATIONS,
                    arg1 = cell.index.toLong(),
                    arg2 = animationMethod.toLong(),
                    obj = decors
                )
            }

            fun <T : CellDecor<T>> insertDecorations(
                indexInCell: Int,
                decors: Array<out T>,
                cell: Cell,
                animationMethod: Int
            ): Payload {
                return Payload(
                    INSERT_DECORATIONS,
                    arg1 = packInts(cell.index, indexInCell),
                    arg2 = animationMethod.toLong(),
                    obj = decors
                )
            }

            fun removeDecoration(decor: CellDecor<*>, withAnimation: Boolean): Payload {
                return Payload(REMOVE_DECORATION, arg1 = if (withAnimation) 1 else 0, obj = decor)
            }

            fun removeDecorationRange(
                start: Int, endInclusive: Int,
                cell: Cell,
                animationMethod: Int
            ): Payload {
                return Payload(
                    REMOVE_DECORATION_RANGE,
                    arg1 = packInts(cell.index, animationMethod),
                    arg2 = packInts(start, endInclusive)
                )
            }

            fun removeAllDecorations(cell: Cell, animationMethod: Int): Payload {
                return Payload(
                    REMOVE_ALL_DECORATIONS,
                    arg1 = cell.index.toLong(),
                    arg2 = animationMethod.toLong()
                )
            }
        }
    }

    private var count = PAGES_BETWEEN_ABS_MIN_MAX

    private var minDateYm = YearMonth(0)

    private var minDate = RangeCalendarView.MIN_DATE
    private var maxDate = RangeCalendarView.MAX_DATE
    private var minDateEpoch = RangeCalendarView.MIN_DATE_EPOCH
    private var maxDateEpoch = RangeCalendarView.MAX_DATE_EPOCH

    var selectionType = 0

    // if selectionType = CELL, index of selected date is stored
    // if selectionType = WEEK, week index is stored
    // if selectionType = MONTH, nothing is stored
    // if selectionType = CUSTOM, start and end index of range are stored
    var selectionData = 0

    // We need to save year-month of selection despite the fact we can compute it from selectionData
    // because computed position will point to the page where the position is in currentMonthRange
    // and this can lead to bugs when we mutate the wrong page.
    var selectionYm = YearMonth(0)

    private var today = PackedDate(0)
    private val calendarInfo = CalendarInfo()
    private val styleData = LongArray(19)
    private val styleObjData = arrayOfNulls<Any>(4)
    private var onSelectionListener: RangeCalendarView.OnSelectionListener? = null

    init {
        initStyle(STYLE_SELECTION_COLOR, cr.colorPrimary)
        initStyle(STYLE_DAY_NUMBER_TEXT_SIZE, cr.dayNumberTextSize)
        initStyle(STYLE_IN_MONTH_DAY_NUMBER_COLOR, cr.textColor)
        initStyle(STYLE_OUT_MONTH_DAY_NUMBER_COLOR, cr.outMonthTextColor)
        initStyle(STYLE_DISABLED_DAY_NUMBER_COLOR, cr.disabledTextColor)
        initStyle(STYLE_TODAY_COLOR, cr.colorPrimary)
        initStyle(STYLE_WEEKDAY_COLOR, cr.textColor)
        initStyle(STYLE_WEEKDAY_TEXT_SIZE, cr.weekdayTextSize)
        initStyle(STYLE_WEEKDAY_TYPE, WeekdayType.SHORT)
        initStyle(STYLE_HOVER_COLOR, cr.hoverColor)
        initStyle(STYLE_HOVER_ON_SELECTION_COLOR, cr.colorPrimaryDark)
        initStyle(STYLE_RR_RADIUS_RATIO, RangeCalendarGridView.DEFAULT_RR_RADIUS_RATIO)
        initStyle(STYLE_CELL_SIZE, cr.cellSize)
        initStyle(STYLE_CLICK_ON_CELL_SELECTION_BEHAVIOR, ClickOnCellSelectionBehavior.NONE)
        initStyle(
            STYLE_COMMON_ANIMATION_DURATION,
            RangeCalendarGridView.DEFAULT_COMMON_ANIM_DURATION
        )
        initStyle(STYLE_COMMON_ANIMATION_INTERPOLATOR, LINEAR_INTERPOLATOR)
        initStyle(STYLE_HOVER_ANIMATION_DURATION, RangeCalendarGridView.DEFAULT_HOVER_ANIM_DURATION)
        initStyle(STYLE_HOVER_ANIMATION_INTERPOLATOR, LINEAR_INTERPOLATOR)
        initStyle(STYLE_VIBRATE_ON_SELECTING_CUSTOM_RANGE, true)
        initStyle(STYLE_GRID_GRADIENT_ENABLED, true)
        initStyle(
            STYLE_GRID_GRADIENT_START_END_COLORS, packInts(
                cr.colorPrimary, cr.colorPrimaryDark
            )
        )
    }

    private fun initStyle(type: Int, data: Long) {
        styleData[type] = data
    }

    private fun initStyle(type: Int, data: Boolean) {
        styleData[type] = if (data) 1 else 0
    }

    private fun initStyle(type: Int, data: Int) {
        styleData[type] = data.toLong()
    }

    private fun initStyle(type: Int, data: Float) {
        initStyle(type, java.lang.Float.floatToIntBits(data))
    }

    private fun initStyle(type: Int, data: Any) {
        styleObjData[type - STYLE_OBJ_START] = data
    }

    fun setOnSelectionListener(value: RangeCalendarView.OnSelectionListener) {
        onSelectionListener = value
    }

    fun setToday(date: PackedDate) {
        today = date

        val position = getItemPositionForDate(date)

        if (position in 0 until count) {
            notifyItemChanged(position, Payload.updateTodayIndex())
        }
    }

    fun getStyleLong(type: Int): Long {
        return styleData[type]
    }

    fun getStyleInt(type: Int): Int {
        return getStyleLong(type).toInt()
    }

    fun getStyleBool(type: Int): Boolean {
        return getStyleLong(type) == 1L
    }

    fun getStyleFloat(type: Int): Float {
        return java.lang.Float.floatToIntBits(getStyleInt(type).toFloat()).toFloat()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getStyleObject(type: Int): T {
        return styleObjData[type - STYLE_OBJ_START] as T
    }

    fun setStyleInt(type: Int, value: Int, notify: Boolean = true) {
        setStyleLong(type, value.toLong(), notify)
    }

    fun setStyleLong(type: Int, value: Long, notify: Boolean = true) {
        styleData[type] = value

        if (notify) {
            notifyItemRangeChanged(
                0,
                count,
                Payload.updateStyle(type, value)
            )
        }
    }

    fun setStyleBool(type: Int, value: Boolean, notify: Boolean = true) {
        setStyleInt(type, if (value) 1 else 0, notify)
    }

    fun setStyleFloat(type: Int, value: Float, notify: Boolean = true) {
        setStyleInt(type, java.lang.Float.floatToIntBits(value), notify)
    }

    fun setStyleObject(type: Int, data: Any) {
        styleObjData[type - STYLE_OBJ_START] = data

        notifyItemRangeChanged(0, count, Payload.updateStyle(type, data))
    }

    private fun updateStyle(
        gridView: RangeCalendarGridView,
        type: Int, data: Long
    ) {
        val i = data.toInt()
        val b = data == 1L
        val f = java.lang.Float.intBitsToFloat(i)

        when (type) {
            STYLE_DAY_NUMBER_TEXT_SIZE -> gridView.setDayNumberTextSize(f)
            STYLE_WEEKDAY_TEXT_SIZE -> gridView.setDayNameTextSize(f)
            STYLE_SELECTION_COLOR -> gridView.setSelectionColor(i)
            STYLE_IN_MONTH_DAY_NUMBER_COLOR -> gridView.setInMonthDayNumberColor(i)
            STYLE_OUT_MONTH_DAY_NUMBER_COLOR -> gridView.setOutMonthDayNumberColor(i)
            STYLE_DISABLED_DAY_NUMBER_COLOR -> gridView.setDisabledDayNumberColor(i)
            STYLE_TODAY_COLOR -> gridView.setTodayColor(i)
            STYLE_WEEKDAY_COLOR -> gridView.setDayNameColor(i)
            STYLE_HOVER_COLOR -> gridView.setHoverColor(i)
            STYLE_HOVER_ON_SELECTION_COLOR -> gridView.setHoverOnSelectionColor(i)
            STYLE_RR_RADIUS_RATIO -> gridView.roundRectRadiusRatio = f
            STYLE_CELL_SIZE -> gridView.cellSize = f
            STYLE_WEEKDAY_TYPE -> gridView.setWeekdayType(i)
            STYLE_CLICK_ON_CELL_SELECTION_BEHAVIOR -> gridView.clickOnCellSelectionBehavior = i
            STYLE_COMMON_ANIMATION_DURATION -> gridView.commonAnimationDuration = i
            STYLE_HOVER_ANIMATION_DURATION -> gridView.hoverAnimationDuration = i
            STYLE_VIBRATE_ON_SELECTING_CUSTOM_RANGE -> gridView.vibrateOnSelectingCustomRange = b
            STYLE_GRID_GRADIENT_ENABLED -> gridView.setGradientEnabled(b)
            STYLE_GRID_GRADIENT_START_END_COLORS -> {
                val pair = PackedIntPair(data)

                gridView.setGradientColors(pair.first, pair.second)
            }
        }
    }

    private fun updateStyle(
        gridView: RangeCalendarGridView,
        type: Int, data: Any?
    ) {
        when (type) {
            STYLE_COMMON_ANIMATION_INTERPOLATOR -> {
                gridView.commonAnimationInterpolator = data as TimeInterpolator
            }
            STYLE_HOVER_ANIMATION_INTERPOLATOR -> {
                gridView.hoverAnimationInterpolator = data as TimeInterpolator
            }
        }
    }

    fun setRange(minDate: PackedDate, minDateEpoch: Long, maxDate: PackedDate, maxDateEpoch: Long) {
        this.minDate = minDate
        this.maxDate = maxDate
        this.minDateEpoch = minDateEpoch
        this.maxDateEpoch = maxDateEpoch

        val oldCount = count

        minDateYm = YearMonth.forDate(minDate)
        count = YearMonth.forDate(maxDate).totalMonths - minDateYm.totalMonths + 1

        if (oldCount == count) {
            notifyItemRangeChanged(0, count, Payload.updateEnabledRange())
        } else {
            notifyDataSetChanged()
        }
    }

    fun getYearMonthForCalendar(position: Int): YearMonth {
        return minDateYm + position
    }

    fun clearHoverAt(position: Int) {
        notifyItemChanged(position, Payload.clearHover())
    }

    private fun updateEnabledRange(gridView: RangeCalendarGridView) {
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

        gridView.setEnabledCellRange(CellRange(startCell, endCell))
    }

    private fun updateInMonthRange(gridView: RangeCalendarGridView) {
        var s = calendarInfo.start
        if (isFirstDaySunday) {
            s++
        }

        gridView.setInMonthRange(CellRange(s, s + calendarInfo.daysInMonth - 1))
    }

    private fun getCellByDate(date: PackedDate, offset: Int = 0): Cell {
        for (i in offset until 42) {
            if (getDateAtIndex(i) == date) {
                return Cell(i)
            }
        }

        return Cell.Undefined
    }

    private fun getDateAtIndex(index: Int): PackedDate {
        val info = calendarInfo

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

    fun getItemPositionForDate(date: PackedDate): Int {
        return getItemPositionForYearMonth(YearMonth.forDate(date))
    }

    fun getItemPositionForYearMonth(ym: YearMonth): Int {
        return ym.totalMonths - minDateYm.totalMonths
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

    private fun createRedirectSelectionListener(ym: YearMonth): RangeCalendarGridView.OnSelectionListener {
        return object : RangeCalendarGridView.OnSelectionListener {
            override fun onSelectionCleared() {
                discardSelectionValues()
                val listener = onSelectionListener
                listener?.onSelectionCleared()
            }

            override fun onCellSelected(index: Int): Boolean {
                return onSelectedHandler(SelectionType.CELL, index) {
                    val date = getDateAtIndex(index)

                    onDaySelected(date.year, date.month, date.dayOfMonth)
                }
            }

            override fun onWeekSelected(weekIndex: Int, startIndex: Int, endIndex: Int): Boolean {
                return onSelectedHandler(SelectionType.WEEK, weekIndex) {
                    val startDate = getDateAtIndex(startIndex)
                    val endDate = getDateAtIndex(endIndex)

                    onWeekSelected(
                        weekIndex,
                        startDate.year, startDate.month, startDate.dayOfMonth,
                        endDate.year, endDate.month, endDate.dayOfMonth
                    )
                }
            }

            override fun onCustomRangeSelected(startIndex: Int, endIndex: Int): Boolean {
                return onSelectedHandler(
                    SelectionType.CUSTOM,
                    packShorts(startIndex, endIndex)
                ) {
                    val startDate = getDateAtIndex(startIndex)
                    val endDate = getDateAtIndex(endIndex)

                    onCustomRangeSelected(
                        startDate.year, startDate.month, startDate.dayOfMonth,
                        endDate.year, endDate.month, endDate.dayOfMonth
                    )
                }
            }

            private inline fun onSelectedHandler(
                selType: Int,
                selData: Int,
                method: RangeCalendarView.OnSelectionListener.() -> Boolean
            ): Boolean {
                clearSelectionOnAnotherPages()
                setSelectionValues(selType, selData, ym)

                val listener = onSelectionListener
                if (listener != null) {
                    calendarInfo.set(ym)

                    return listener.method()
                }

                return true
            }

            // should be called before changing selection values
            private fun clearSelectionOnAnotherPages() {
                if (selectionYm != ym) {
                    clearSelection(false)
                }
            }
        }
    }

    private fun setSelectionValues(type: Int, data: Int, ym: YearMonth) {
        selectionType = type
        selectionData = data
        selectionYm = ym
    }

    private fun discardSelectionValues() {
        setSelectionValues(SelectionType.NONE, 0, YearMonth(0))
    }

    fun clearSelection() {
        clearSelection(true)
    }

    private fun clearSelection(fireEvent: Boolean) {
        if (selectionType != SelectionType.NONE) {
            val position = getItemPositionForYearMonth(selectionYm)

            discardSelectionValues()

            if (position in 0 until count) {
                notifyItemChanged(position, Payload.clearSelection())
            }

            if (fireEvent) {
                onSelectionListener?.onSelectionCleared()
            }
        }
    }

    // if type = CELL, data is date,
    // if type = WEEK, data is pair of year-month and weekIndex,
    // if type = MONTH, data is year-month,
    // if type = CUSTOM, data is date int range.
    // NOTE, that this year-month will point to the page where selection is (partially) in currentMonthRange
    fun getYearMonthForSelection(type: Int, data: Long): YearMonth {
        return when (type) {
            SelectionType.CELL -> YearMonth.forDate(PackedDate(data.toInt()))
            SelectionType.WEEK, SelectionType.MONTH -> YearMonth(data.toInt())
            SelectionType.CUSTOM -> YearMonth.forDate(PackedDate(unpackFirstInt(data)))
            else -> YearMonth(-1)
        }
    }

    // if type = CELL, data is date,
    // if type = WEEK, data is week index,
    // if type = MONTH, data is unused.
    // if type = CUSTOM, data is date int range
    private fun transformToGridSelection(
        position: Int,
        type: Int,
        data: Long,
        withAnimation: Boolean
    ): RangeCalendarGridView.SetSelectionInfo {
        return when (type) {
            SelectionType.CELL -> {
                calendarInfo.set(getYearMonthForCalendar(position))

                val cell = getCellByDate(PackedDate(data.toInt()))

                RangeCalendarGridView.SetSelectionInfo.cell(cell, withAnimation)
            }
            SelectionType.WEEK -> {
                RangeCalendarGridView.SetSelectionInfo.week(unpackSecondInt(data), withAnimation)
            }
            SelectionType.MONTH -> {
                RangeCalendarGridView.SetSelectionInfo.month(withAnimation)
            }
            SelectionType.CUSTOM -> {
                calendarInfo.set(getYearMonthForCalendar(position))

                val range = PackedIntRange(data)

                val startDate = PackedDate(range.start)
                val endDate = PackedDate(range.endInclusive)

                val startCell = getCellByDate(startDate)
                val endCell = getCellByDate(endDate, startCell.index)

                RangeCalendarGridView.SetSelectionInfo.customRange(
                    CellRange(startCell, endCell),
                    withAnimation
                )
            }
            else -> RangeCalendarGridView.SetSelectionInfo.Undefined
        }
    }

    // if type = CELL, data is date
    // if type = WEEK, data is pair of year-month and weekIndex
    // if type = MONTH, data is year-month
    // if type = CUSTOM, data is date int range
    fun select(type: Int, data: Long, withAnimation: Boolean) {
        val ym = getYearMonthForSelection(type, data)
        val position = getItemPositionForYearMonth(ym)

        // position can be negative if selection is out of min-max range
        if (position in 0 until count) {
            val gridSelectionInfo = transformToGridSelection(position, type, data, withAnimation)

            when (type) {
                SelectionType.MONTH -> {
                    val allowed = onMonthSelected(selectionYm, ym)

                    if (!allowed) {
                        return
                    }
                }
                SelectionType.CUSTOM -> {
                    verifyCustomRange(data)
                }
                else -> {}
            }

            setSelectionValues(type, gridSelectionInfo.data, ym)
            notifyItemChanged(position, Payload.select(gridSelectionInfo))
        }
    }

    // if type = CELL, data is index of the cell
    // if type = WEEK, data is week index
    // if type = MONTH, data is unused
    // if type = CUSTOM, data is start and end indices of the range
    //
    // this is special case for RangeCalendarView.onRestoreInstanceState
    fun select(ym: YearMonth, type: Int, data: Int) {
        val position = getItemPositionForYearMonth(ym)

        if (type == SelectionType.MONTH) {
            val allowed = onMonthSelected(selectionYm, ym)
            if (!allowed) {
                return
            }
        }

        setSelectionValues(type, data, ym)

        if (position in 0 until count) {
            notifyItemChanged(
                position,
                Payload.select(
                    RangeCalendarGridView.SetSelectionInfo.create(type, data, false)
                )
            )
        }
    }

    private fun verifyCustomRange(rangeBits: Long) {
        val range = PackedIntRange(rangeBits)

        val start = range.start
        val end = range.endInclusive

        require(
            YearMonth.forDate(PackedDate(start)) == YearMonth.forDate(PackedDate(end))
        ) {
            "Calendar page position for start date of the range differ from calendar page position for the end"
        }
    }

    private fun onMonthSelected(prevYm: YearMonth, ym: YearMonth): Boolean {
        if (prevYm != ym) {
            clearSelection()
        }

        return onSelectionListener?.onMonthSelected(ym.year, ym.month) ?: true
    }

    private fun updateTodayIndex(gridView: RangeCalendarGridView) {
        val cell = getCellByDate(today, 0)

        if (cell.isDefined) {
            gridView.setTodayCell(cell)
        }
    }

    fun <T : CellDecor<T>> addDecoration(
        decor: T,
        date: PackedDate,
        withAnimation: Boolean
    ) {
        val position = getItemPositionForDate(date)

        if (position in 0 until count) {
            decor.date = date

            calendarInfo.set(getYearMonthForCalendar(position))
            val cell = getCellByDate(date)

            notifyItemChanged(position, Payload.addDecoration(decor, cell, withAnimation))
        }
    }

    fun <T : CellDecor<T>> addDecorations(
        decors: Array<out T>,
        date: PackedDate,
        @DecorAnimationMethodInt animationMethod: Int
    ) {
        val position = getItemPositionForDate(date)

        if (position in 0 until count) {
            decors.forEach { it.date = date }

            calendarInfo.set(getYearMonthForCalendar(position))
            val cell = getCellByDate(date)

            notifyItemChanged(position, Payload.addDecorations(decors, cell, animationMethod))
        }
    }

    fun <T : CellDecor<T>> insertDecorations(
        indexInCell: Int,
        decors: Array<out T>,
        date: PackedDate,
        @DecorAnimationMethodInt animationMethod: Int
    ) {
        val position = getItemPositionForDate(date)

        if (position in 0 until count) {
            decors.forEach { it.date = date }

            calendarInfo.set(getYearMonthForCalendar(position))
            val cell = getCellByDate(date)

            notifyItemChanged(position, Payload.insertDecorations(indexInCell, decors, cell, animationMethod))
        }
    }

    fun <T : CellDecor<T>> removeDecoration(decor: T, withAnimation: Boolean) {
        val position = getItemPositionForDate(decor.date)

        if (position in 0 until count) {
            notifyItemChanged(position, Payload.removeDecoration(decor, withAnimation))
        }
    }

    fun removeDecorationRange(
        start: Int, endInclusive: Int,
        date: PackedDate,
        animationMethod: Int
    ) {
        val position = getItemPositionForDate(date)

        if (position in 0 until count) {
            calendarInfo.set(getYearMonthForCalendar(position))
            val cell = getCellByDate(date)

            notifyItemChanged(
                position,
                Payload.removeDecorationRange(start, endInclusive, cell, animationMethod)
            )
        }
    }

    fun removeAllDecorations(
        date: PackedDate,
        animationMethod: Int
    ) {
        val position = getItemPositionForDate(date)

        if (position in 0 until count) {
            calendarInfo.set(getYearMonthForCalendar(position))
            val cell = getCellByDate(date)

            notifyItemChanged(
                position,
                Payload.removeAllDecorations(cell, animationMethod)
            )
        }
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

        gridView.isFirstDaySunday = isFirstDaySunday
        gridView.onSelectionListener = createRedirectSelectionListener(ym)

        updateGrid(gridView)
        updateEnabledRange(gridView)
        updateInMonthRange(gridView)

        for (type in styleData.indices) {
            updateStyle(gridView, type, styleData[type])
        }

        for (type in styleObjData.indices) {
            updateStyle(gridView, type + STYLE_OBJ_START, styleObjData[type])
        }

        if (getItemPositionForDate(today) == position) {
            updateTodayIndex(gridView)
        }

        if (selectionYm == ym) {
            gridView.select(selectionType, selectionData, true)
        }

        gridView.ym = ym
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
                    gridView.select(RangeCalendarGridView.SetSelectionInfo(payload.arg1))
                }
                Payload.UPDATE_TODAY_INDEX -> {
                    calendarInfo.set(getYearMonthForCalendar(position))

                    updateTodayIndex(gridView)
                }
                Payload.UPDATE_STYLE -> {
                    val type = payload.arg1.toInt()
                    val value = payload.arg2

                    if (type >= STYLE_OBJ_START) {
                        updateStyle(gridView, type, payload.obj)
                    } else {
                        updateStyle(gridView, type, value)
                    }
                }
                Payload.CLEAR_HOVER -> {
                    gridView.clearHoverCellWithAnimation()
                }

                Payload.CLEAR_SELECTION -> {
                    // Don't fire event here. If it's needed, it will be fired in clearSelection()
                    gridView.clearSelection(fireEvent = false, doAnimation = true)
                }

                Payload.ADD_DECORATION -> {
                    val decor = payload.obj as CellDecor<*>
                    val cell = Cell(payload.arg1.toInt())

                    gridView.addDecoration(decor, cell, payload.arg2 == 1L)
                }
                Payload.ADD_DECORATIONS -> {
                    val decors = payload.obj as Array<out CellDecor<*>>
                    val cell = Cell(payload.arg1.toInt())
                    val animationMethod = payload.arg2.toInt()

                    gridView.addDecorations(decors, cell, animationMethod)
                }

                Payload.INSERT_DECORATIONS -> {
                    val decors = payload.obj as Array<out CellDecor<*>>
                    val cellIndex = unpackFirstInt(payload.arg1)
                    val indexInCell = unpackSecondInt(payload.arg1)

                    val cell = Cell(cellIndex)
                    val animationMethod = payload.arg2.toInt()

                    gridView.insertDecorations(indexInCell, decors, cell, animationMethod)
                }

                Payload.REMOVE_DECORATION -> {
                    val decor = payload.obj as CellDecor<*>

                    gridView.removeDecoration(decor, payload.arg1 == 1L)
                }
                Payload.REMOVE_DECORATION_RANGE -> {
                    val cell = Cell(unpackFirstInt(payload.arg1))
                    val animationMethod = unpackSecondInt(payload.arg1)

                    val start = unpackFirstInt(payload.arg2)
                    val end = unpackSecondInt(payload.arg2)

                    gridView.removeDecorationRange(start, end, cell, animationMethod)
                }
                Payload.REMOVE_ALL_DECORATIONS -> {
                    val cell = Cell(unpackFirstInt(payload.arg1))
                    val animationMethod = payload.arg1.toInt()

                    gridView.removeAllDecorationsFromCell(cell, animationMethod)
                }
            }
        }
    }

    companion object {
        const val STYLE_SELECTION_COLOR = 0
        const val STYLE_DAY_NUMBER_TEXT_SIZE = 1
        const val STYLE_IN_MONTH_DAY_NUMBER_COLOR = 2
        const val STYLE_OUT_MONTH_DAY_NUMBER_COLOR = 3
        const val STYLE_DISABLED_DAY_NUMBER_COLOR = 4
        const val STYLE_TODAY_COLOR = 5
        const val STYLE_WEEKDAY_COLOR = 6
        const val STYLE_WEEKDAY_TEXT_SIZE = 7
        const val STYLE_HOVER_COLOR = 8
        const val STYLE_HOVER_ON_SELECTION_COLOR = 9
        const val STYLE_RR_RADIUS_RATIO = 10
        const val STYLE_CELL_SIZE = 11
        const val STYLE_WEEKDAY_TYPE = 12
        const val STYLE_CLICK_ON_CELL_SELECTION_BEHAVIOR = 13
        const val STYLE_COMMON_ANIMATION_DURATION = 14
        const val STYLE_HOVER_ANIMATION_DURATION = 15
        const val STYLE_VIBRATE_ON_SELECTING_CUSTOM_RANGE = 16
        const val STYLE_GRID_GRADIENT_ENABLED = 17
        const val STYLE_GRID_GRADIENT_START_END_COLORS = 18

        private const val STYLE_OBJ_START = 32
        const val STYLE_COMMON_ANIMATION_INTERPOLATOR = 32
        const val STYLE_HOVER_ANIMATION_INTERPOLATOR = 33

        private val PAGES_BETWEEN_ABS_MIN_MAX: Int

        init {
            val absMin = RangeCalendarView.MIN_DATE
            val absMax = RangeCalendarView.MAX_DATE

            val absMaxMonths = YearMonth.forDate(absMax).totalMonths
            val absMinMonths = YearMonth.forDate(absMin).totalMonths

            PAGES_BETWEEN_ABS_MIN_MAX = absMaxMonths - absMinMonths + 1
        }
    }
}