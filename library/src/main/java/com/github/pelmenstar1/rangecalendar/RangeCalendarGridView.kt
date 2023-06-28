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
internal class RangeCalendarGridView(
    context: Context,
    val cr: CalendarResources,
    val style: RangeCalendarStyleData
) : View(context) {
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

            grid.fillCellBounds(cell, tempRect)

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
                    withAnimation = false
                )

                true
            } else false
        }

        private fun getDayDescriptionForIndex(index: Int): CharSequence {
            val provider =
                grid.style.getObject<RangeCalendarCellAccessibilityInfoProvider> { CELL_ACCESSIBILITY_INFO_PROVIDER }

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
            get() = view.cellWidth()

        override val cellHeight: Float
            get() = view.cellHeight()

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

    private val dayNumberPaint: Paint
    private val weekdayPaint: Paint
    private val selectionPaint: Paint
    private val cellHoverPaint: Paint

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
    private var selectionRenderOptions: SelectionRenderOptions? = null

    private val cellMeasureManager = CellMeasureManagerImpl(this)

    private var customRangeStartCell = Cell.Undefined
    private var isSelectingCustomRange = false

    private var animType = 0
    private var animFraction = 0f
    private var animator: ValueAnimator? = null
    private var onAnimationEnd: (() -> Unit)? = null
    private var animationHandler: (() -> Unit)? = null

    private val touchHelper = TouchHelper(this)

    private val weekdayRow: WeekdayRow

    private var isDayNumberMeasurementsDirty = false
    private var dayNumberSizes = cr.defaultDayNumberSizes

    private val vibrator = VibratorCompat(context)

    internal var decorations: DecorGroupedList? = null
    private var decorRegion = PackedIntRange.Undefined

    private val decorVisualStates = LazyCellDataArray<CellDecor.VisualState>()
    private val decorLayoutOptionsArray = LazyCellDataArray<DecorLayoutOptions>()
    private val cellInfo = CellInfo()

    private var decorAnimFractionInterpolator: DecorAnimationFractionInterpolator? = null
    private var decorAnimatedCell = Cell.Undefined
    private var decorAnimatedRange = PackedIntRange(0)
    private var decorAnimationHandler: (() -> Unit)? = null
    private var decorTransitiveState: CellDecor.VisualState.Transitive? = null

    private val pressTimeoutHandler = PressTimeoutHandler(this)

    init {
        ViewCompat.setAccessibilityDelegate(this, touchHelper)

        background = null

        selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        dayNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        weekdayPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        cellHoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        weekdayRow = WeekdayRow(cr.defaultWeekdayData, weekdayPaint)
    }

    private fun rrRadius(): Float = style.getFloat { CELL_ROUND_RADIUS }
    private fun cellWidth(): Float = style.getFloat { CELL_WIDTH }
    private fun cellHeight(): Float = style.getFloat { CELL_HEIGHT }

    private fun selectionFill() = style.getObject<Fill> { SELECTION_FILL }
    private fun selectionFillGradientBoundsType() =
        SelectionFillGradientBoundsType.ofOrdinal(style.getInt { SELECTION_FILL_GRADIENT_BOUNDS_TYPE })

    private fun clickOnCellSelectionBehavior() =
        ClickOnCellSelectionBehavior.ofOrdinal(style.getInt { CLICK_ON_CELL_SELECTION_BEHAVIOR })

    private fun cellAnimationType() =
        CellAnimationType.ofOrdinal(style.getInt { CELL_ANIMATION_TYPE })

    private fun showAdjacentMonths() = style.getBoolean { SHOW_ADJACENT_MONTHS }
    private fun vibrateOnSelectingRange() = style.getBoolean { VIBRATE_ON_SELECTING_RANGE }

    private fun commonAnimationDuration() = style.getInt { COMMON_ANIMATION_DURATION }
    private fun hoverAnimationDuration() = style.getInt { HOVER_ANIMATION_DURATION }

    private fun commonAnimationInterpolator() =
        style.getObject<TimeInterpolator> { COMMON_ANIMATION_INTERPOLATOR }

    private fun hoverAnimationInterpolator() =
        style.getObject<TimeInterpolator> { HOVER_ANIMATION_INTERPOLATOR }

    private fun decorDefaultLayoutOptions() =
        style.getObject<DecorLayoutOptions> { DECOR_DEFAULT_LAYOUT_OPTIONS }

    private fun isSelectionAnimatedByDefault() =
        style.getBoolean { IS_SELECTION_ANIMATED_BY_DEFAULT }

    private fun isHoverAnimationEnabled() = style.getBoolean { IS_HOVER_ANIMATION_ENABLED }

    private fun updateSelectionRenderOptions() {
        selectionRenderOptions = SelectionRenderOptions(
            selectionFill(),
            selectionFillGradientBoundsType(),
            cellRoundRadius(),
            cellAnimationType()
        )

        invalidate()
    }

    private fun updateSelectionStateConfiguration() {
        selectionManager.updateConfiguration(cellMeasureManager)

        invalidate()
    }

    fun onStylePropertyChanged(propIndex: Int) {
        if (RangeCalendarStyleData.isObjectProperty(propIndex)) {
            onStylePropertyObjectChanged(propIndex)
        } else {
            onStylePropertyIntChanged(propIndex)
        }
    }

    private fun onStylePropertyIntChanged(propIndex: Int) {
        val data = style.getPackedInt(propIndex)

        with(RangeCalendarStyleData.Companion) {
            when (propIndex) {
                DAY_NUMBER_TEXT_SIZE -> onDayNumberTextSizeChanged(data.float())
                WEEKDAY_TEXT_SIZE -> onWeekdayTextSizeChanged(data.float())
                CELL_WIDTH, CELL_HEIGHT -> onCellSizeComponentChanged()
                CELL_ROUND_RADIUS -> onCellRoundRadiusChanged()
                WEEKDAY_TYPE -> onWeekdayTypeChanged(data.enum(WeekdayType::ofOrdinal))
                SELECTION_FILL_GRADIENT_BOUNDS_TYPE -> onSelectionFillOrGradientBoundsTypeChanged()
                CELL_ANIMATION_TYPE -> onCellAnimationTypeChanged()
                WEEKDAY_TEXT_COLOR -> onWeekdayTextColorChanged(data.value)
                SHOW_ADJACENT_MONTHS -> onShowAdjacentMonthsChanged()
            }
        }
    }

    private fun onStylePropertyObjectChanged(propIndex: Int) {
        val data = PackedObject(style.getObject(propIndex))

        with(RangeCalendarStyleData.Companion) {
            when (propIndex) {
                DECOR_DEFAULT_LAYOUT_OPTIONS -> onDecorationDefaultLayoutOptionsChanged()
                SELECTION_FILL -> onSelectionFillOrGradientBoundsTypeChanged()
                SELECTION_MANAGER -> onSelectionManagerChanged(data.value())
                CELL_ACCESSIBILITY_INFO_PROVIDER -> onCellAccessibilityInfoProviderChanged()
                WEEKDAY_TYPEFACE -> onWeekdayTypefaceChanged(data.value())
                WEEKDAYS -> onWeekdaysChanged(data.value())
            }
        }
    }

    private fun onSelectionFillOrGradientBoundsTypeChanged() {
        updateGradientBoundsIfNeeded()
        updateSelectionRenderOptions()
        invalidate()
    }

    private fun onSelectionManagerChanged(manager: SelectionManager?) {
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

        invalidate()
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

    private fun onCellAnimationTypeChanged() {
        updateSelectionRenderOptions()
    }

    private fun onDayNumberTextSizeChanged(newSize: Float) {
        dayNumberPaint.textSize = newSize

        if (newSize != cr.dayNumberTextSize) {
            isDayNumberMeasurementsDirty = true
        }

        refreshAllDecorVisualStates()
    }

    private fun onWeekdayTextSizeChanged(newSize: Float) {
        weekdayPaint.textSize = newSize
        weekdayRow.onMeasurementsChanged()

        onGridTopChanged()
    }

    private fun onWeekdayTypefaceChanged(typeface: Typeface?) {
        weekdayPaint.typeface = typeface
        weekdayRow.onMeasurementsChanged()

        onGridTopChanged()
    }

    private fun onWeekdaysChanged(weekdays: Array<out String>?) {
        weekdayRow.weekdays = weekdays

        onGridChanged()
    }

    private fun onWeekdayTextColorChanged(color: Int) {
        weekdayPaint.color = color
        invalidate()
    }

    fun onCellSizeComponentChanged() {
        refreshAllDecorVisualStates()
        updateSelectionStateConfiguration()

        // Size depends on cellWidth and cellHeight
        requestLayout()
    }

    fun onCellRoundRadiusChanged() {
        refreshAllDecorVisualStates()
        updateSelectionRenderOptions()

        invalidate()
    }

    private fun onWeekdayTypeChanged(_type: WeekdayType) {
        // There is no narrow weekdays before API < 24, so we need to resolve it
        val type = _type.resolved()

        if (weekdayRow.type != type) {
            weekdayRow.type = type

            onGridTopChanged()
            invalidate()
        }
    }

    private fun onShowAdjacentMonthsChanged() {
        // showAdjacentMonths directly affects on the selection range, so we need to update it.
        updateSelectionRange()
    }

    private fun onCellAccessibilityInfoProviderChanged() {
        // Some accessibility-related properties might be changed.
        touchHelper.invalidateRoot()
    }

    fun setInMonthRange(range: CellRange) {
        inMonthRange = range

        updateSelectionRange()
    }

    fun setEnabledCellRange(range: CellRange) {
        enabledCellRange = range

        updateSelectionRange()
    }

    fun setTodayCell(cell: Cell) {
        val old = todayCell
        todayCell = cell

        if (old != cell) {
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val preferredWidth = ceilToInt(cellWidth() * 7)
        val preferredHeight = ceilToInt(gridTop() + (cellHeight() * 6))

        setMeasuredDimension(
            resolveSize(preferredWidth, widthMeasureSpec),
            resolveSize(preferredHeight, heightMeasureSpec)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        updateGradientBoundsIfNeeded()
        updateSelectionStateConfiguration()
    }

    private fun updateGradientBoundsIfNeeded() {
        if (selectionFillGradientBoundsType() == SelectionFillGradientBoundsType.GRID) {
            val hPadding = cr.hPadding

            selectionFill().setBounds(
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
                        val hoverTime = eTime + ViewConfiguration.getTapTimeout()

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
                }

                MotionEvent.ACTION_UP -> {
                    performClick()

                    if (!isSelectingCustomRange && isXInActiveZone(x)) {
                        val cell = getCellByPointOnScreen(x, y)
                        val touchTime = e.downTime

                        if (isSelectableCell(cell)) {
                            val withAnimation = isSelectionAnimatedByDefault()
                            val timeout = ViewConfiguration.getDoubleTapTimeout()

                            if (touchTime - lastTouchTime < timeout && lastTouchCell == cell) {
                                selectRange(
                                    range = CellRange.week(cell.gridY),
                                    requestRejectedBehaviour = SelectionRequestRejectedBehaviour.PRESERVE_CURRENT_SELECTION,
                                    isCellSelectionByUser = false,
                                    isUserStartSelection = false,
                                    withAnimation
                                )
                            } else {
                                selectRange(
                                    range = CellRange.single(cell),
                                    requestRejectedBehaviour = SelectionRequestRejectedBehaviour.PRESERVE_CURRENT_SELECTION,
                                    isCellSelectionByUser = true,
                                    isUserStartSelection = false,
                                    withAnimation
                                )

                                sendClickEventToAccessibility(cell)

                                lastTouchCell = cell
                                lastTouchTime = touchTime
                            }
                        }
                    }

                    // Delete all messages from queue, long-press or hover may already happened or not.
                    pressTimeoutHandler.removeCallbacksAndMessages(null)

                    // Don't call clearHoverCell() because it will start animation,
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
                    clearHoverCell()
                    stopSelectingCustomRange()
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
                            withAnimation = isSelectionAnimatedByDefault()
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
            withAnimation = isSelectionAnimatedByDefault()
        )
    }

    override fun getFocusedRect(r: Rect) {
        val selState = selectionManager.currentState
        val start = selState.startCell
        val end = selState.endCell

        if (start.sameY(end)) {
            fillRangeOnRowBounds(start, end, r)
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
            withAnimation = isSelectionAnimatedByDefault()
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
            withAnimation
        )
    }

    private fun selectRange(
        range: CellRange,
        requestRejectedBehaviour: SelectionRequestRejectedBehaviour,
        isCellSelectionByUser: Boolean,
        isUserStartSelection: Boolean,
        withAnimation: Boolean
    ) {
        val selState = selectionManager.currentState
        val rangeStart = range.start
        val isSameCellSelection = rangeStart == range.end && selState.isSingleCell(rangeStart)

        // We don't do gate validation if the request come from user and it's the same selection and specified behaviour is CLEAR.
        // That's done because if the gate rejects the request and behaviour is PRESERVE, the cell won't be cleared but
        // it should be.
        if (isCellSelectionByUser && isSameCellSelection && clickOnCellSelectionBehavior() == ClickOnCellSelectionBehavior.CLEAR) {
            clearSelection(fireEvent = true, withAnimation)
            return
        }

        val gate = selectionGate
        if (gate != null) {
            if (!gate.range(range)) {
                clearSelectionToMatchBehaviour(requestRejectedBehaviour, withAnimation)

                return
            }
        }

        // Clear hover here and not in onCellLongPress() because custom range might be disallowed and
        // hover will be cleared but it shouldn't.
        clearHoverCell()

        var intersection = range.intersectionWith(enabledCellRange)
        if (!showAdjacentMonths()) {
            intersection = intersection.intersectionWith(inMonthRange)
        }

        if (intersection == CellRange.Invalid) {
            clearSelectionToMatchBehaviour(requestRejectedBehaviour, withAnimation)

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

        if (vibrateOnSelectingRange() && isUserStartSelection) {
            vibrateOnUserSelection()
        }

        if (withAnimation && selectionManager.hasTransition()) {
            startSelectionTransition()
        } else {
            invalidate()
        }
    }

    private fun vibrateOnUserSelection() {
        vibrator.vibrateTick()
    }

    private fun clearSelectionToMatchBehaviour(
        value: SelectionRequestRejectedBehaviour,
        withAnimation: Boolean
    ) {
        if (value == SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION) {
            clearSelection(fireEvent = true, withAnimation)
        }
    }

    private fun startSelectionTransition() {
        val controller = selectionManager.transitionController

        val handler = getLazyValue(
            selectionTransitionHandler,
            {
                {
                    controller.handleTransition(
                        selectionTransitiveState!!,
                        cellMeasureManager,
                        animFraction
                    )
                }
            },
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
            selectionManager.createTransition(cellMeasureManager, selectionRenderOptions!!)

        startCalendarAnimation(SELECTION_ANIMATION, handler, onEnd)
    }

    private fun setHoverCell(cell: Cell) {
        if (selectionManager.currentState.isSingleCell(cell) || hoverCell == cell) {
            return
        }

        animationHoverCell = cell
        hoverCell = cell

        if (isHoverAnimationEnabled()) {
            startCalendarAnimation(HOVER_ANIMATION)
        } else {
            invalidate()
        }
    }

    fun clearHoverCell() {
        if (hoverCell.isDefined) {
            hoverCell = Cell.Undefined

            if (isHoverAnimationEnabled()) {
                startCalendarAnimation(HOVER_ANIMATION or ANIMATION_REVERSE_BIT)
            } else {
                invalidate()
            }
        }
    }

    fun clearSelection(fireEvent: Boolean, withAnimation: Boolean) {
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

        if (withAnimation) {
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

        val cw = cellWidth()
        val ch = cellHeight()

        val halfCellWidth = cw * 0.5f
        val halfCellHeight = ch * 0.5f

        val cellTextSize = getDayNumberSize(cell)
        val halfCellTextWidth = cellTextSize.width * 0.5f
        val halfCellTextHeight = cellTextSize.height * 0.5f

        cellInfo.apply {
            width = cw
            height = ch
            radius = cellRoundRadius()
            layoutOptions = decorLayoutOptionsArray[cell] ?: decorDefaultLayoutOptions()

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
    fun onDecorationDefaultLayoutOptionsChanged() {
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

    fun onDecorInit(newDecorRegion: PackedIntRange) {
        decorRegion = newDecorRegion

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
                    animType = NO_ANIMATION
                    onAnimationEnd?.invoke()

                    invalidate()
                }
            })

            this.animator = animator
        }

        if (type and ANIMATION_DATA_MASK == HOVER_ANIMATION) {
            animator.duration = hoverAnimationDuration().toLong()
            animator.interpolator = hoverAnimationInterpolator()
        } else {
            animator.duration = commonAnimationDuration().toLong()
            animator.interpolator = commonAnimationInterpolator()
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
        val options = selectionRenderOptions!!

        if ((animType and ANIMATION_DATA_MASK) == SELECTION_ANIMATION) {
            selectionTransitiveState?.let {
                renderer.drawTransition(c, it, options)
            }
        } else {
            renderer.draw(c, selectionManager.currentState, options)
        }
    }

    private fun drawWeekdayRow(c: Canvas) {
        weekdayRow.draw(c, cr.hPadding, columnWidth())
    }

    private fun drawHover(c: Canvas) {
        val isHoverAnimation = (animType and ANIMATION_DATA_MASK) == HOVER_ANIMATION

        if ((isHoverAnimation && animationHoverCell.isDefined) || hoverCell.isDefined) {
            val cell = if (isHoverAnimation) animationHoverCell else hoverCell

            val isOnSelection = selectionManager.currentState.contains(cell)

            var color = if (isOnSelection) {
                style.getInt { HOVER_ON_SELECTION_COLOR }
            } else {
                style.getInt { HOVER_COLOR }
            }

            if (isHoverAnimation) {
                color = color.withCombinedAlpha(animFraction)
            }

            cellHoverPaint.color = color

            val halfCellWidth = cellWidth() * 0.5f
            val cellHeight = cellHeight()

            val centerX = getCellCenterX(cell)
            val left = centerX - halfCellWidth
            val right = centerX + halfCellWidth

            val top = getCellTop(cell, cellHeight)
            val bottom = top + cellHeight

            c.drawRoundRectCompat(
                left, top, right, bottom,
                cellRoundRadius(), cellHoverPaint
            )
        }
    }

    private fun drawCells(c: Canvas) {
        measureDayNumberTextSizesIfNecessary()

        val startIndex: Int
        val endIndex: Int

        if (showAdjacentMonths()) {
            startIndex = 0
            endIndex = 41
        } else {
            val (start, end) = inMonthRange

            startIndex = start.index
            endIndex = end.index
        }

        val columnWidth = columnWidth()
        val cellHeight = cellHeight()
        val halfCellHeight = cellHeight * 0.5f

        for (i in startIndex..endIndex) {
            val cell = Cell(i)

            val centerX = getCellCenterX(cell, columnWidth)
            val centerY = getCellTop(cell, cellHeight) + halfCellHeight

            drawCell(c, centerX, centerY, cells[i].toInt(), resolveCellType(cell))
        }
    }

    private fun resolveCellType(cell: Cell): Int {
        val isSelectionOverlaysCell = selectionTransitiveState?.overlaysCell(cell.index)
            ?: selectionManager.currentState.contains(cell)

        var cellType = if (isSelectionOverlaysCell) {
            CELL_SELECTED
        } else if (enabledCellRange.contains(cell)) {
            if (inMonthRange.contains(cell)) {
                if (cell == todayCell) {
                    CELL_TODAY
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
            val propIndex = when (cellType and CELL_DATA_MASK) {
                CELL_SELECTED, CELL_IN_MONTH -> RangeCalendarStyleData.IN_MONTH_TEXT_COLOR
                CELL_OUT_MONTH -> RangeCalendarStyleData.OUT_MONTH_TEXT_COLOR
                CELL_TODAY -> if ((cellType and CELL_HOVER_BIT) != 0) {
                    RangeCalendarStyleData.IN_MONTH_TEXT_COLOR
                } else {
                    RangeCalendarStyleData.TODAY_TEXT_COLOR
                }

                else -> throw IllegalArgumentException("type")
            }

            val color = style.getInt(propIndex)

            val (textWidth, textHeight) = getDayNumberSize(day)

            val textX = centerX - textWidth * 0.5f
            val textY = centerY + textHeight * 0.5f

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
        val columnWidth = columnWidth()
        val cellWidth = cellWidth()
        val cellHeight = cellHeight()

        decorVisualStates.forEachNotNull { cell, state ->
            val dx = getCellLeft(cell, columnWidth, cellWidth)
            val dy = getCellTop(cell, cellHeight)

            c.translate(dx, dy)
            state.visual().renderer().renderState(c, state)
            c.translate(-dx, -dy)
        }
    }

    private fun onGridTopChanged() {
        // Gradient bounds if gradient bounds type is GRID, depends on gridTop()
        updateGradientBoundsIfNeeded()

        // Selection state's internal measurements depend on coordinates of cells that depend on gridTop()
        updateSelectionStateConfiguration()

        // Height of the view depends on gridTop()
        requestLayout()

        // Y coordinates of cells depend on gridTop(), so we need to refresh accessibility info
        touchHelper.invalidateRoot()
    }

    private fun gridTop(): Float {
        return weekdayRow.height + cr.weekdayRowMarginBottom
    }

    // It'd be better if cellRoundRadius() returns round radius that isn't greater than half of cell size.
    private fun cellRoundRadius(): Float {
        return min(rrRadius(), min(cellWidth(), cellHeight()) * 0.5f)
    }

    // Width of the view without horizontal paddings (left and right)
    private fun rowWidth(): Float {
        return width - cr.hPadding * 2f
    }

    private fun columnWidth(): Float {
        return rowWidth() / 7f
    }

    private fun firstCellLeft(): Float {
        return cr.hPadding + (columnWidth() - cellWidth()) * 0.5f
    }

    private fun getCellCenterX(cell: Cell) = getCellCenterX(cell, columnWidth())

    private fun getCellCenterX(cell: Cell, columnWidth: Float): Float {
        return cr.hPadding + columnWidth * (cell.gridX + 0.5f)
    }

    private fun getCellLeft(cell: Cell) = getCellLeft(cell, columnWidth(), cellWidth())

    private fun getCellLeft(cell: Cell, columnWidth: Float, cellWidth: Float): Float {
        return getCellCenterX(cell, columnWidth) - cellWidth * 0.5f
    }

    private fun getCellRight(cell: Cell, columnWidth: Float, cellWidth: Float): Float {
        return getCellCenterX(cell, columnWidth) + cellWidth * 0.5f
    }

    private fun getCellTopByGridY(gridY: Int) = getCellTopByGridY(gridY, cellHeight())

    private fun getCellTopByGridY(gridY: Int, cellHeight: Float): Float {
        return gridTop() + gridY * cellHeight
    }

    private fun getCellTop(cell: Cell) = getCellTop(cell, cellHeight())

    private fun getCellTop(cell: Cell, cellHeight: Float): Float {
        return getCellTopByGridY(cell.gridY, cellHeight)
    }

    private fun fillCellBounds(cell: Cell, bounds: Rect) {
        val halfCw = cellWidth() * 0.5f
        val ch = cellHeight()

        val centerX = getCellCenterX(cell)

        val left = centerX - halfCw
        val right = centerX + halfCw

        val top = getCellTop(cell, ch)
        val bottom = top + ch

        bounds.set(left.toInt(), top.toInt(), ceilToInt(right), ceilToInt(bottom))
    }

    private fun fillRangeOnRowBounds(start: Cell, end: Cell, bounds: Rect) {
        val columnWidth = columnWidth()
        val cellWidth = cellWidth()
        val cellHeight = cellHeight()

        val left = getCellLeft(start, columnWidth, cellWidth)
        val top = getCellTop(start, cellHeight)
        val right = getCellRight(end, columnWidth, cellWidth)
        val bottom = top + cellHeight

        bounds.set(left.toInt(), top.toInt(), ceilToInt(right), ceilToInt(bottom))
    }

    private fun getCellDistance(cell: Cell): Float {
        val rw = rowWidth()

        // First, find a x-axis of the cell but without horizontal padding
        var distance = (rw / 7f) * (cell.gridX + 0.5f) - cellWidth() * 0.5f

        // Add widths of the rows on the way to the cell.
        distance += rw * cell.gridY

        return distance
    }

    private fun getCellAndPointByCellDistance(distance: Float, outPoint: PointF): Int {
        val rw = rowWidth()

        val gridY = (distance / rw).toInt()
        val cellTop = getCellTopByGridY(gridY)

        val xOnRow = distance - gridY * rw

        // Basically this is xOnRow / columnWidth() but we already have rowWidth, so
        // we rewrite xOnRow / (rw / 7f) as (7f * xOnRow) / rw
        val gridX = ((7f * xOnRow) / rw).toInt()

        outPoint.x = cr.hPadding + xOnRow
        outPoint.y = cellTop

        return gridY * 7 + gridX
    }

    private fun getCellByPointOnScreen(x: Float, y: Float): Cell {
        val gridX = ((x - cr.hPadding) / columnWidth()).toInt()
        val gridY = ((y - gridTop()) / cellHeight()).toInt()

        return Cell(gridY * 7 + gridX)
    }

    private fun isSelectableCell(cell: Cell): Boolean {
        return enabledCellRange.contains(cell) &&
                (showAdjacentMonths() || inMonthRange.contains(cell))
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

        private const val TAG = "RangeCalendarGridView"

        private const val ANIMATION_REVERSE_BIT = 1 shl 31
        private const val ANIMATION_DATA_MASK = ANIMATION_REVERSE_BIT.inv()

        private const val NO_ANIMATION = 0
        private const val SELECTION_ANIMATION = 1
        private const val HOVER_ANIMATION = 2
        private const val DECOR_ANIMATION = 3
    }
}