package com.github.pelmenstar1.rangecalendar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Typeface
import android.os.*
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.github.pelmenstar1.rangecalendar.decoration.*
import com.github.pelmenstar1.rangecalendar.selection.*
import com.github.pelmenstar1.rangecalendar.utils.VibratorCompat
import com.github.pelmenstar1.rangecalendar.utils.ceilToInt
import com.github.pelmenstar1.rangecalendar.utils.drawRoundRectCompat
import com.github.pelmenstar1.rangecalendar.utils.getLazyValue
import com.github.pelmenstar1.rangecalendar.utils.getTextBoundsArray
import com.github.pelmenstar1.rangecalendar.utils.withCombinedAlpha
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.min

// It will never be XML layout, so there's no need to match conventions
@SuppressLint("ViewConstructor")
internal class RangeCalendarGridView(context: Context, val cr: CalendarResources) : View(context) {
    interface OnSelectionListener {
        fun onSelectionCleared()
        fun onSelection(range: CellRange)
    }

    interface SelectionGate {
        fun range(range: CellRange): Boolean
    }

    private class TouchHelper(private val grid: RangeCalendarGridView) :
        ExploreByTouchHelper(grid) {
        private val tempCalendar = Calendar.getInstance()
        private val tempRect = Rect()

        override fun getVirtualViewAt(x: Float, y: Float): Int {
            return if (grid.isXInActiveZone(x) && y > grid.gridTop()) {
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

            tempRect.set(
                x.toInt(),
                y.toInt(),
                (x + grid.cellWidth).toInt(),
                (y + grid.cellHeight).toInt()
            )

            node.apply {
                @Suppress("DEPRECATION")
                setBoundsInParent(tempRect)

                contentDescription = getDayDescriptionForIndex(virtualViewId)
                text = CalendarResources.getDayText(grid.cells[virtualViewId].toInt())

                isSelected = grid.selectionManager.currentState.isSingleCell(cell)
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
                grid.selectRange(
                    range = CellRange.single(virtualViewId),
                    requestRejectedBehaviour = SelectionRequestRejectedBehaviour.PRESERVE_CURRENT_SELECTION,
                    isCellSelectionByUser = true,
                    isUserStartSelection = false,
                    doAnimation = false
                )

                true
            } else false
        }

        private fun getDayDescriptionForIndex(index: Int): CharSequence {
            val provider = grid.cellAccessibilityInfoProvider
                ?: throw RuntimeException("cellAccessibilityInfoProvider should not be null")

            val (monthStart, monthEnd) = grid.inMonthRange

            var ym = grid.ym
            if (index < monthStart.index) {
                ym -= 1
            } else if (index > monthEnd.index) {
                ym += 1
            }

            val day = grid.cells[index].toInt()

            return provider.getContentDescription(ym.year, ym.month, day)
        }

        companion object {
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

    private class CellMeasureManagerImpl(private val view: RangeCalendarGridView) :
        CellMeasureManager {
        override val cellWidth: Float
            get() = view.cellWidth

        override val cellHeight: Float
            get() = view.cellHeight

        override val roundRadius: Float
            get() = view.cellRoundRadius()

        override fun getCellLeft(cellIndex: Int): Float = view.getCellLeft(Cell(cellIndex))
        override fun getCellTop(cellIndex: Int): Float = view.getCellTop(Cell(cellIndex))

        override fun getCellDistance(cellIndex: Int): Float =
            view.getCellDistance(Cell(cellIndex))

        override fun getCellAndPointByDistance(distance: Float, outPoint: PointF): Int =
            view.getCellAndPointByCellDistance(distance, outPoint)
    }

    val cells = ByteArray(42)

    private var cellWidth: Float = cr.cellSize
    private var cellHeight: Float = cr.cellSize

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

    private var inMonthTextColor: Int
    private var outMonthTextColor: Int
    private var disabledCellTextColor: Int
    private var todayCellTextColor: Int
    private var hoverCellBgColor: Int
    private var hoverOnSelectionBgColor: Int

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

    private var selectionTransitionHandler: (() -> Unit)? = null
    private var onSelectionTransitionEnd: (() -> Unit)? = null

    private var selectionTransitiveState: SelectionState.Transitive? = null

    private var selectionManager: SelectionManager = DefaultSelectionManager()
    private var selectionRenderer = selectionManager.renderer
    private var selectionRenderOptions: SelectionRenderOptions

    private var selectionFill: Fill
    private var selectionFillGradientBoundsType = SelectionFillGradientBoundsType.GRID

    private val cellMeasureManager = CellMeasureManagerImpl(this)

    private var customRangeStartCell = Cell.Undefined
    private var isSelectingCustomRange = false

    private var animType = 0
    private var animFraction = 0f
    private var animator: ValueAnimator? = null
    private var onAnimationEnd: (() -> Unit)? = null
    private var animationHandler: (() -> Unit)? = null

    private val touchHelper: TouchHelper

    private val weekdayRow: WeekdayRow

    private var isDayNumberMeasurementsDirty = false
    private var dayNumberSizes = cr.defaultDayNumberSizes

    var clickOnCellSelectionBehavior = ClickOnCellSelectionBehavior.NONE
    var commonAnimationDuration = DEFAULT_COMMON_ANIM_DURATION
    var hoverAnimationDuration = DEFAULT_HOVER_ANIM_DURATION
    var commonAnimationInterpolator: TimeInterpolator = LINEAR_INTERPOLATOR
    var hoverAnimationInterpolator: TimeInterpolator = LINEAR_INTERPOLATOR

    private var cellAnimationType = CellAnimationType.ALPHA

    private val vibrator = VibratorCompat(context)

    var vibrateOnSelectingCustomRange = true

    private var showAdjacentMonths = true

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

    private var cellAccessibilityInfoProvider: RangeCalendarCellAccessibilityInfoProvider? = null

    init {
        val defTextColor = cr.textColor
        val colorPrimary = cr.colorPrimary
        val dayNumberTextSize = cr.dayNumberTextSize

        touchHelper = TouchHelper(this)
        ViewCompat.setAccessibilityDelegate(this, touchHelper)

        inMonthTextColor = defTextColor
        outMonthTextColor = cr.outMonthTextColor
        disabledCellTextColor = cr.disabledTextColor
        todayCellTextColor = colorPrimary
        hoverCellBgColor = cr.hoverColor
        hoverOnSelectionBgColor = cr.colorPrimaryDark

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
            color = defTextColor
        }

        cellHoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        weekdayRow = WeekdayRow(cr.defaultWeekdayData, weekdayPaint)
    }

    private inline fun updateUIState(block: () -> Unit) =
        updateUIState(condition = true, block)

    private inline fun <T> updateUIState(oldValue: T, newValue: T, block: () -> Unit) =
        updateUIState(oldValue != newValue, block)

    private inline fun updateUIState(oldValue: Float, newValue: Float, block: () -> Unit) =
        updateUIState(oldValue != newValue, block)

    private inline fun updateUIState(oldValue: Int, newValue: Int, block: () -> Unit) =
        updateUIState(oldValue != newValue, block)

    private inline fun updateUIState(condition: Boolean, block: () -> Unit) {
        if (condition) {
            block()

            invalidate()
        }
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

    fun setSelectionFillGradientBoundsType(value: SelectionFillGradientBoundsType) =
        updateUIState(selectionFillGradientBoundsType, value) {
            selectionFillGradientBoundsType = value

            updateGradientBoundsIfNeeded()
            updateSelectionRenderOptions()
        }

    fun setSelectionManager(manager: SelectionManager?) = updateUIState {
        val resolvedManager = manager ?: DefaultSelectionManager()

        // DefaultSelectionManager has no options and preferences which means re-setting it has no effect.
        if (resolvedManager.javaClass == DefaultSelectionManager::class.java &&
            resolvedManager.javaClass == DefaultSelectionManager::class.java
        ) {
            return
        }

        val prevSelState = selectionManager.previousState
        val selState = selectionManager.currentState

        resolvedManager.setStateOrNone(prevSelState)
        resolvedManager.setStateOrNone(selState)

        selectionManager = resolvedManager
        selectionRenderer = resolvedManager.renderer
    }

    private fun SelectionManager.setStateOrNone(state: SelectionState) {
        val rangeStart = state.rangeStart
        val rangeEnd = state.rangeEnd

        if (rangeStart > rangeEnd) {
            setNoneState()
        } else {
            setState(rangeStart, rangeEnd, cellMeasureManager)
        }
    }

    fun setCellAnimationType(type: CellAnimationType) = updateUIState(cellAnimationType, type) {
        cellAnimationType = type

        updateSelectionRenderOptions()
    }

    fun setDayNumberTextSize(size: Float) = updateUIState(dayNumberPaint.textSize, size) {
        dayNumberPaint.textSize = size

        if (size != cr.dayNumberTextSize) {
            isDayNumberMeasurementsDirty = true
        }

        refreshAllDecorVisualStates()
    }

    fun setWeekdayTextSize(size: Float) = updateUIState(weekdayPaint.textSize, size) {
        weekdayPaint.textSize = size
        weekdayRow.onMeasurementsChanged()

        onGridTopChanged()
    }

    fun setWeekdayTypeface(typeface: Typeface?) = updateUIState(weekdayPaint.typeface, typeface) {
        weekdayPaint.typeface = typeface
        weekdayRow.onMeasurementsChanged()

        onGridTopChanged()
    }

    fun setCustomWeekdays(weekdays: Array<out String>?) = updateUIState(weekdayRow.weekdays, weekdays) {
        weekdayRow.weekdays = weekdays

        onGridChanged()
    }

    fun setInMonthTextColor(color: Int) = updateUIState(inMonthTextColor, color) {
        inMonthTextColor = color
    }

    fun setOutMonthTextColor(color: Int) = updateUIState(outMonthTextColor, color) {
        outMonthTextColor = color
    }

    fun setDisabledCellTextColor(color: Int) = updateUIState(disabledCellTextColor, color) {
        disabledCellTextColor = color
    }

    fun setTodayCellColor(color: Int) = updateUIState(todayCellTextColor, color) {
        todayCellTextColor = color
    }

    fun setHoverColor(color: Int) = updateUIState(hoverCellBgColor, color) {
        hoverCellBgColor = color
    }

    fun setHoverOnSelectionColor(color: Int) = updateUIState(hoverOnSelectionBgColor, color) {
        hoverCellBgColor = color
    }

    fun setWeekdayTextColor(color: Int) = updateUIState(weekdayPaint.color, color) {
        weekdayPaint.color = color
    }

    fun setCellSize(size: Float) {
        require(size > 0f) { "size <= 0" }

        if (cellWidth != size || cellHeight != size) {
            cellWidth = size
            cellHeight = size

            refreshAllDecorVisualStates()
            refreshColumnWidth()
            updateSelectionStateConfiguration()

            requestLayout()
        }
    }

    private inline fun setCellWidthHeightInternal(
        newValue: Float,
        currentValue: Float,
        setCurrent: (Float) -> Unit
    ) {
        if (currentValue != newValue) {
            setCurrent(newValue)

            refreshAllDecorVisualStates()
            refreshColumnWidth()
            updateSelectionStateConfiguration()

            requestLayout()
        }
    }

    fun setCellWidth(value: Float) {
        require(value > 0f) { "width <= 0" }

        setCellWidthHeightInternal(value, cellWidth) { cellWidth = it }
    }

    fun setCellHeight(value: Float) {
        require(value > 0f) { "height <= 0" }

        setCellWidthHeightInternal(value, cellHeight) { cellHeight = it }
    }

    fun setCellRoundRadius(value: Float) = updateUIState(rrRadius, value) {
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

    fun setWeekdayType(_type: WeekdayType) {
        // There is no narrow weekdays before API < 24, so we need to resolve it
        val type = _type.resolved()

        if (weekdayRow.type != type) {
            weekdayRow.type = type

            onGridTopChanged()
            invalidate()
        }
    }

    fun setTodayCell(cell: Cell) = updateUIState(todayCell != cell) {
        todayCell = cell
        invalidate()
    }

    fun setShowAdjacentMonths(value: Boolean) = updateUIState(showAdjacentMonths != value) {
        showAdjacentMonths = value

        // showAdjacentMonths directly affects on the selection range, so we need to update it.
        updateSelectionRange()
    }

    fun setCellAccessibilityInfoProvider(value: RangeCalendarCellAccessibilityInfoProvider) {
        cellAccessibilityInfoProvider = value

        // Some accessibility-related properties might be changed.
        touchHelper.invalidateRoot()
    }

    private fun refreshColumnWidth() = updateUIState {
        val width = width.toFloat()

        columnWidth = (width - cr.hPadding * 2) * (1f / 7)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val preferredWidth = ceilToInt(cellWidth * 7)
        val preferredHeight = ceilToInt(gridTop() + (cellHeight * 6))

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

                    if (isSelectableCell(cell)) {
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

                        if (isSelectableCell(cell)) {
                            if (lastTouchTime > 0 &&
                                touchTime - lastTouchTime < DOUBLE_TOUCH_MAX_MILLIS &&
                                lastTouchCell == cell
                            ) {
                                selectRange(
                                    range = CellRange.week(cell.gridY),
                                    requestRejectedBehaviour = SelectionRequestRejectedBehaviour.PRESERVE_CURRENT_SELECTION,
                                    isCellSelectionByUser = false,
                                    isUserStartSelection = false,
                                    doAnimation = true
                                )
                            } else {
                                selectRange(
                                    CellRange.single(cell),
                                    requestRejectedBehaviour = SelectionRequestRejectedBehaviour.PRESERVE_CURRENT_SELECTION,
                                    isCellSelectionByUser = true,
                                    isUserStartSelection = false,
                                    doAnimation = true
                                )

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
                    if (isSelectingCustomRange && isXInActiveZone(x)) {
                        parent?.requestDisallowInterceptTouchEvent(true)

                        val cell = getCellByPointOnScreen(x, y)
                        val newRange = CellRange(customRangeStartCell, cell).normalize()

                        selectRange(
                            newRange,
                            requestRejectedBehaviour = SelectionRequestRejectedBehaviour.PRESERVE_CURRENT_SELECTION,
                            isCellSelectionByUser = false,
                            isUserStartSelection = false,
                            doAnimation = true
                        )
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
        selectRange(
            CellRange.single(cell),
            requestRejectedBehaviour = SelectionRequestRejectedBehaviour.PRESERVE_CURRENT_SELECTION,
            isCellSelectionByUser = false,
            isUserStartSelection = true,
            doAnimation = true
        )
    }

    override fun getFocusedRect(r: Rect) {
        val selState = selectionManager.currentState
        val start = selState.startCell
        val end = selState.endCell

        if (start.sameY(end)) {
            val (left, top) = getCellLeftTop(start)
            val right = getCellRight(end)

            r.set(left.toInt(), top.toInt(), right.toInt(), (top + cellHeight).toInt())
        } else {
            super.getFocusedRect(r)
        }
    }

    private fun sendClickEventToAccessibility(cell: Cell) {
        touchHelper.sendEventForVirtualView(cell.index, AccessibilityEvent.TYPE_VIEW_CLICKED)
    }

    private fun updateSelectionRange() {
        val selState = selectionManager.currentState

        selectRange(
            selState.range,
            requestRejectedBehaviour = SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION,
            isCellSelectionByUser = false,
            isUserStartSelection = false,
            doAnimation = true
        )
    }

    fun select(
        range: CellRange,
        requestRejectedBehaviour: SelectionRequestRejectedBehaviour,
        withAnimation: Boolean
    ) {
        selectRange(
            range,
            requestRejectedBehaviour,
            isCellSelectionByUser = false,
            isUserStartSelection = false,
            doAnimation = withAnimation
        )
    }

    private fun selectRange(
        range: CellRange,
        requestRejectedBehaviour: SelectionRequestRejectedBehaviour,
        isCellSelectionByUser: Boolean,
        isUserStartSelection: Boolean,
        doAnimation: Boolean
    ) {
        val selState = selectionManager.currentState
        val rangeStart = range.start
        val isSameCellSelection = rangeStart == range.end && selState.isSingleCell(rangeStart)

        // We don't do gate validation if the request come from user and it's the same selection and specified behaviour is CLEAR.
        // That's done because if the gate rejects the request and behaviour is PRESERVE, the cell won't be cleared but
        // it should be.
        if (isCellSelectionByUser && isSameCellSelection && clickOnCellSelectionBehavior == ClickOnCellSelectionBehavior.CLEAR) {
            clearSelection(fireEvent = true, doAnimation = true)
            return
        }

        val gate = selectionGate
        if (gate != null) {
            if (!gate.range(range)) {
                clearSelectionToMatchBehaviour(requestRejectedBehaviour)

                return
            }
        }

        // Clear hover here and not in onCellLongPress() because custom range might be disallowed and
        // hover will be cleared but it shouldn't.
        clearHoverCellWithAnimation()

        var intersection = range.intersectionWith(enabledCellRange)
        if (!showAdjacentMonths) {
            intersection = intersection.intersectionWith(inMonthRange)
        }

        if (intersection == CellRange.Invalid) {
            clearSelectionToMatchBehaviour(requestRejectedBehaviour)

            return
        } else if (!isUserStartSelection && selState.range == intersection) {
            return
        }

        if (isUserStartSelection) {
            customRangeStartCell = range.start
            isSelectingCustomRange = true
        }

        selectionManager.setState(intersection, cellMeasureManager)

        onSelectionListener?.onSelection(intersection)

        if (hoverCell.isDefined) {
            clearHoverCellWithAnimation()
        }

        if (vibrateOnSelectingCustomRange && isUserStartSelection) {
            vibrateOnUserSelection()
        }

        if (doAnimation && selectionManager.hasTransition()) {
            startSelectionTransition()
        } else {
            invalidate()
        }
    }

    private fun vibrateOnUserSelection() {
        vibrator.vibrateTick()
    }

    private fun clearSelectionToMatchBehaviour(value: SelectionRequestRejectedBehaviour) {
        if (value == SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION) {
            clearSelection(fireEvent = true, doAnimation = true)
        }
    }

    private fun startSelectionTransition() {
        val controller = selectionManager.transitionController

        val handler = getLazyValue(
            selectionTransitionHandler,
            { { controller.handleTransition(selectionTransitiveState!!, cellMeasureManager, animFraction) } },
            { selectionTransitionHandler = it }
        )
        val onEnd = getLazyValue(
            onSelectionTransitionEnd,
            { { selectionTransitiveState = null } },
            { onSelectionTransitionEnd = it }
        )

        // Before changing selectionTransitiveState, previous animation (which may be selection-like) should be stopped.
        endCalendarAnimation()

        selectionTransitiveState =
            selectionManager.createTransition(cellMeasureManager, selectionRenderOptions)

        startCalendarAnimation(SELECTION_ANIMATION, handler, onEnd)
    }

    private fun setHoverCell(cell: Cell) {
        if (selectionManager.currentState.isSingleCell(cell) || hoverCell == cell) {
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
        if (selectionManager.currentState.isNone) {
            return
        }

        selectionManager.setNoneState()

        // Fire event if it's demanded
        val listener = onSelectionListener
        if (fireEvent && listener != null) {
            listener.onSelectionCleared()
        }

        if (doAnimation) {
            startSelectionTransition()
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

        val cw = cellWidth
        val ch = cellHeight

        val halfCellWidth = cw * 0.5f
        val halfCellHeight = ch * 0.5f

        val cellTextSize = getDayNumberSize(cell)
        val halfCellTextWidth = cellTextSize.width * 0.5f
        val halfCellTextHeight = cellTextSize.height * 0.5f

        cellInfo.apply {
            width = cw
            height = ch
            radius = cellRoundRadius()
            layoutOptions = decorLayoutOptionsArray[cell] ?: decorDefaultLayoutOptions

            setTextBounds(
                halfCellWidth - halfCellTextWidth,
                halfCellHeight - halfCellTextHeight,
                halfCellWidth + halfCellTextWidth,
                halfCellHeight + halfCellTextHeight
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

    private fun endCalendarAnimation() {
        animator?.let {
            if (it.isRunning) {
                it.end()
            }
        }
    }

    // It could be startAnimation(), but this name would interfere with View's startAnimation(Animation)
    private fun startCalendarAnimation(
        type: Int,
        handler: (() -> Unit)? = null,
        onEnd: (() -> Unit)? = null
    ) {
        var animator = animator
        endCalendarAnimation()

        animType = type
        onAnimationEnd = onEnd
        animationHandler = handler
        animFraction = if ((animType and ANIMATION_REVERSE_BIT) != 0) 1f else 0f

        if (animator == null) {
            animator = AnimationHelper.createFractionAnimator { value: Float ->
                animFraction = value
                animationHandler?.invoke()

                invalidate()
            }

            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    //Log.i(TAG, "onAnimationEnd()")

                    animType = NO_ANIMATION
                    onAnimationEnd?.invoke()

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
        val renderer = selectionRenderer
        val options = selectionRenderOptions

        if ((animType and ANIMATION_DATA_MASK) == SELECTION_ANIMATION) {
            selectionTransitiveState?.let {
                renderer.drawTransition(c, it, options)
            }
        } else {
            renderer.draw(c, selectionManager.currentState, options)
        }
    }

    private fun drawWeekdayRow(c: Canvas) {
        weekdayRow.draw(c, cr.hPadding, columnWidth)
    }

    private fun drawHover(c: Canvas) {
        val isHoverAnimation = (animType and ANIMATION_DATA_MASK) == HOVER_ANIMATION

        if ((isHoverAnimation && animationHoverCell.isDefined) || hoverCell.isDefined) {
            val cell = if (isHoverAnimation) animationHoverCell else hoverCell
            val (left, top) = getCellLeftTop(cell)
            val isOnSelection = selectionManager.currentState.contains(cell)

            var color = if (isOnSelection) {
                hoverOnSelectionBgColor
            } else {
                hoverCellBgColor
            }

            if (isHoverAnimation) {
                color = color.withCombinedAlpha(animFraction)
            }

            cellHoverPaint.color = color

            c.drawRoundRectCompat(
                left, top, left + cellWidth, top + cellHeight,
                cellRoundRadius(), cellHoverPaint
            )
        }
    }

    private fun drawCells(c: Canvas) {
        measureDayNumberTextSizesIfNecessary()

        val halfCellHeight = cellHeight * 0.5f
        val startIndex: Int
        val endIndex: Int

        if (showAdjacentMonths) {
            startIndex = 0
            endIndex = 42
        } else {
            val (start, end) = inMonthRange

            startIndex = start.index

            // endIndex is exclusive, inMonthRange.end is inclusive, that's why +1
            endIndex = end.index + 1
        }

        for (i in startIndex until endIndex) {
            val cell = Cell(i)

            val centerX = getCellCenterLeft(cell)
            val centerY = getCellTop(cell) + halfCellHeight

            drawCell(c, centerX, centerY, cells[i].toInt(), resolveCellType(cell))
        }
    }

    private fun resolveCellType(cell: Cell): Int {
        val selState = selectionManager.currentState
        val start = selState.rangeStart
        val end = selState.rangeEnd

        var cellType = if (start == cell.index && end == cell.index) { // Is a single cell
            CELL_SELECTED
        } else if (enabledCellRange.contains(cell)) {
            if (inMonthRange.contains(cell)) {
                if (cell == todayCell) {
                    if (cell.index in start..end) {
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

    private fun drawCell(c: Canvas, centerX: Float, centerY: Float, day: Int, cellType: Int) {
        if (day > 0) {
            val color = when (cellType and CELL_DATA_MASK) {
                CELL_SELECTED, CELL_IN_MONTH -> inMonthTextColor
                CELL_OUT_MONTH -> outMonthTextColor
                CELL_DISABLED -> disabledCellTextColor
                CELL_TODAY -> if ((cellType and CELL_HOVER_BIT) != 0) {
                    inMonthTextColor
                } else {
                    todayCellTextColor
                }

                else -> throw IllegalArgumentException("type")
            }

            val textSize = getDayNumberSize(day)

            val textX = centerX - textSize.width * 0.5f
            val textY = centerY + textSize.height * 0.5f

            dayNumberPaint.color = color

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

            dayNumberPaint.getTextBoundsArray(CalendarResources.DAYS, 0, 31) { i, width, height ->
                sizes[i] = PackedSize(width, height)
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

        // Height of the view depends on gridTop()
        requestLayout()

        // y-axis of entries depends on type of weekday, so we need to refresh accessibility info
        touchHelper.invalidateRoot()
    }

    private fun gridTop(): Float {
        return weekdayRow.height + cr.weekdayRowMarginBottom
    }

    // It'd be better if cellRoundRadius() returns round radius that isn't greater than half of cell size.
    private fun cellRoundRadius(): Float {
        return min(rrRadius, min(cellWidth, cellHeight) * 0.5f)
    }

    private fun firstCellLeft(): Float {
        return cr.hPadding + (columnWidth - cellWidth) * 0.5f
    }

    private fun getCellCenterLeft(cell: Cell): Float {
        return cr.hPadding + columnWidth * (cell.gridX + 0.5f)
    }

    private fun getCellLeft(cell: Cell): Float {
        return getCellCenterLeft(cell) - cellWidth * 0.5f
    }

    private fun getCellTopByGridY(gridY: Int): Float {
        return gridTop() + gridY * cellHeight
    }

    private fun getCellTop(cell: Cell): Float {
        return getCellTopByGridY(cell.gridY)
    }

    private fun getCellCenterTop(cell: Cell): Float {
        return getCellTop(cell) + cellHeight * 0.5f
    }

    private fun getCellLeftTop(cell: Cell): PackedPointF {
        return PackedPointF(getCellLeft(cell), getCellTop(cell))
    }

    private fun getCellCenter(cell: Cell): PackedPointF {
        return PackedPointF(getCellCenterLeft(cell), getCellCenterTop(cell))
    }

    private fun getCellRight(cell: Cell): Float {
        return getCellCenterLeft(cell) + cellWidth * 0.5f
    }

    private fun getCellDistance(cell: Cell): Float {
        // Width of the view without horizontal paddings (left and right)
        val rowWidth = width - 2 * cr.hPadding

        // First, find a x-axis of the cell but without horizontal padding
        var distance = columnWidth * (cell.gridX + 0.5f) - cellWidth * 0.5f

        // Add widths of the rows on the way to the cell.
        distance += rowWidth * cell.gridY

        return distance
    }

    private fun getCellAndPointByCellDistance(distance: Float, outPoint: PointF): Int {
        val rowWidth = width - 2f * cr.hPadding

        val gridY = (distance / rowWidth).toInt()
        val cellTop = getCellTopByGridY(gridY)

        val xOnRow = distance - gridY * rowWidth
        val gridX = (xOnRow / columnWidth).toInt()

        outPoint.x = cr.hPadding + xOnRow
        outPoint.y = cellTop

        return gridY * 7 + gridX
    }

    private fun getCellByPointOnScreen(x: Float, y: Float): Cell {
        val gridX = ((x - cr.hPadding) / columnWidth).toInt()
        val gridY = ((y - gridTop()) / cellHeight).toInt()

        return Cell(gridY * 7 + gridX)
    }

    private fun isSelectableCell(cell: Cell): Boolean {
        return enabledCellRange.contains(cell) && (showAdjacentMonths || inMonthRange.contains(cell))
    }

    // Checks whether x in active (touchable) zone
    private fun isXInActiveZone(x: Float): Boolean {
        return x >= cr.hPadding && x <= width - cr.hPadding
    }

    private fun getDayNumberSize(day: Int) = dayNumberSizes[day - 1]
    private fun getDayNumberSize(cell: Cell) = getDayNumberSize(cells[cell.index].toInt())

    companion object {
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
        private const val TAG = "RangeCalendarGridView"

        private const val ANIMATION_REVERSE_BIT = 1 shl 31
        private const val ANIMATION_DATA_MASK = ANIMATION_REVERSE_BIT.inv()

        private const val NO_ANIMATION = 0
        private const val SELECTION_ANIMATION = 1
        private const val HOVER_ANIMATION = 2
        private const val DECOR_ANIMATION = 3
    }
}