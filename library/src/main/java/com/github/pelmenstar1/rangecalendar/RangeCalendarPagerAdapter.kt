package com.github.pelmenstar1.rangecalendar

import android.util.SparseArray
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.pelmenstar1.rangecalendar.decoration.CellDecor
import com.github.pelmenstar1.rangecalendar.decoration.DecorAnimationFractionInterpolator
import com.github.pelmenstar1.rangecalendar.decoration.DecorGroupedList
import com.github.pelmenstar1.rangecalendar.decoration.DecorLayoutOptions
import com.github.pelmenstar1.rangecalendar.selection.*
import kotlin.math.min

// TODO: Audit isFirstDaySunday
internal class RangeCalendarPagerAdapter(
    private val cr: CalendarResources,
    //private val isFirstDaySunday: Boolean
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

            private val CLEAR_HOVER_PAYLOAD = Payload(CLEAR_HOVER)
            private val UPDATE_ENABLED_RANGE_PAYLOAD = Payload(UPDATE_ENABLED_RANGE)
            private val UPDATE_TODAY_INDEX_PAYLOAD = Payload(UPDATE_TODAY_INDEX)
            private val SELECT_PAYLOAD = Payload(SELECT)

            fun clearHover() = CLEAR_HOVER_PAYLOAD

            fun updateEnabledRange() = UPDATE_ENABLED_RANGE_PAYLOAD
            fun updateTodayIndex() = UPDATE_TODAY_INDEX_PAYLOAD

            fun onStylePropertyChanged(styleIndex: Int): Payload {
                return Payload(ON_STYLE_PROP_CHANGED, arg1 = styleIndex.toLong())
            }

            fun onCellSizeChanged(): Payload {
                return Payload(ON_CELL_SIZE_CHANGED)
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

    var selectionRange = CellRange.Invalid
    var selectionYm = YearMonth(0)

    // internal as used in tests
    internal var today = PackedDate.INVALID

    private val gridInfo = YearMonthGridInfo()
    private val style = RangeCalendarStyleData.default(cr)

    private var onSelectionListener: RangeCalendarView.OnSelectionListener? = null
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
        val startCell = if (minDate > gridInfo.firstCellInGridDate) {
            gridInfo.getCellByDate(minDate)
        } else {
            Cell(0)
        }

        val endCell = if (maxDate < gridInfo.lastCellInGridDate) {
            gridInfo.getCellByDate(maxDate)
        } else {
            Cell(42)
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

    private fun updateGrid(gridView: RangeCalendarGridView) {
        gridInfo.fillGrid(gridView.cells)

        gridView.onGridChanged()
    }

    private fun createRedirectSelectionGate(ym: YearMonth): RangeCalendarGridView.SelectionGate {
        return object : RangeCalendarGridView.SelectionGate {
            override fun range(range: CellRange): Boolean {
                return selectionGate?.let {
                    gridInfo.set(ym)

                    val (startDate, endDate) = gridInfo.getDateRangeByCellRange(range)

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
                    gridInfo.set(ym)

                    val (startDate, endDate) = gridInfo.getDateRangeByCellRange(range)

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
            notifyPageChanged(selectionYm, Payload.clearSelection(withAnimation))
            discardSelectionValues()

            if (fireEvent) {
                onSelectionListener?.onSelectionCleared()
            }
        }
    }

    private fun isSelectionAllowed(dateRange: PackedDateRange): Boolean {
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

        if (isValidPosition(position) && isSelectionAllowed(dateRange)) {
            gridInfo.set(ym)

            // Clear selection on the page with selection if it's not the page we're changing selection of.
            clearSelectionOnAnotherPage(ym)

            val cellRange = gridInfo.getCellRangeByDateRange(dateRange)

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
        // Restore the selection of the page. Do it without animation because we're restoring things, not setting it.
        val payload = Payload.select(
            cellRange,
            SelectionRequestRejectedBehaviour.PRESERVE_CURRENT_SELECTION,
            withAnimation = false
        )

        notifyPageChanged(ym, payload)
    }

    // Expects that the gridInfo is initialized to the right year-month.
    private fun updateTodayIndex(gridView: RangeCalendarGridView, position: Int) {
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
            gridInfo.set(date.year, date.month)
            val cell = gridInfo.getCellByDate(date)

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

        if (isValidPosition(position)) {
            val ym = YearMonth.forDate(date)
            gridInfo.set(date.year, date.month)

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

            gridInfo.set(ym)
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
            gridInfo.set(date.year, date.month)

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

        gridInfo.set(ym)
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
        gridInfo.set(ym)

        val gridView = holder.calendar

        gridView.ym = ym
        gridView.onSelectionListener = createRedirectSelectionListener(ym)
        gridView.selectionGate = createRedirectSelectionGate(ym)

        updateGrid(gridView)
        updateEnabledRange(gridView)
        gridView.setInMonthRange(gridInfo.inMonthRange)
        updateTodayIndex(gridView, position)

        // Call onStylePropertyChanged to initialize the view with particular style data.
        style.forEachProperty { propIndex ->
            gridView.onStylePropertyChanged(propIndex)
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
                    gridInfo.set(getYearMonthForCalendar(position))

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
                    gridInfo.set(getYearMonthForCalendar(position))

                    updateTodayIndex(gridView, position)
                }

                Payload.ON_STYLE_PROP_CHANGED -> {
                    val propIndex = payload.arg1.toInt()

                    gridView.onStylePropertyChanged(propIndex)
                }

                Payload.ON_CELL_SIZE_CHANGED -> {
                    gridView.onCellSizeComponentChanged()
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
        // Precomputed value
        internal const val PAGES_BETWEEN_ABS_MIN_MAX = 786432
    }
}