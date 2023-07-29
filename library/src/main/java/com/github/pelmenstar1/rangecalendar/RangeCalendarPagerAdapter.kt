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
    private val cr: CalendarResources
) : RecyclerView.Adapter<RangeCalendarPagerAdapter.ViewHolder>() {
    class ViewHolder(val calendar: RangeCalendarGridView) : RecyclerView.ViewHolder(calendar)

    internal data class Payload(
        val type: Int,
        val arg1: Long = 0L,
        val arg2: Long = 0L,
        val arg3: Long = 0L,
        val obj1: Any? = null,
        val obj2: Any? = null,
    ) {
        // Mainly for tests
        override fun toString(): String = buildString {
            append("Payload(type=")
            when(type) {
                UPDATE_ENABLED_RANGE -> append("UPDATE_ENABLED_RANGE")
                SELECT -> {
                    append("SELECT, range=")
                    append(CellRange(arg1.toInt()).toString())
                    append(", requestRejectedBehaviour=")
                    append(SelectionRequestRejectedBehaviour.fromOrdinal(arg2.toInt()))
                    append(", withAnimation=")
                    append(arg3 == 1L)
                }
                UPDATE_TODAY_INDEX -> append("UPDATE_TODAY_INDEX")
                ON_STYLE_PROP_CHANGED -> {
                    append("ON_STYLE_PROP_CHANGED, styleIndex=")
                    append(RangeCalendarStyleData.propertyToString(arg1.toInt()))
                }
                ON_CELL_SIZE_CHANGED -> append("ON_CELL_SIZE_CHANGED")
                CLEAR_HOVER -> append("CLEAR_HOVER")
                CLEAR_SELECTION -> {
                    append("CLEAR_SELECTION, withAnimation=")
                    append(arg1 == 1L)
                }
                ON_DECOR_ADDED -> {
                    append("ON_DECOR_ADDED, newDecorRange=")
                    append(PackedIntRange(arg1).toString())
                    append(", affectedRange=")
                    append(PackedIntRange(arg2).toString())
                }
                ON_DECOR_REMOVED -> {
                    append("ON_DECOR_REMOVED, newDecorRange=")
                    append(PackedIntRange(arg1).toString())
                    append(", affectedRange=")
                    append(PackedIntRange(arg2).toString())
                }
                SET_DECOR_LAYOUT_OPTIONS -> {
                    append("SET_DECOR_LAYOUT_OPTIONS, cell=")
                    append(Cell(arg1.toInt()).toString())
                    append(", withAnimation")
                    append(arg2 == 1L)
                    append(", options=")
                    append(obj1 as DecorLayoutOptions)
                }
                ON_FIRST_DAY_OF_WEEK_CHANGED -> append("ON_FIRST_DAY_OF_WEEK_CHANGED")
                else -> append("UNKNOWN")
            }

            append(')')
        }

        companion object {
            const val UPDATE_ENABLED_RANGE = 0
            const val SELECT = 1
            const val UPDATE_TODAY_INDEX = 2
            const val ON_STYLE_PROP_CHANGED = 3
            const val ON_CELL_SIZE_CHANGED = 4
            const val CLEAR_HOVER = 5
            const val CLEAR_SELECTION = 6
            const val ON_DECOR_ADDED = 7
            const val ON_DECOR_REMOVED = 8
            const val SET_DECOR_LAYOUT_OPTIONS = 9
            const val ON_FIRST_DAY_OF_WEEK_CHANGED = 10

            private val CLEAR_HOVER_PAYLOAD = Payload(CLEAR_HOVER)
            private val UPDATE_ENABLED_RANGE_PAYLOAD = Payload(UPDATE_ENABLED_RANGE)
            private val UPDATE_TODAY_INDEX_PAYLOAD = Payload(UPDATE_TODAY_INDEX)
            private val ON_FIRST_DAY_OF_WEEK_CHANGED_PAYLOAD = Payload(ON_FIRST_DAY_OF_WEEK_CHANGED)
            private val ON_CELL_SIZE_CHANGED_PAYLOAD = Payload(ON_CELL_SIZE_CHANGED)

            fun clearHover() = CLEAR_HOVER_PAYLOAD

            fun updateEnabledRange() = UPDATE_ENABLED_RANGE_PAYLOAD
            fun updateTodayIndex() = UPDATE_TODAY_INDEX_PAYLOAD
            fun onCellSizeChanged() = ON_CELL_SIZE_CHANGED_PAYLOAD
            fun onFirstDayOfWeekChanged() = ON_FIRST_DAY_OF_WEEK_CHANGED_PAYLOAD

            fun onStylePropertyChanged(styleIndex: Int): Payload {
                return Payload(ON_STYLE_PROP_CHANGED, arg1 = styleIndex.toLong())
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

    var selectedRange = PackedDateRange.Invalid

    // internal as used in tests
    internal var today = PackedDate.INVALID

    private var firstDayOfWeek = CompatDayOfWeek.Undefined

    private val gridInfo = YearMonthGridInfo()
    private val style = RangeCalendarStyleData.default(cr)

    var onSelectionListener: RangeCalendarView.OnSelectionListener? = null
    var selectionGate: RangeCalendarView.SelectionGate? = null

    private val decorations = DecorGroupedList()
    private val decorLayoutOptionsMap = SparseArray<DecorLayoutOptions>()

    fun <T> getStyleObject(type: Int): T = style.getObject(type)
    fun getStylePackedInt(styleIndex: Int) = style.getPackedInt(styleIndex)

    inline fun getStylePackedInt(getStyle: GetCalendarStyleProp): PackedInt =
        getStylePackedInt(getStyle(RangeCalendarStyleData.Companion))

    inline fun <T : Enum<T>> getStyleEnum(getStyle: GetCalendarStyleProp, fromInt: (Int) -> T) =
        getStylePackedInt(getStyle).enum(fromInt)

    inline fun getStyleInt(getType: GetCalendarStyleProp) = getStylePackedInt(getType).value
    inline fun getStyleFloat(getType: GetCalendarStyleProp) = getStylePackedInt(getType).float()
    inline fun getStyleBool(getType: GetCalendarStyleProp) = getStylePackedInt(getType).boolean()

    inline fun <T> getStyleObject(getType: GetCalendarStyleProp): T =
        getStyleObject(getType(RangeCalendarStyleData.Companion))

    fun setStylePacked(styleIndex: Int, packed: PackedInt, notify: Boolean) {
        setStyleWithNotify(styleIndex, notify) { style.set(styleIndex, packed.value) }
    }

    fun setStyleObject(styleIndex: Int, value: Any?, notify: Boolean = true) {
        setStyleWithNotify(styleIndex, notify) { style.set(styleIndex, value) }
    }

    private inline fun setStyleWithNotify(styleIndex: Int, notify: Boolean, set: () -> Boolean) {
        val changed = set()

        if (notify && changed) {
            notifyAllPages(Payload.onStylePropertyChanged(styleIndex))
        }
    }

    inline fun setStylePacked(getStyle: GetCalendarStyleProp, value: PackedInt, notify: Boolean) =
        setStylePacked(getStyle(RangeCalendarStyleData.Companion), value, notify)

    inline fun setStyleInt(getStyle: GetCalendarStyleProp, value: Int, notify: Boolean = true) =
        setStylePacked(getStyle, PackedInt(value), notify)

    inline fun setStyleFloat(getStyle: GetCalendarStyleProp, value: Float, notify: Boolean = true) =
        setStylePacked(getStyle, PackedInt(value), notify)

    inline fun setStyleBool(getStyle: GetCalendarStyleProp, value: Boolean, notify: Boolean = true) =
        setStylePacked(getStyle, PackedInt(value), notify)

    inline fun setStyleEnum(getStyle: GetCalendarStyleProp, value: Enum<*>, notify: Boolean = true) =
        setStylePacked(getStyle, PackedInt(value), notify)

    inline fun setStyleObject(getStyle: GetCalendarStyleProp, data: Any?, notify: Boolean = true) =
        setStyleObject(getStyle(RangeCalendarStyleData.Companion), data, notify)

    fun setCellSize(value: Float) {
        var changed = style.set(RangeCalendarStyleData.CELL_WIDTH, value)
        changed = changed or style.set(RangeCalendarStyleData.CELL_HEIGHT, value)

        if (changed) {
            notifyAllPages(Payload.onCellSizeChanged())
        }
    }

    fun setRange(minDate: PackedDate, maxDate: PackedDate) {
        val oldMinDate = this.minDate
        val oldMaxDate = this.maxDate

        if (oldMinDate == minDate && oldMaxDate == maxDate) {
            return
        }

        this.minDate = minDate
        this.maxDate = maxDate

        val oldCount = count
        val newMinYm = YearMonth.forDate(minDate)
        val newMaxYm = YearMonth.forDate(maxDate)

        val newCount = (newMaxYm - newMinYm + 1).totalMonths
        count = newCount

        if (newCount != oldCount) {
            val minDiff = (newMinYm - YearMonth.forDate(oldMinDate)).totalMonths
            val maxDiff = (newMaxYm - YearMonth.forDate(oldMaxDate)).totalMonths

            if (minDiff > 0) {
                notifyItemRangeRemoved(0, minDiff)

                when {
                    maxDiff > 0 -> notifyItemRangeInserted(oldCount - minDiff, maxDiff)
                    maxDiff < 0 -> notifyItemRangeRemoved(newCount, -maxDiff)
                }
            } else {
                val insertedToFrontCount = -minDiff

                if (minDiff != 0) {
                    notifyItemRangeInserted(0, insertedToFrontCount)
                }

                when {
                    maxDiff > 0 ->
                        notifyItemRangeInserted(oldCount + insertedToFrontCount, maxDiff)

                    maxDiff < 0 -> notifyItemRangeRemoved(newCount, -maxDiff)
                }
            }
        }

        notifyAllPages(Payload.updateEnabledRange())
    }

    fun clearHoverAt(ym: YearMonth) {
        notifyPageChanged(ym, Payload.clearHover())
    }

    private fun createEnabledRange(): CellRange {
        var startCell = gridInfo.getCellByDate(minDate)
        if (startCell.isUndefined) {
            startCell = Cell.Min
        }

        var endCell = gridInfo.getCellByDate(maxDate)
        if (endCell.isUndefined) {
            endCell = Cell.Max
        }

        return CellRange(startCell, endCell)
    }

    fun getYearMonthForCalendar(position: Int): YearMonth {
        return YearMonth.forDate(minDate) + position
    }

    fun getItemPositionForDate(date: PackedDate): Int {
        return getItemPositionForYearMonth(YearMonth.forDate(date))
    }

    fun getItemPositionForYearMonth(ym: YearMonth): Int {
        return ym.totalMonths - YearMonth.forDate(minDate).totalMonths
    }

    private fun isValidPosition(position: Int) = position in 0 until count

    private fun isValidDateRange(range: PackedDateRange): Boolean {
        return range.start.isBetween(minDate, maxDate) && range.end.isBetween(minDate, maxDate)
    }

    private fun notifyAllPages(payload: Payload) {
        notifyItemRangeChanged(0, count, payload)
    }

    private fun notifyPageChanged(position: Int, payload: Payload) {
        if (isValidPosition(position)) {
            notifyItemChanged(position, payload)
        }
    }

    private fun notifyPageChanged(ym: YearMonth, payload: Payload) {
        notifyPageChanged(getItemPositionForYearMonth(ym), payload)
    }

    private fun notifyPageRangeChanged(startYm: YearMonth, endYm: YearMonth, payload: Payload) {
        if (startYm <= endYm) {
            val startPos = getItemPositionForYearMonth(startYm)
            val count = (endYm - startYm + 1).totalMonths

            notifyItemRangeChanged(startPos, count, payload)
        }
    }

    private fun updateGridInfo(ym: YearMonth) {
        gridInfo.set(ym, firstDayOfWeek)
    }

    private fun updateGridInfo(year: Int, month: Int) {
        gridInfo.set(year, month, firstDayOfWeek)
    }

    private fun updateEnabledRange(gridView: RangeCalendarGridView) {
        gridView.setEnabledCellRange(createEnabledRange())
    }

    fun setToday(date: PackedDate) {
        val oldToday = today
        today = date

        val oldTodayPosition = if (oldToday == PackedDate.INVALID) {
            -1
        } else {
            getItemPositionForDate(oldToday)
        }

        val newTodayPosition = getItemPositionForDate(date)

        notifyPageChanged(newTodayPosition, Payload.updateTodayIndex())

        if (oldTodayPosition != newTodayPosition) {
            notifyPageChanged(oldTodayPosition, Payload.updateTodayIndex())
        }
    }

    fun setFirstDayOfWeek(firstDayOfWeek: CompatDayOfWeek) {
        if (this.firstDayOfWeek != firstDayOfWeek) {
            this.firstDayOfWeek = firstDayOfWeek
            
            clearSelection(fireEvent = true, withAnimation = false)

            notifyAllPages(Payload.onFirstDayOfWeekChanged())
        }
    }

    private fun onFirstDayOfWeekChanged(gridView: RangeCalendarGridView, position: Int) {
        updateGrid(gridView)
        updateToday(gridView, position)

        gridView.clearSelection(
            fireEvent = false,
            withAnimation = style.getBoolean { IS_SELECTION_ANIMATED_BY_DEFAULT }
        )

        gridView.setFirstDayOfWeek(firstDayOfWeek)
    }

    private fun updateGrid(gridView: RangeCalendarGridView) {
        gridInfo.fillGrid(gridView.cells)

        gridView.onGridChanged()
    }

    private fun createRedirectSelectionGate(ym: YearMonth): RangeCalendarGridView.SelectionGate {
        return object : RangeCalendarGridView.SelectionGate {
            override fun accept(range: CellRange): Boolean {
                return selectionGate?.let {
                    updateGridInfo(ym)

                    val (startDate, endDate) = gridInfo.getDateRangeByCellRange(range)

                    it.accept(
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
                selectedRange = PackedDateRange.Invalid

                onSelectionListener?.onSelectionCleared()
            }

            override fun onSelection(range: CellRange) {
                clearSelectionOnAnotherPage(ym)
                updateGridInfo(ym)

                val dateRange = gridInfo.getDateRangeByCellRange(range)
                selectedRange = dateRange

                onSelectionListener?.let {
                    val (startDate, endDate) = dateRange

                    it.onSelection(
                        startDate.year, startDate.month, startDate.dayOfMonth,
                        endDate.year, endDate.month, endDate.dayOfMonth
                    )
                }
            }
        }
    }

    fun clearSelection(withAnimation: Boolean) {
        clearSelection(fireEvent = true, withAnimation)
    }

    private fun clearSelection(fireEvent: Boolean, withAnimation: Boolean) {
        val selRange = selectedRange

        if (selRange.isValid) {
            val (startYm, endYm) = selRange.toYearMonthRange()

            notifyPageRangeChanged(startYm, endYm, Payload.clearSelection(withAnimation))

            selectedRange = PackedDateRange.Invalid

            if (fireEvent) {
                onSelectionListener?.onSelectionCleared()
            }
        }
    }

    private fun clearSelectionOnAnotherPage(ym: YearMonth) {
        clearSelectionExcept(ym, ym)
    }

    private fun clearSelectionExcept(startYm: YearMonth, endYm: YearMonth) {
        val selRange = selectedRange

        if (selRange.isValid) {
            val otherRange = YearMonthRange(startYm, endYm)
            val selYmRange = selRange.toYearMonthRange()

            val intersection = selYmRange.intersectionWith(otherRange)

            if (intersection.isValid) {
                val payload = Payload.clearSelection(withAnimation = false)

                notifyPageRangeChanged(selYmRange.start, intersection.start - 1, payload)
                notifyPageRangeChanged(intersection.end + 1, selYmRange.end, payload)
            }
        }
    }

    private fun clearSelectionOnPage(ym: YearMonth, withAnimation: Boolean) {
        notifyPageChanged(ym, Payload.clearSelection(withAnimation))
    }

    private fun isSelectionAllowed(dateRange: PackedDateRange): Boolean {
        val (start, end) = dateRange
        val gate = selectionGate

        return gate == null ||
                gate.accept(
                    start.year, start.month, start.dayOfMonth,
                    end.year, end.month, end.dayOfMonth
                )
    }

    fun selectRange(
        dateRange: PackedDateRange,
        requestRejectedBehaviour: SelectionRequestRejectedBehaviour,
        withAnimation: Boolean,
    ): Boolean {
        if (isValidDateRange(dateRange)) {
            if (!isSelectionAllowed(dateRange)) {
                if (requestRejectedBehaviour == SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION) {
                    clearSelection(withAnimation)
                }

                return false
            }

            val ymRange = dateRange.toYearMonthRange()
            val (startYm, endYm) = ymRange

            clearSelectionExcept(startYm, endYm)

            iterateYearMonth(startYm, endYm) { ym ->
                updateGridInfo(ym)

                val cellRange = gridInfo.getCellRangeByDateRange(dateRange)

                // Notify the page about selection.
                notifyPageChanged(
                    ym,
                    Payload.select(cellRange, requestRejectedBehaviour, withAnimation)
                )
            }

            selectedRange = dateRange

            return true
        }

        return false
    }

    // Expects that the gridInfo is initialized to the right year-month.
    private fun updateToday(gridView: RangeCalendarGridView, position: Int) {
        val cell = if (getItemPositionForDate(today) == position) {
            gridInfo.getCellByDate(today)
        } else {
            Cell.Undefined
        }

        gridView.setTodayCell(cell)
    }

    // Expects that gridInfo is initialized to the right year-month
    private fun initDecorations(gridView: RangeCalendarGridView, ym: YearMonth) {
        gridView.decorations = decorations

        gridView.onDecorInit(newDecorRegion = decorations.getRegion(ym))

        for (i in 0 until decorLayoutOptionsMap.size()) {
            val date = PackedDate(decorLayoutOptionsMap.keyAt(i))

            if (YearMonth.forDate(date) == ym) {
                val cell = gridInfo.getCellByDate(date)
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

        if (isValidPosition(position)) {
            updateGridInfo(date.year, date.month)
            val cell = gridInfo.getCellByDate(date)

            notifyItemChanged(position, Payload.setDecorLayoutOptions(cell, value, withAnimation))
        }
    }

    private fun throwDecorAlreadyAdded(): Nothing {
        throw IllegalArgumentException("Decoration is already added to the calendar")
    }

    private fun throwDecorsShouldBeSameClass() {
        throw IllegalStateException("All decorations in a cell should be of single type")
    }

    private fun checkDecor(decor: CellDecor, ym: YearMonth, cell: Cell) {
        if (decor.cell.isDefined) {
            throwDecorAlreadyAdded()
        }

        val subregion = decorations.getSubregion(ym, cell)

        if (subregion.isDefined) {
            val expectedClass = decorations[subregion.start].javaClass
            val actualClass = decor.javaClass

            if (actualClass != expectedClass) {
                throwDecorsShouldBeSameClass()
            }
        }
    }

    private fun checkDecors(decors: Array<out CellDecor>, ym: YearMonth, cell: Cell) {
        if (decors.isEmpty()) {
            throw IllegalArgumentException("Decorations array can't be empty")
        }

        val subregion = decorations.getSubregion(ym, cell)

        val expectedClass = if (subregion.isDefined) {
            decorations[subregion.start].javaClass
        } else {
            decors[0].javaClass
        }

        for (decor in decors) {
            if (decor.cell.isDefined) {
                throwDecorAlreadyAdded()
            }

            if (decor.javaClass != expectedClass) {
                throwDecorsShouldBeSameClass()
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

        if (isValidPosition(position)) {
            val ym = YearMonth.forDate(date)
            updateGridInfo(date.year, date.month)

            val cell = gridInfo.getCellByDate(date)

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

        if (isValidPosition(position)) {
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

        if (isValidPosition(position)) {
            val ym = getYearMonthForCalendar(position)

            updateGridInfo(ym)
            val cell = gridInfo.getCellByDate(date)

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

        if (isValidPosition(position)) {
            val ym = YearMonth.forDate(date)
            updateGridInfo(date.year, date.month)

            val cell = gridInfo.getCellByDate(date)

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

        updateGridInfo(ym)
        val cell = gridInfo.getCellByDate(date)

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
        return ViewHolder(RangeCalendarGridView(parent.context, cr, style).apply {
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.MATCH_PARENT
            )
        })
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val ym = getYearMonthForCalendar(position)
        updateGridInfo(ym)

        val gridView = holder.calendar

        gridView.ym = ym
        gridView.onSelectionListener = createRedirectSelectionListener(ym)
        gridView.selectionGate = createRedirectSelectionGate(ym)

        updateGrid(gridView)
        updateEnabledRange(gridView)
        gridView.setInMonthRange(gridInfo.inMonthRange)
        updateToday(gridView, position)

        gridView.setFirstDayOfWeek(firstDayOfWeek)

        gridView.onAllStylePropertiesPossiblyChanged()

        val (selStart, selEnd) = selectedRange

        if (gridInfo.contains(selStart) || gridInfo.contains(selEnd)) {
            val cellRange = gridInfo.getCellRangeByDateRange(selectedRange)

            // Animation should be seen because animation should be started when selection *changed*,
            // but in this case, it's actually *restored*
            gridView.select(
                cellRange,
                SelectionRequestRejectedBehaviour.PRESERVE_CURRENT_SELECTION,
                withAnimation = false,
                fireEvent = false
            )
        } else {
            gridView.clearSelection(fireEvent = false, withAnimation = false)
        }

        initDecorations(gridView, ym)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        val gridView = holder.calendar
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position)
        } else {
            val payload = payloads[0] as Payload
            when (payload.type) {
                Payload.UPDATE_ENABLED_RANGE -> {
                    updateGridInfo(getYearMonthForCalendar(position))

                    updateEnabledRange(gridView)
                }

                Payload.SELECT -> {
                    val range = CellRange(payload.arg1.toInt())
                    val requestRejectedBehaviour =
                        SelectionRequestRejectedBehaviour.fromOrdinal(payload.arg2.toInt())

                    val withAnimation = payload.arg3 == 1L

                    gridView.select(range, requestRejectedBehaviour, withAnimation, fireEvent = false)
                }

                Payload.UPDATE_TODAY_INDEX -> {
                    updateGridInfo(getYearMonthForCalendar(position))

                    updateToday(gridView, position)
                }

                Payload.ON_STYLE_PROP_CHANGED -> {
                    val propIndex = payload.arg1.toInt()

                    gridView.onStylePropertyChanged(propIndex)
                }

                Payload.ON_CELL_SIZE_CHANGED -> {
                    gridView.onCellSizeComponentChanged()
                }

                Payload.CLEAR_HOVER -> {
                    gridView.clearHoverCell()
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

                Payload.ON_FIRST_DAY_OF_WEEK_CHANGED -> {
                    updateGridInfo(getYearMonthForCalendar(position))

                    onFirstDayOfWeekChanged(gridView, position)
                }
            }
        }
    }

    companion object {
        // Precomputed value
        internal const val PAGES_BETWEEN_ABS_MIN_MAX = 786432
    }
}