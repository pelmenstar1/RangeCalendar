package io.github.pelmenstar1.rangecalendar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.*
import android.text.format.DateFormat
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.ColorInt
import androidx.annotation.ColorLong
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import io.github.pelmenstar1.rangecalendar.decoration.*
import io.github.pelmenstar1.rangecalendar.selection.*
import io.github.pelmenstar1.rangecalendar.utils.*
import java.lang.ref.WeakReference
import java.util.*

// It will never be XML layout, so there's no need to match conventions
@SuppressLint("ViewConstructor")
internal class RangeCalendarGridView(
    context: Context,
    val cr: CalendarResources
) : View(context) {
    interface OnSelectionListener {
        fun onSelectionCleared()
        fun onCellSelected(cell: Cell)
        fun onWeekSelected(weekIndex: Int, range: CellRange)
        fun onCustomRangeSelected(range: CellRange)
    }

    interface SelectionGate {
        fun cell(cell: Cell): Boolean
        fun week(weekIndex: Int, range: CellRange): Boolean
        fun customRange(range: CellRange): Boolean
    }

    @JvmInline
    value class SetSelectionInfo(val bits: Long) {
        // Packed values positions:
        // 64-32 bits - data
        // 32-24 bits - type
        // 24-16 bits - withAnimation (1 if true, 0 if false)

        val type: SelectionType
            get() = SelectionType.ofOrdinal((bits shr 24).toInt() and 0xFF)

        val data: NarrowSelectionData
            get() = NarrowSelectionData((bits shr 32).toInt())

        val withAnimation: Boolean
            get() = (bits shr 16) and 0xFF == 1L

        companion object {
            val Undefined = create(SelectionType.NONE, NarrowSelectionData(0), false)

            fun create(
                type: SelectionType,
                data: NarrowSelectionData,
                withAnimation: Boolean
            ): SetSelectionInfo {
                return SetSelectionInfo(
                    (data.bits.toLong() shl 32) or
                            (type.ordinal.toLong() shl 24) or
                            ((if (withAnimation) 1L else 0L) shl 16)
                )
            }

            fun cell(cell: Cell, withAnimation: Boolean): SetSelectionInfo {
                return create(SelectionType.CELL, NarrowSelectionData.cell(cell), withAnimation)
            }

            fun week(index: Int, withAnimation: Boolean): SetSelectionInfo {
                return create(SelectionType.WEEK, NarrowSelectionData.week(index), withAnimation)
            }

            fun month(withAnimation: Boolean): SetSelectionInfo {
                return create(SelectionType.MONTH, NarrowSelectionData(0), withAnimation)
            }

            fun customRange(range: CellRange, withAnimation: Boolean): SetSelectionInfo {
                return create(
                    SelectionType.CUSTOM,
                    NarrowSelectionData.customRange(range),
                    withAnimation
                )
            }
        }
    }

    private class TouchHelper(private val grid: RangeCalendarGridView) :
        ExploreByTouchHelper(grid) {
        private val tempCalendar = Calendar.getInstance()
        private val tempRect = Rect()

        override fun getVirtualViewAt(x: Float, y: Float): Int {
            return if (y > grid.gridTop()) {
                grid.getCellByPointOnScreen(x, y).index
            } else {
                INVALID_ID
            }
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            virtualViewIds.addAll(INDICES)
        }

        override fun onPopulateNodeForVirtualView(
            virtualViewId: Int,
            node: AccessibilityNodeInfoCompat
        ) {
            val cell = Cell(virtualViewId)

            val (x, y) = grid.getCellLeftTop(cell)

            val cellSize = grid.cellSize

            tempRect.set(x.toInt(), y.toInt(), (x + cellSize).toInt(), (y + cellSize).toInt())

            node.apply {
                @Suppress("DEPRECATION")
                setBoundsInParent(tempRect)

                contentDescription = getDayDescriptionForIndex(virtualViewId)
                text = CalendarResources.getDayText(grid.cells[virtualViewId].toInt())

                val selState = grid.selectionManager.currentState

                isSelected = selState.type == SelectionType.CELL && cell == selState.startCell
                isClickable = true

                isEnabled = if (grid.enabledCellRange.contains(cell)) {
                    addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)

                    true
                } else {
                    false
                }
            }
        }

        override fun onPerformActionForVirtualView(
            virtualViewId: Int,
            action: Int,
            arguments: Bundle?
        ): Boolean {
            return if (action == AccessibilityNodeInfoCompat.ACTION_CLICK) {
                grid.selectCell(Cell(virtualViewId), doAnimation = false, isUser = true)

                true
            } else false
        }

        private fun getDayDescriptionForIndex(index: Int): CharSequence {
            val currentMonthStart = grid.inMonthRange.start.index
            val currentMonthEnd = grid.inMonthRange.end.index

            var ym = grid.ym
            if (index < currentMonthStart) {
                ym -= 1
            } else if (index > currentMonthEnd) {
                ym += 1
            }

            val day = grid.cells[index].toInt()

            PackedDate(ym, day).toCalendar(tempCalendar)

            return DateFormat.format(DATE_FORMAT, tempCalendar)
        }

        companion object {
            private const val DATE_FORMAT = "dd MMMM yyyy"

            private val INDICES = ArrayList<Int>(42).also {
                repeat(42, it::add)
            }
        }
    }

    private class PressTimeoutHandler(
        gridView: RangeCalendarGridView
    ) : Handler(Looper.getMainLooper()) {
        private val ref = WeakReference(gridView)

        override fun handleMessage(msg: Message) {
            val obj = ref.get() ?: return

            val cell = Cell(msg.arg1)

            when (msg.what) {
                MSG_LONG_PRESS -> obj.onCellLongPress(cell)
                MSG_HOVER_PRESS -> obj.setHoverCell(cell)
            }
        }
    }

    private class CellMeasureManagerImpl(private val view: RangeCalendarGridView) : CellMeasureManager {
        override val cellSize: Float
            get() = view.cellSize

        override fun getCellLeft(cellIndex: Int): Float = view.getCellLeft(Cell(cellIndex))
        override fun getCellTop(cellIndex: Int): Float = view.getCellTop(Cell(cellIndex))

        override fun lerpCellLeft(startIndex: Int, endIndex: Int, fraction: Float, outPoint: PointF) {
            view.lerpCellLeft(Cell(startIndex), Cell(endIndex), fraction, outPoint)
        }

        override fun lerpCellRight(startIndex: Int, endIndex: Int, fraction: Float, outPoint: PointF) {
            view.lerpCellRight(Cell(startIndex), Cell(endIndex), fraction, outPoint)
        }
    }

    val cells = ByteArray(42)

    internal var cellSize: Float = cr.cellSize
    private var columnWidth = 0f

    // The cell is circle by default and to achieve it with any possible cell size,
    // we should take greatest round radius possible, that's why it's positive inf.
    //
    // But when infinity is passed as round radius, round radius is changed to 0 somewhere inside the Android
    // and round rect becomes rect. So, when rrRadius is used, it needs to be resolved to get rid of infinities.
    //
    // There's method for that: cellRoundRadius()
    private var rrRadius: Float = Float.POSITIVE_INFINITY

    private val dayNumberPaint: Paint
    private val weekdayPaint: Paint
    private val selectionPaint: Paint
    private val cellHoverPaint: Paint

    private var selectionFill: Fill
    private var selectionFillGradientBoundsType = SelectionFillGradientBoundsType.GRID

    private val styleColors: CompatColorArray

    private var lastTouchTime: Long = -1
    private var lastTouchCell = Cell.Undefined

    private var inMonthRange = CellRange.Invalid
    private var enabledCellRange = ALL_SELECTED
    private var todayCell = Cell.Undefined
    private var hoverCell = Cell.Undefined
    private var animationHoverCell = Cell.Undefined

    var ym = YearMonth(0)

    var onSelectionListener: OnSelectionListener? = null
    var selectionGate: SelectionGate? = null

    private var selectionManager: SelectionManager = DefaultSelectionManager()
    private var selectionRenderOptions: SelectionRenderOptions

    private val cellMeasureManager = CellMeasureManagerImpl(this)

    private var customRangeStartCell = Cell.Undefined
    private var isSelectingCustomRange = false

    private var animType = 0
    private var animFraction = 0f
    private var animator: ValueAnimator? = null
    private var onAnimationEnd: Runnable? = null
    private var animationHandler: (() -> Unit)? = null

    var isFirstDaySunday = false
    private val touchHelper: TouchHelper

    private var weekdayType = WeekdayType.SHORT

    private var isWeekdayMeasurementsDirty = false

    private var weekdayWidths: FloatArray = cr.defaultWeekdayWidths
    private var maxWeekdayHeight: Float = cr.defaultShortWeekdayRowHeight

    private var isDayNumberMeasurementsDirty = false
    private var dayNumberSizes = cr.defaultDayNumberSizes

    var clickOnCellSelectionBehavior = ClickOnCellSelectionBehavior.NONE
    var commonAnimationDuration = DEFAULT_COMMON_ANIM_DURATION
    var hoverAnimationDuration = DEFAULT_HOVER_ANIM_DURATION
    var commonAnimationInterpolator: TimeInterpolator = LINEAR_INTERPOLATOR
    var hoverAnimationInterpolator: TimeInterpolator = LINEAR_INTERPOLATOR

    private var cellAnimationType = CellAnimationType.ALPHA

    private var vibrator: Vibrator? = null
    private var vibrationEffect: VibrationEffect? = null

    var vibrateOnSelectingCustomRange = true

    internal var decorations: DecorGroupedList? = null
    private var decorRegion = PackedIntRange.Undefined

    private val decorVisualStates = LazyCellDataArray<CellDecor.VisualState>()
    private val decorLayoutOptionsArray = LazyCellDataArray<DecorLayoutOptions>()
    private val cellInfo = CellInfo()
    private var decorDefaultLayoutOptions: DecorLayoutOptions? = null

    private var decorAnimFractionInterpolator: DecorAnimationFractionInterpolator? = null
    private var decorAnimatedCell = Cell.Undefined
    private var decorAnimatedRange = PackedIntRange(0)
    private var decorAnimationHandler: (() -> Unit)? = null
    private var decorTransitiveState: CellDecor.VisualState.Transitive? = null

    private val pressTimeoutHandler = PressTimeoutHandler(this)

    init {
        val defTextColor = cr.textColor
        val colorPrimary = cr.colorPrimary
        val dayNumberTextSize = cr.dayNumberTextSize

        touchHelper = TouchHelper(this)
        ViewCompat.setAccessibilityDelegate(this, touchHelper)

        styleColors = CompatColorArray(7).apply {
            setColorInt(COLOR_STYLE_IN_MONTH, defTextColor)
            setColorInt(COLOR_STYLE_OUT_MONTH, cr.outMonthTextColor)
            setColorInt(COLOR_STYLE_DISABLED, cr.disabledTextColor)
            setColorInt(COLOR_STYLE_TODAY, colorPrimary)
            setColorInt(COLOR_STYLE_HOVER, cr.hoverColor)
            setColorInt(COLOR_STYLE_HOVER_ON_SELECTION, cr.colorPrimaryDark)
            setColorInt(COLOR_STYLE_WEEKDAY, defTextColor)
        }

        selectionFill = Fill.solid(colorPrimary)
        background = null

        selectionRenderOptions = SelectionRenderOptions(
            selectionFill,
            SelectionFillGradientBoundsType.GRID,
            cr.cellSize * 0.5f,
            CellAnimationType.ALPHA
        )

        selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        dayNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = dayNumberTextSize
            color = defTextColor
        }

        weekdayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = cr.weekdayTextSize
            typeface = Typeface.DEFAULT_BOLD
        }

        cellHoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    }

    private inline fun updateUIState(block: () -> Unit) {
        block()

        invalidate()
    }

    private fun updateSelectionRenderOptions() {
        selectionRenderOptions = SelectionRenderOptions(
            selectionFill,
            selectionFillGradientBoundsType,
            cellRoundRadius(),
            cellAnimationType
        )

        invalidate()
    }

    private fun updateSelectionStateConfiguration() {
        selectionManager.updateConfiguration(cellMeasureManager)

        invalidate()
    }

    fun setSelectionFill(fill: Fill) = updateUIState {
        selectionFill = fill

        updateGradientBoundsIfNeeded()

        updateSelectionRenderOptions()
    }

    fun setSelectionFillGradientBoundsType(value: SelectionFillGradientBoundsType) = updateUIState {
        selectionFillGradientBoundsType = value

        updateGradientBoundsIfNeeded()
        updateSelectionRenderOptions()
    }

    fun setSelectionManager(manager: SelectionManager?) = updateUIState {
        val resolvedManager = manager ?: DefaultSelectionManager()

        // DefaultSelectionManager has no options and preferences which means re-setting it has no effect.
        if(resolvedManager.javaClass == DefaultSelectionManager::class.java &&
            resolvedManager.javaClass == DefaultSelectionManager::class.java) {
            return
        }

        val prevSelState = selectionManager.currentState
        val selState = selectionManager.currentState

        resolvedManager.setState(
            prevSelState.type,
            prevSelState.rangeStart,
            prevSelState.rangeEnd,
            cellMeasureManager
        )
        resolvedManager.setState(
            selState.type,
            selState.rangeStart,
            selState.rangeEnd,
            cellMeasureManager
        )

        selectionManager = resolvedManager
    }

    fun setCellAnimationType(type: CellAnimationType) = updateUIState {
        cellAnimationType = type

        updateSelectionRenderOptions()
    }

    fun setDayNumberTextSize(size: Float) = updateUIState {
        dayNumberPaint.textSize = size

        if (size != cr.dayNumberTextSize) {
            isDayNumberMeasurementsDirty = true
        }

        refreshAllDecorVisualStates()
    }

    fun setWeekdayTextSize(size: Float) = updateUIState {
        weekdayPaint.textSize = size

        if (size != cr.weekdayTextSize) {
            isWeekdayMeasurementsDirty = true
        }

        onGridTopChanged()
    }

    fun setStyleColorInt(index: Int, @ColorInt color: Int) = updateUIState {
        styleColors.setColorInt(index, color)
    }

    @RequiresApi(26)
    fun setStyleColorLong(index: Int, @ColorLong color: Long) = updateUIState {
        styleColors.setColorLong(index, color)
    }

    fun setStyleColors(colors: CompatColorArray) {
        styleColors.copyFrom(colors)
    }

    fun getCellSize(): Float {
        return cellSize
    }

    fun setCellSize(size: Float) {
        require(size > 0f) { "size <= 0" }

        cellSize = size

        refreshAllDecorVisualStates()
        refreshColumnWidth()
        updateSelectionStateConfiguration()

        requestLayout()
    }

    fun setCellRoundRadius(value: Float) = updateUIState {
        require(value >= 0f) { "ratio < 0" }

        rrRadius = value

        refreshAllDecorVisualStates()
        updateSelectionRenderOptions()
    }

    fun setInMonthRange(range: CellRange) {
        inMonthRange = range

        updateSelectionRange()
    }

    fun setEnabledCellRange(range: CellRange) {
        enabledCellRange = range

        updateSelectionRange()
    }

    fun setWeekdayType(_type: WeekdayType) = updateUIState {
        // There is no narrow weekdays before API < 24, so we need to resolve it
        val type = _type.resolved()

        weekdayType = type

        // If weekdays measurements are from calendar resources then we can use precomputed values and don't make them "dirty"
        if (weekdayWidths === cr.defaultWeekdayWidths) {
            maxWeekdayHeight = if (type == WeekdayType.SHORT) {
                cr.defaultShortWeekdayRowHeight
            } else {
                cr.defaultNarrowWeekdayRowHeight
            }
        } else {
            // Widths and max height needs to be precomputed if they are not from calendar resources.
            isWeekdayMeasurementsDirty = true
        }

        onGridTopChanged()
    }

    fun setTodayCell(cell: Cell) {
        todayCell = cell
        invalidate()
    }

    private fun refreshColumnWidth() = updateUIState {
        val width = width.toFloat()

        columnWidth = (width - cr.hPadding * 2) * (1f / 7)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val preferredWidth = (cellSize * 7).toInt()
        val preferredHeight = (gridTop() + (cellSize + cr.yCellMargin) * 6).toInt()

        setMeasuredDimension(
            resolveSize(preferredWidth, widthMeasureSpec),
            resolveSize(preferredHeight, heightMeasureSpec)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        refreshColumnWidth()

        updateGradientBoundsIfNeeded()
        updateSelectionStateConfiguration()
    }

    private fun updateGradientBoundsIfNeeded() {
        if (selectionFillGradientBoundsType == SelectionFillGradientBoundsType.GRID) {
            val hPadding = cr.hPadding

            selectionFill.setBounds(
                hPadding, gridTop(), width - hPadding, height.toFloat()
            )
        }
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        return touchHelper.dispatchHoverEvent(event) && super.dispatchHoverEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val action = e.actionMasked
        val x = e.x
        val y = e.y

        if (y > gridTop()) {
            when (action) {
                MotionEvent.ACTION_DOWN -> if (isXInActiveZone(x)) {
                    val cell = getCellByPointOnScreen(x, y)

                    if (enabledCellRange.contains(cell)) {
                        val eTime = e.eventTime

                        val longPressTime = eTime + ViewConfiguration.getLongPressTimeout()
                        val hoverTime = eTime + HOVER_DELAY

                        val msg1 = Message.obtain().apply {
                            what = MSG_LONG_PRESS
                            arg1 = cell.index
                        }
                        val msg2 = Message.obtain().apply {
                            what = MSG_HOVER_PRESS
                            arg1 = cell.index
                        }

                        // Send messages in future to detect long-press or hover.
                        // If MotionEvent.ACTION_UP event happens, these messages will be cancelled.
                        //
                        // In other words, if these messages aren't cancelled (when pointer is up),
                        // then pointer is down long enough to treat this touch as long-press or
                        // to start hover.
                        // In pressTimeoutHandler.handleMessage we start long-press or hover,
                        // but pressTimeoutHandler.handleMessage won't be invoked we messages are cancelled
                        // (removed from queue).
                        pressTimeoutHandler.sendMessageAtTime(msg1, longPressTime)
                        pressTimeoutHandler.sendMessageAtTime(msg2, hoverTime)
                    }

                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    performClick()
                    if (!isSelectingCustomRange && isXInActiveZone(x)) {
                        val cell = getCellByPointOnScreen(x, y)
                        val touchTime = e.downTime

                        if (enabledCellRange.contains(cell)) {
                            if (lastTouchTime > 0 &&
                                touchTime - lastTouchTime < DOUBLE_TOUCH_MAX_MILLIS &&
                                lastTouchCell == cell
                            ) {
                                selectWeek(cell.gridY, doAnimation = true)
                            } else {
                                selectCell(cell, doAnimation = true, isUser = true)
                                sendClickEventToAccessibility(cell)

                                lastTouchCell = cell
                                lastTouchTime = touchTime
                            }
                        }
                    }

                    // Delete all messages from queue, long-press or hover may already happened or not.
                    pressTimeoutHandler.removeCallbacksAndMessages(null)

                    // Don't call clearHoverIndex() because it will start animation,
                    // but we don't need it, because we selected something else.
                    // Or we selected nothing, but in that hover won't happen and we don't need animation too.
                    hoverCell = Cell.Undefined

                    stopSelectingCustomRange()

                    // There's something to update.
                    invalidate()
                }
                MotionEvent.ACTION_CANCEL -> {
                    // Delete all messages from queue, long-press or hover may already happened or not.
                    pressTimeoutHandler.removeCallbacksAndMessages(null)

                    // If event is cancelled, then we don't select anything and animation is necessary.
                    clearHoverCellWithAnimation()
                    stopSelectingCustomRange()

                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isSelectingCustomRange && isXInActiveZone(x) && y > gridTop()) {
                        parent?.requestDisallowInterceptTouchEvent(true)

                        val cell = getCellByPointOnScreen(x, y)
                        val newRange = CellRange(customRangeStartCell, cell).normalize()

                        selectCustom(newRange, false)

                        invalidate()
                    }
                }
            }
        }

        return true
    }

    private fun stopSelectingCustomRange() {
        customRangeStartCell = Cell.Undefined
        isSelectingCustomRange = false
    }

    private fun onCellLongPress(cell: Cell) {
        customRangeStartCell = cell
        isSelectingCustomRange = true

        selectCustom(CellRange.cell(cell), true)
    }

    override fun getFocusedRect(r: Rect) {
        val size = cellSize
        val selState = selectionManager.currentState

        when (selState.type) {
            SelectionType.CELL -> {
                val (left, top) = getCellLeftTop(selState.startCell)

                r.set(left.toInt(), top.toInt(), (left + size).toInt(), (top + size).toInt())
            }
            SelectionType.WEEK -> {
                val start = Cell(selState.rangeStart)
                val end = Cell(selState.rangeEnd)

                val (left, top) = getCellLeftTop(start)
                val right = getCellRight(end)

                r.set(left.toInt(), top.toInt(), right.toInt(), (top + size).toInt())
            }

            else -> super.getFocusedRect(r)
        }
    }

    private fun sendClickEventToAccessibility(cell: Cell) {
        touchHelper.sendEventForVirtualView(cell.index, AccessibilityEvent.TYPE_VIEW_CLICKED)
    }

    private fun updateSelectionRange() {
        val selState = selectionManager.currentState

        when (selState.type) {
            SelectionType.CELL -> {
                // If type is CELL, then rangeStart and rangeEnd should point to the same cell.
                if (!enabledCellRange.contains(selState.rangeStart)) {
                    clearSelection(fireEvent = true, doAnimation = true)
                }
            }
            SelectionType.WEEK -> {
                // If type is WEEK, then rangeStart and rangeEnd should be on the same row.
                val weekIndex = selState.rangeStart / 7

                selectWeek(weekIndex, true)
            }
            SelectionType.MONTH -> {
                selectMonth(doAnimation = true, reselect = true)
            }
            SelectionType.CUSTOM -> {
                selectCustom(selState.range, false)
            }
            else -> {}
        }
    }

    fun select(info: SetSelectionInfo) {
        select(info.type, info.data, info.withAnimation)
    }

    fun select(type: SelectionType, data: NarrowSelectionData, doAnimation: Boolean) {
        when (type) {
            SelectionType.CELL -> selectCell(data.cell, doAnimation)
            SelectionType.WEEK -> selectWeek(data.weekIndex, doAnimation)
            SelectionType.MONTH -> selectMonth(doAnimation)
            SelectionType.CUSTOM -> selectCustom(data.range, startSelecting = false, doAnimation)
            else -> {}
        }
    }

    private fun selectCell(cell: Cell, doAnimation: Boolean, isUser: Boolean = false) {
        val selState = selectionManager.currentState

        // If selection type is CELL, then rangeStart and rangeEnd should point to the same cell.
        if (selState.type == SelectionType.CELL && selState.rangeStart == cell.index) {
            // If it was user and behavior on selecting the same cell is CLEAR,
            // then we need to do it.
            if (isUser && clickOnCellSelectionBehavior == ClickOnCellSelectionBehavior.CLEAR) {
                clearSelection(fireEvent = true, doAnimation = true)
            }

            return
        }

        // Check if cell is enabled
        if (!enabledCellRange.contains(cell)) {
            return
        }

        // Call listeners and check if such selection is allowed.
        val gate = selectionGate
        if (gate != null) {
            if (!gate.cell(cell)) {
                return
            }
        }

        onSelectionListener?.onCellSelected(cell)

        if (hoverCell.isDefined) {
            clearHoverCellWithAnimation()
        }

        selectionManager.setState(
            SelectionType.CELL,
            cell, cell,
            cellMeasureManager
        )

        if (doAnimation && selectionManager.hasTransition()) {
            startCalendarAnimation(SELECTION_ANIMATION)
        } else {
            invalidate()
        }
    }

    private fun selectWeek(weekIndex: Int, doAnimation: Boolean) {
        // 1. Resolve week range.
        val weekRange = CellRange.week(weekIndex).intersectionWith(enabledCellRange)
        if (weekRange == CellRange.Invalid) {
            return
        }

        val selState = selectionManager.currentState
        val (start, end) = weekRange

        // 2. If we're selecting the same range or range is cell-type, stop it or redirect to selectCell().
        if (selState.type == SelectionType.WEEK && selState.range == weekRange) {
            clearSelection(fireEvent = true, doAnimation)

            return
        } else if (end == start) {
            selectCell(start, doAnimation)

            return
        }

        // 3. Call listeners and check if such selection is allowed.
        val gate = selectionGate
        if (gate != null) {
            if (!gate.week(weekIndex, weekRange)) {
                return
            }
        }

        onSelectionListener?.onWeekSelected(weekIndex, weekRange)

        selectionManager.setState(
            SelectionType.WEEK,
            start, end,
            cellMeasureManager,
        )

        // 4. Start appropriate animation if it's necessary.
        if (doAnimation && selectionManager.hasTransition()) {
            startCalendarAnimation(SELECTION_ANIMATION)
        } else {
            invalidate()
        }
    }

    fun selectMonth(doAnimation: Boolean, reselect: Boolean = false) {
        val selState = selectionManager.currentState

        if (!reselect && selState.type == SelectionType.MONTH) {
            return
        }

        val intersection = inMonthRange.intersectionWith(enabledCellRange)

        // If there's no intersection, clear path and previous selection.
        if (intersection == CellRange.Invalid) {
            clearSelection(fireEvent = true, doAnimation)

            return
        } else if (selState.type == SelectionType.MONTH && selState.range == intersection) {
            return
        }

        selectionManager.setState(SelectionType.MONTH, intersection, cellMeasureManager)

        // Start appropriate animation if it's necessary.
        if (doAnimation && selectionManager.hasTransition()) {
            startCalendarAnimation(SELECTION_ANIMATION)
        } else {
            invalidate()
        }
    }

    fun selectCustom(range: CellRange, startSelecting: Boolean, doAnimation: Boolean = true) {
        val gate = selectionGate
        if (gate != null) {
            if (!gate.customRange(range)) {
                return
            }
        }

        onSelectionListener?.onCustomRangeSelected(range)

        if (vibrateOnSelectingCustomRange && startSelecting) {
            if (vibrator == null) {
                vibrator = getVibrator(context)

                // If vibrator is null then vibrationEffect is too.
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrationEffect = VibrationEffect.createOneShot(
                        VIBRATE_DURATION,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                }
            }

            vibrator?.let {
                if (Build.VERSION.SDK_INT >= 26) {
                    it.vibrate(vibrationEffect)
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(VIBRATE_DURATION)
                }
            }
        }

        // Clear hover here and not in onCellLongPress() because custom range might be disallowed and
        // hover will be cleared but it shouldn't.
        clearHoverCellWithAnimation()

        val selState = selectionManager.currentState
        val intersection = range.intersectionWith(enabledCellRange)

        if (intersection == CellRange.Invalid) {
            clearSelection(fireEvent = true, doAnimation = true)

            return
        } else if (selState.range == intersection) {
            return
        }

        selectionManager.setState(SelectionType.CUSTOM, intersection, cellMeasureManager)

        if(doAnimation && selectionManager.hasTransition()) {
            startCalendarAnimation(SELECTION_ANIMATION)
        } else {
            invalidate()
        }
    }

    private fun setHoverCell(cell: Cell) {
        val selState = selectionManager.currentState
        if ((selState.type == SelectionType.CELL && selState.rangeStart == cell.index) || hoverCell == cell) {
            return
        }

        animationHoverCell = cell
        hoverCell = cell

        startCalendarAnimation(HOVER_ANIMATION)
    }

    fun clearHoverCellWithAnimation() {
        if (hoverCell.isDefined) {
            hoverCell = Cell.Undefined

            startCalendarAnimation(HOVER_ANIMATION or ANIMATION_REVERSE_BIT)
        }
    }

    fun clearSelection(fireEvent: Boolean, doAnimation: Boolean) {
        // No sense to clear selection if there's none.
        if (selectionManager.currentState.type == SelectionType.NONE) {
            return
        }

        selectionManager.setNoneState()

        // Fire event if it's demanded
        val listener = onSelectionListener
        if (fireEvent && listener != null) {
            listener.onSelectionCleared()
        }

        if (doAnimation) {
            startCalendarAnimation(SELECTION_ANIMATION)
        } else {
            invalidate()
        }
    }

    private fun createDecorVisualState(
        stateHandler: CellDecor.VisualStateHandler,
        cell: Cell
    ): CellDecor.VisualState {
        val decors = decorations!!

        val subregion = decors.getSubregion(decorRegion, cell)

        if (subregion.isUndefined) {
            return stateHandler.emptyState()
        }

        val halfCellSize = cellSize * 0.5f

        val cellTextSize = getDayNumberSize(cell)
        val halfCellTextWidth = cellTextSize.width * 0.5f
        val halfCellTextHeight = cellTextSize.height * 0.5f

        cellInfo.apply {
            size = cellSize
            radius = cellRoundRadius()
            layoutOptions = decorLayoutOptionsArray[cell] ?: decorDefaultLayoutOptions

            setTextBounds(
                halfCellSize - halfCellTextWidth,
                halfCellSize - halfCellTextHeight,
                halfCellSize + halfCellTextWidth,
                halfCellSize + halfCellTextHeight
            )
        }

        return stateHandler.createState(
            context,
            decors.elements,
            subregion.start, subregion.endInclusive,
            cellInfo
        )
    }

    private fun refreshAllDecorVisualStates() {
        measureDayNumberTextSizesIfNecessary()

        decorVisualStates.forEachNotNull { cell, value ->
            val stateHandler = value.visual().stateHandler()

            decorVisualStates[cell] = createDecorVisualState(stateHandler, cell)
        }
    }

    // Should be called on calendar's decors initialization
    fun setDecorationDefaultLayoutOptions(options: DecorLayoutOptions?) {
        decorDefaultLayoutOptions = options

        decorVisualStates.forEachNotNull { cell, value ->
            val endState = createDecorVisualState(value.visual().stateHandler(), cell)

            decorVisualStates[cell] = endState
        }

        invalidate()
    }

    fun setDecorationLayoutOptions(
        cell: Cell,
        options: DecorLayoutOptions,
        withAnimation: Boolean
    ) {
        val decors = decorations!!

        val region = decors.getSubregion(decorRegion, cell)
        if (region.isUndefined) {
            return
        }

        val stateHandler = decors[region.start].visual().stateHandler()
        val startState = decorVisualStates[cell] ?: stateHandler.emptyState()

        decorLayoutOptionsArray[cell] = options

        val endState = createDecorVisualState(stateHandler, cell)

        if (withAnimation) {
            decorVisualStates[cell] = stateHandler.createTransitiveState(
                startState, endState,
                region.start, region.endInclusive,
                CellDecor.VisualStateChange.CELL_INFO
            )

            startCalendarAnimation(
                DECOR_ANIMATION,
                handler = getDecorAnimationHandler(),
                onEnd = {
                    val transitive = decorVisualStates[cell] as CellDecor.VisualState.Transitive

                    decorVisualStates[cell] = transitive.end
                }
            )
        } else {
            decorVisualStates[cell] = endState

            invalidate()
        }
    }

    // Common setDecorationLayoutOptions() updates visual states.
    // Default layout options is set here to avoid double-update of the states.
    fun onDecorInit(
        newDecorRegion: PackedIntRange,
        defaultLayoutOptions: DecorLayoutOptions?
    ) {
        decorRegion = newDecorRegion
        decorDefaultLayoutOptions = defaultLayoutOptions

        val decors = decorations!!

        decorVisualStates.clear()
        decorLayoutOptionsArray.clear()

        decors.iterateSubregions(newDecorRegion) {
            val instance = decors[it.start]
            val stateHandler = instance.visual().stateHandler()
            val cell = instance.cell

            decorVisualStates[cell] = createDecorVisualState(stateHandler, cell)
        }

        invalidate()
    }

    fun onDecorAdded(
        newDecorRegion: PackedIntRange,
        affectedRange: PackedIntRange,
        fractionInterpolator: DecorAnimationFractionInterpolator?
    ) {
        decorRegion = newDecorRegion

        val decors = decorations!!

        val instanceDecor = decors[affectedRange.start]

        val stateHandler = instanceDecor.visual().stateHandler()
        val cell = instanceDecor.cell
        val endState = createDecorVisualState(stateHandler, cell)

        if (fractionInterpolator != null) {
            decorAnimatedRange = affectedRange
            decorAnimatedCell = cell
            decorAnimFractionInterpolator = fractionInterpolator

            val startState = decorVisualStates[cell] ?: stateHandler.emptyState()

            decorVisualStates[cell] = stateHandler.createTransitiveState(
                startState, endState,
                affectedRange.start, affectedRange.endInclusive,
                CellDecor.VisualStateChange.ADD
            )

            startCalendarAnimation(
                DECOR_ANIMATION,
                handler = getDecorAnimationHandler(),
                onEnd = {
                    val transitive = decorVisualStates[cell] as CellDecor.VisualState.Transitive

                    decorVisualStates[cell] = transitive.end
                }
            )
        } else {
            decorVisualStates[cell] = endState

            invalidate()
        }
    }

    fun onDecorRemoved(
        newDecorRegion: PackedIntRange,
        affectedRange: PackedIntRange,
        cell: Cell,
        visual: CellDecor.Visual,
        fractionInterpolator: DecorAnimationFractionInterpolator?
    ) {
        decorRegion = newDecorRegion

        val stateHandler = visual.stateHandler()

        if (fractionInterpolator != null) {
            decorAnimatedRange = affectedRange
            decorAnimatedCell = cell
            decorAnimFractionInterpolator = fractionInterpolator

            val startState = decorVisualStates[cell]!!

            val endState = createDecorVisualState(stateHandler, cell)

            decorVisualStates[cell] = stateHandler.createTransitiveState(
                startState, endState,
                affectedRange.start, affectedRange.endInclusive,
                CellDecor.VisualStateChange.REMOVE
            )

            startCalendarAnimation(
                DECOR_ANIMATION,
                handler = getDecorAnimationHandler(),
                onEnd = {
                    val transitive = decorVisualStates[cell] as CellDecor.VisualState.Transitive

                    updateDecorVisualStateOnRemove(cell, transitive.end)
                }
            )
        } else {
            updateDecorVisualStateOnRemove(cell, createDecorVisualState(stateHandler, cell))

            invalidate()
        }
    }

    private fun updateDecorVisualStateOnRemove(cell: Cell, endState: CellDecor.VisualState) {
        decorVisualStates[cell] = if (endState.isEmpty) null else endState
    }

    // It could be startAnimation(), but this name would interfere with View's startAnimation(Animation)
    private fun startCalendarAnimation(
        type: Int,
        handler: (() -> Unit)? = null,
        onEnd: Runnable? = null
    ) {
        var animator = animator
        if (animator != null && animator.isRunning) {
            animator.end()
        }

        animType = type
        onAnimationEnd = onEnd
        animationHandler = handler

        if (animator == null) {
            animator = AnimationHelper.createFractionAnimator { value: Float ->
                animFraction = value
                animationHandler?.invoke()

                invalidate()
            }

            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    animType = NO_ANIMATION
                    onAnimationEnd?.run()

                    Log.i("Calendar", "onAnimationEnd()")
                    invalidate()
                }
            })

            this.animator = animator
        }

        if (type and ANIMATION_DATA_MASK == HOVER_ANIMATION) {
            animator.duration = hoverAnimationDuration.toLong()
            animator.interpolator = hoverAnimationInterpolator
        } else {
            animator.duration = commonAnimationDuration.toLong()
            animator.interpolator = commonAnimationInterpolator
        }

        if ((type and ANIMATION_REVERSE_BIT) != 0) {
            animator.reverse()
        } else {
            animator.start()
        }
    }

    fun onGridChanged() {
        invalidate()
        touchHelper.invalidateRoot()
    }

    public override fun onDraw(c: Canvas) {
        // No matter where is it, either way weekday row doesn't overlap anything.
        drawWeekdayRow(c)

        // Order is important here:
        // - Cells should be on top of everything and that's why it's the last one,
        //   selection or hover shouldn't overlap text.
        // - Then hover, it should overlap selection but not cells
        // - Finally (first), selection it should be on bottom of everything.

        drawSelection(c)
        drawHover(c)
        drawDecorations(c)
        drawCells(c)
    }

    private fun drawSelection(c: Canvas) {
        val manager = selectionManager
        val options = selectionRenderOptions

        if ((animType and ANIMATION_DATA_MASK) == SELECTION_ANIMATION) {
            Log.i("Calendar", "drawSelection(): animation")
            manager.drawTransition(
                c,
                cellMeasureManager,
                options,
                animFraction
            )
        } else {
            Log.i("Calendar", "drawSelection(): non-animation")

            manager.draw(c, options)
        }
    }

    private fun drawWeekdayRow(c: Canvas) {
        styleColors.initPaintColor(COLOR_STYLE_WEEKDAY, weekdayPaint)

        if (isWeekdayMeasurementsDirty) {
            isWeekdayMeasurementsDirty = false

            measureWeekdays()
        }

        var x = cr.hPadding + columnWidth * 0.5f

        val offset = if (weekdayType == WeekdayType.SHORT)
            CalendarResources.SHORT_WEEKDAYS_OFFSET
        else
            CalendarResources.NARROW_WEEKDAYS_OFFSET

        val startIndex = if (isFirstDaySunday) 0 else 1

        // If widths are from calendar resources, then we do not need any widths-offset to get the size,
        // because it contains both short and narrow (if API level >= 24) weekday widths.
        // But if the widths are recomputed, not from calendar resources,
        // then it contains either short or narrow (if API level >= 24) weekday widths and
        // we need to shift the index.
        val widthsOffset = if (weekdayWidths === cr.defaultWeekdayWidths) 0 else offset

        for (i in offset + startIndex until offset + 7) {
            drawWeekday(c, i, widthsOffset, x)

            x += columnWidth
        }

        if (!isFirstDaySunday) {
            drawWeekday(c, offset, widthsOffset, x)
        }
    }

    private fun drawWeekday(c: Canvas, index: Int, widthsOffset: Int, midX: Float) {
        val textX = midX - weekdayWidths[index - widthsOffset] * 0.5f
        val textY = maxWeekdayHeight

        c.drawText(cr.weekdays[index], textX, textY, weekdayPaint)
    }

    private fun measureWeekdays() {
        val offset = if (weekdayType == WeekdayType.SHORT) 0 else 7

        var maxHeight = -1

        // If weekdays are from calendar resources,
        // then create new array to not overwrite the resources' one.
        if (weekdayWidths === cr.defaultWeekdayWidths) {
            weekdayWidths = FloatArray(7)
        }

        weekdayPaint.getTextBoundsArray(cr.weekdays, offset, offset + 7) { i, size ->
            val height = size.height
            if (height > maxHeight) {
                maxHeight = height
            }

            weekdayWidths[i] = size.width.toFloat()
        }

        maxWeekdayHeight = maxHeight.toFloat()
    }

    private fun drawHover(c: Canvas) {
        val isHoverAnimation = (animType and ANIMATION_DATA_MASK) == HOVER_ANIMATION

        if ((isHoverAnimation && animationHoverCell.isDefined) || hoverCell.isDefined) {
            val cell = if (isHoverAnimation) animationHoverCell else hoverCell
            val (left, top) = getCellLeftTop(cell)

            val index = if (isSelectionRangeContains(cell)) {
                COLOR_STYLE_HOVER_ON_SELECTION
            } else {
                COLOR_STYLE_HOVER
            }

            styleColors.initPaintColor(
                index,
                if (isHoverAnimation) animFraction else 1f,
                cellHoverPaint
            )

            c.drawRoundRectCompat(
                left, top, left + cellSize, top + cellSize,
                cellRoundRadius(), cellHoverPaint
            )
        }
    }

    private fun drawCells(c: Canvas) {
        measureDayNumberTextSizesIfNecessary()

        val halfCellSize = cellSize * 0.5f

        for (i in 0 until 42) {
            val cell = Cell(i)

            val centerX = getCellCenterLeft(cell)
            val centerY = getCellTop(cell) + halfCellSize

            drawCell(c, centerX, centerY, cells[i].toInt(), resolveCellType(cell))
        }
    }

    private fun resolveCellType(cell: Cell): Int {
        val selState = selectionManager.currentState

        var cellType =
            if (selState.type == SelectionType.CELL && selState.rangeStart == cell.index) {
                CELL_SELECTED
            } else if (enabledCellRange.contains(cell)) {
                if (inMonthRange.contains(cell)) {
                    if (cell == todayCell) {
                        if (isSelectionRangeContains(cell)) {
                            CELL_IN_MONTH
                        } else {
                            CELL_TODAY
                        }
                    } else {
                        CELL_IN_MONTH
                    }
                } else {
                    CELL_OUT_MONTH
                }
            } else {
                CELL_DISABLED
            }

        if (cell == hoverCell) {
            cellType = cellType or CELL_HOVER_BIT
        }

        return cellType
    }

    private fun cellTypeToColorStyle(type: Int) = when (type and CELL_DATA_MASK) {
        CELL_SELECTED, CELL_IN_MONTH -> COLOR_STYLE_IN_MONTH
        CELL_OUT_MONTH -> COLOR_STYLE_OUT_MONTH
        CELL_DISABLED -> COLOR_STYLE_DISABLED
        CELL_TODAY -> if ((type and CELL_HOVER_BIT) != 0)
            COLOR_STYLE_IN_MONTH
        else
            COLOR_STYLE_TODAY
        else -> throw IllegalArgumentException("type")
    }

    private fun drawCell(c: Canvas, centerX: Float, centerY: Float, day: Int, cellType: Int) {
        if (day > 0) {
            val colorStyle = cellTypeToColorStyle(cellType)

            val textSize = getDayNumberSize(day)

            val textX = centerX - textSize.width * 0.5f
            val textY = centerY + textSize.height * 0.5f

            styleColors.initPaintColor(colorStyle, dayNumberPaint)

            c.drawText(CalendarResources.getDayText(day), textX, textY, dayNumberPaint)
        }
    }

    private fun measureDayNumberTextSizesIfNecessary() {
        if (isDayNumberMeasurementsDirty) {
            isDayNumberMeasurementsDirty = false

            var sizes = dayNumberSizes
            if (sizes.array === cr.defaultDayNumberSizes.array) {
                sizes = PackedSizeArray(31)
                dayNumberSizes = sizes
            }

            dayNumberPaint.getTextBoundsArray(CalendarResources.DAYS, 0, 31) { i, size ->
                sizes[i] = size
            }
        }
    }

    private fun getDecorAnimationHandler(): () -> Unit {
        return getLazyValue(
            decorAnimationHandler,
            { this::handleDecorationAnimation },
            { decorAnimationHandler = it }
        )
    }

    private fun handleDecorationAnimation() {
        if ((animType and ANIMATION_DATA_MASK) == DECOR_ANIMATION) {
            val state = decorVisualStates[decorAnimatedCell]

            if (state is CellDecor.VisualState.Transitive) {
                state.handleAnimation(animFraction, decorAnimFractionInterpolator!!)
            }
        }
    }

    private fun drawDecorations(c: Canvas) {
        decorVisualStates.forEachNotNull { cell, state ->
            val (dx, dy) = getCellLeftTop(cell)

            c.translate(dx, dy)
            state.visual().renderer().renderState(c, state)
            c.translate(-dx, -dy)
        }
    }

    private fun onGridTopChanged() {
        updateGradientBoundsIfNeeded()
        updateSelectionStateConfiguration()

        // y-axis of entries depends on type of weekday, so we need to refresh accessibility info
        touchHelper.invalidateRoot()
    }

    private fun gridTop(): Float {
        return maxWeekdayHeight + cr.weekdayRowMarginBottom
    }

    // It'd be better if cellRoundRadius() returns round radius that isn't greater than half of cell size.
    private fun cellRoundRadius(): Float {
        val halfCellSize = cellSize * 0.5f

        return if (rrRadius > halfCellSize) halfCellSize else rrRadius
    }

    private fun firstCellLeft(): Float {
        return cr.hPadding + (columnWidth - cellSize) * 0.5f
    }

    private fun getCellLeft(cell: Cell): Float {
        return getCellCenterLeft(cell) - cellSize * 0.5f
    }

    private fun getCellCenterLeft(cell: Cell): Float {
        return cr.hPadding + columnWidth * (cell.gridX + 0.5f)
    }

    private fun getCellTop(cell: Cell): Float {
        return gridTop() + cell.gridY * (cellSize + cr.yCellMargin)
    }

    private fun getCellCenterTop(cell: Cell): Float {
        return getCellTop(cell) + cellSize * 0.5f
    }

    private fun getCellLeftTop(cell: Cell): PackedPointF {
        return PackedPointF(getCellLeft(cell), getCellTop(cell))
    }

    private fun getCellCenter(cell: Cell): PackedPointF {
        return PackedPointF(getCellCenterLeft(cell), getCellCenterTop(cell))
    }

    private fun getCellRight(cell: Cell): Float {
        return getCellCenterLeft(cell) + cellSize * 0.5f
    }

    private fun lerpCellLeft(start: Cell, end: Cell, fraction: Float, outPoint: PointF) {
        val cellF = lerp(start.index.toFloat(), end.index.toFloat(), fraction)

        val currentCell = Cell(cellF.toInt())
        val currentCellLeft = getCellLeft(currentCell)
        val nextCell = currentCell + 1

        // If currentCell's and nextCell's rows are different, then we should end this part of 'lerp' in the end of the current cell.
        val nextCellLeft = if(nextCell.index % 7 == 0) {
            currentCellLeft + cellSize
        } else {
            getCellLeft(nextCell)
        }

        val leftover = cellF - currentCell.index.toFloat()

        outPoint.x = lerp(currentCellLeft, nextCellLeft, leftover)
        outPoint.y = getCellTop(currentCell)
    }

    private fun lerpCellRight(start: Cell, end: Cell, fraction: Float, outPoint: PointF) {
        val cellF = lerp(start.index.toFloat(), end.index.toFloat(), fraction)

        val currentCell = Cell(cellF.toInt())
        val nextCell = currentCell + 1

        // Specifies whether currentCell's and nextCell's rows are different.
        val isDiffRow = nextCell.index % 7 == 0

        // If the rows are different, then we should start this part of 'lerp' from next row's first cell.
        val currentCellRight = if(isDiffRow) {
            firstCellLeft()
        } else {
            getCellRight(currentCell)
        }

        val nextCellRight = getCellRight(nextCell)

        val leftover = cellF - currentCell.index.toFloat()

        outPoint.x = lerp(currentCellRight, nextCellRight, leftover)

        // If the rows are different, then we should start this part of 'lerp' from next row.
        outPoint.y = getCellTop(if(isDiffRow) nextCell else currentCell)
    }

    private fun isSelectionRangeContains(cell: Cell): Boolean {
        val selState = selectionManager.currentState
        val selType = selState.type

        return selType != SelectionType.NONE &&
                selState.range.contains(cell)
    }

    fun getCellByPointOnScreen(x: Float, y: Float): Cell {
        val gridX = ((x - cr.hPadding) / columnWidth).toInt()
        val gridY = ((y - gridTop()) / (cellSize + cr.yCellMargin)).toInt()

        return Cell(gridY * 7 + gridX)
    }

    // Checks whether x in active (touchable) zone
    private fun isXInActiveZone(x: Float): Boolean {
        return x >= cr.hPadding && x <= width - cr.hPadding
    }

    private fun getDayNumberSize(day: Int) = dayNumberSizes[day - 1]
    private fun getDayNumberSize(cell: Cell) = getDayNumberSize(cells[cell.index].toInt())

    companion object {
        const val COLOR_STYLE_IN_MONTH = 0
        const val COLOR_STYLE_OUT_MONTH = 1
        const val COLOR_STYLE_DISABLED = 2
        const val COLOR_STYLE_TODAY = 3
        const val COLOR_STYLE_HOVER = 4
        const val COLOR_STYLE_HOVER_ON_SELECTION = 5
        const val COLOR_STYLE_WEEKDAY = 6

        private const val MSG_LONG_PRESS = 0
        private const val MSG_HOVER_PRESS = 1

        private const val CELL_IN_MONTH = 0
        private const val CELL_OUT_MONTH = 1
        private const val CELL_SELECTED = 2
        private const val CELL_DISABLED = 3
        private const val CELL_TODAY = 4
        private const val CELL_HOVER_BIT = 1 shl 31
        private const val CELL_DATA_MASK = CELL_HOVER_BIT.inv()

        private val ALL_SELECTED = CellRange(0, 42)

        const val DEFAULT_COMMON_ANIM_DURATION = 250
        const val DEFAULT_HOVER_ANIM_DURATION = 100
        private val HOVER_DELAY = ViewConfiguration.getTapTimeout()

        private const val DOUBLE_TOUCH_MAX_MILLIS: Long = 500
        private const val VIBRATE_DURATION = 50L
        private const val TAG = "RangeCalendarGridView"

        private const val ANIMATION_REVERSE_BIT = 1 shl 31
        private const val ANIMATION_DATA_MASK = ANIMATION_REVERSE_BIT.inv()

        private const val NO_ANIMATION = 0
        private const val SELECTION_ANIMATION = 1
        private const val HOVER_ANIMATION = 2
        private const val DECOR_ANIMATION = 3

        private fun getVibrator(context: Context): Vibrator {
            return if (Build.VERSION.SDK_INT >= 31) {
                val vibratorManager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager

                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        }
    }
}