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
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.ColorInt
import androidx.annotation.ColorLong
import androidx.annotation.RequiresApi
import androidx.core.graphics.withSave
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import io.github.pelmenstar1.rangecalendar.decoration.*
import io.github.pelmenstar1.rangecalendar.selection.Cell
import io.github.pelmenstar1.rangecalendar.selection.CellRange
import io.github.pelmenstar1.rangecalendar.utils.*
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.max
import kotlin.math.sqrt

// It will never be XML layout, so there's no need to match conventions
@SuppressLint("ViewConstructor")
internal class RangeCalendarGridView(
    context: Context,
    val cr: CalendarResources
) : View(context) {
    interface OnSelectionListener {
        fun onSelectionCleared()
        fun onCellSelected(index: Int): Boolean
        fun onWeekSelected(weekIndex: Int, startIndex: Int, endIndex: Int): Boolean
        fun onCustomRangeSelected(startIndex: Int, endIndex: Int): Boolean
    }

    private object Radii {
        private val value = FloatArray(8)
        private var currentRadius: Float = 0f

        private var flags = 0

        private const val LT_SHIFT = 0
        private const val RT_SHIFT = 1
        private const val RB_SHIFT = 2
        private const val LB_SHIFT = 3

        fun clear() {
            flags = 0
        }

        private fun setFlag(shift: Int) {
            // Set bit at 'shift' position.
            flags = flags or (1 shl shift)
        }

        private fun setFlag(shift: Int, condition: Boolean) {
            // Set bit at 'shift' position if 'condition' is true, otherwise do nothing.
            flags = flags or ((if (condition) 1 else 0) shl shift)
        }

        // left top
        fun lt() = setFlag(LT_SHIFT)
        fun lt(condition: Boolean) = setFlag(LT_SHIFT, condition)

        // right top
        fun rt() = setFlag(RT_SHIFT)
        fun rt(condition: Boolean) = setFlag(RT_SHIFT, condition)

        // right bottom
        fun rb() = setFlag(RB_SHIFT)
        fun rb(condition: Boolean) = setFlag(RB_SHIFT, condition)

        // left bottom
        fun lb() = setFlag(LB_SHIFT)
        fun lb(condition: Boolean) = setFlag(LB_SHIFT, condition)

        private fun createMask(shift: Int): Int {
            // Creates conditional select mask.
            // If bit at 'shift' position is set, all bits set in mask, otherwise it's 0
            // Steps:
            // 1. Right-shift flags by 'shift' value to have the bit at LSB position.
            // 2. Then when we have 0 or 1 value, multiply it by -0x1 to get all bits set if the bit is set
            // and 0 if isn't.
            // (-0x1 in Kotlin because Int is signed and 0xFFFFFFFF fits on in Long,
            // so -0x1 is the way to get all bits set in 32-bit signed int).

            return -0x1 * (flags shr shift and 1)
        }

        inline fun withRadius(radius: Float, block: Radii.() -> Unit) {
            clear()

            currentRadius = radius
            block(this)
        }

        fun radii(): FloatArray {
            val radiusBits = currentRadius.toBits()

            initCorner(radiusBits, 0, LT_SHIFT)
            initCorner(radiusBits, 2, RT_SHIFT)
            initCorner(radiusBits, 4, RB_SHIFT)
            initCorner(radiusBits, 6, LB_SHIFT)

            return value
        }

        private fun initCorner(radiusBits: Int, offset: Int, shift: Int) {
            // createMask(shift) creates mask which is all bits set mask if bit at 'shift' position is set,
            // and 0 if isn't.
            // So, we need to binary AND bits of radius float to get the same value if the bit is set,
            // and 0 if isn't.
            //
            // Then we convert it back to float and init 'value' array.
            //
            // (0.0f in bit representation is 0, so Float.intBitsToFloat(0) = 0.0f).
            val cornerRadius = Float.fromBits(radiusBits and createMask(shift))

            value[offset] = cornerRadius
            value[offset + 1] = cornerRadius
        }
    }

    @JvmInline
    value class SetSelectionInfo(val bits: Long) {
        // Packed values positions:
        // 64-32 bits - data
        // 32-24 bits - type
        // 24-16 bits - withAnimation (1 if true, 0 if false)

        val type: SelectionType
            get() = SelectionType.ofOrdinal((bits shr 24).toInt() and 0xFF)

        val data: Int
            get() = (bits shr 32).toInt()

        val withAnimation: Boolean
            get() = (bits shr 16) and 0xFF == 1L

        companion object {
            val Undefined = create(SelectionType.NONE, -1, false)

            fun create(type: SelectionType, data: Int, withAnimation: Boolean): SetSelectionInfo {
                return SetSelectionInfo(
                    (data.toLong() shl 32) or
                            (type.ordinal.toLong() shl 24) or
                            ((if (withAnimation) 1L else 0L) shl 16)
                )
            }

            fun cell(cell: Cell, withAnimation: Boolean): SetSelectionInfo {
                return create(SelectionType.CELL, cell.index, withAnimation)
            }

            fun week(index: Int, withAnimation: Boolean): SetSelectionInfo {
                return create(SelectionType.WEEK, index, withAnimation)
            }

            fun month(withAnimation: Boolean): SetSelectionInfo {
                return create(SelectionType.MONTH, 0, withAnimation)
            }

            fun customRange(range: CellRange, withAnimation: Boolean): SetSelectionInfo {
                return create(SelectionType.CUSTOM, range.bits, withAnimation)
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

            val x = grid.getCellLeft(cell).toInt()
            val y = grid.getCellTop(cell).toInt()

            val cellSize = grid.cellSize.toInt()

            tempRect.set(x, y, x + cellSize, y + cellSize)

            node.apply {
                @Suppress("DEPRECATION")
                setBoundsInParent(tempRect)

                contentDescription = getDayDescriptionForIndex(virtualViewId)
                text = CalendarResources.getDayText(grid.cells[virtualViewId].toInt())
                isSelected = grid.selectionType == SelectionType.CELL &&
                        cell == grid.selectedRange.cell
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

    var onSelectionListener: OnSelectionListener? = null

    private var inMonthRange = CellRange.Invalid
    private var enabledCellRange = ALL_SELECTED
    private var todayCell = Cell.Undefined
    private var hoverCell = Cell.Undefined
    private var animationHoverCell = Cell.Undefined

    var ym = YearMonth(0)

    private var prevSelectionType = SelectionType.NONE
    private var selectionType = SelectionType.NONE

    private var prevSelectedRange = CellRange(0)
    private var selectedRange = CellRange(0)

    private var selectedWeekIndex = 0

    private var customRangePath: Path? = null
    private var customPathRange = CellRange.Invalid
    private var customRangeStartCell = Cell.Undefined
    private var isSelectingCustomRange = false
    private var customRangePathBounds = PackedRectF(0)

    private var animation = 0
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

    fun setSelectionFill(fill: Fill) = updateUIState {
        selectionFill = fill
    }

    fun setSelectionFillGradientBoundsType(value: SelectionFillGradientBoundsType) = updateUIState {
        selectionFillGradientBoundsType = value

        updateGradientBoundsIfNeeded()
    }

    fun setDayNumberTextSize(size: Float) = updateUIState {
        dayNumberPaint.textSize = size

        if (size != cr.dayNumberTextSize) {
            isDayNumberMeasurementsDirty = true

            refreshAllDecorVisualStates()
        }
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
        requestLayout()
    }

    fun setCellRoundRadius(value: Float) = updateUIState {
        require(value >= 0f) { "ratio < 0" }

        rrRadius = value

        refreshAllDecorVisualStates()

        if (selectionType == SelectionType.MONTH) {
            forceResetCustomPath()
        }
    }

    fun setInMonthRange(range: CellRange) {
        inMonthRange = range
        reselect()
    }

    fun setEnabledCellRange(range: CellRange) {
        enabledCellRange = range

        reselect()
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

    private fun reselect() {
        val savedSelectionType = selectionType
        val savedWeekIndex = selectedWeekIndex
        val savedRange = selectedRange

        clearSelection(fireEvent = false, doAnimation = true)

        when (savedSelectionType) {
            SelectionType.CELL -> selectCell(savedRange.cell, doAnimation = true)
            SelectionType.WEEK -> selectWeek(savedWeekIndex, doAnimation = true)
            SelectionType.MONTH -> selectMonth(doAnimation = true, reselect = true)
            SelectionType.CUSTOM -> selectCustom(savedRange, false)
            else -> {}
        }
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

        // Custom-path depends on size, we need to refresh it.
        if (selectionType == SelectionType.MONTH) {
            forceResetCustomPath()
        }

        updateGradientBoundsIfNeeded()
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

                        if (selectedRange != newRange) {
                            selectCustom(newRange, false)
                        }

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
        val size = cellSize.toInt()

        when (selectionType) {
            SelectionType.CELL -> {
                val left = getCellLeft(selectedRange.cell).toInt()
                val top = getCellTop(selectedRange.cell).toInt()

                r.set(left, top, left + size, top + size)
            }
            SelectionType.WEEK -> {
                val startIndex = selectedRange.start
                val endIndex = selectedRange.end

                val left = getCellLeft(startIndex).toInt()
                val right = getCellRight(endIndex).toInt()
                val top = getCellTop(startIndex).toInt()

                r.set(left, top, right, top + size)
            }

            else -> super.getFocusedRect(r)
        }
    }

    private fun sendClickEventToAccessibility(cell: Cell) {
        touchHelper.sendEventForVirtualView(cell.index, AccessibilityEvent.TYPE_VIEW_CLICKED)
    }

    fun select(info: SetSelectionInfo): Boolean {
        return select(info.type, info.data, info.withAnimation)
    }

    fun select(type: SelectionType, data: Int, doAnimation: Boolean): Boolean {
        return when (type) {
            SelectionType.CELL -> selectCell(Cell(data), doAnimation)
            SelectionType.WEEK -> selectWeek(data, doAnimation)
            SelectionType.MONTH -> selectMonth(doAnimation)
            SelectionType.CUSTOM -> selectCustom(CellRange(data), false)
            else -> false
        }
    }

    // Returns whether selection was actually changed
    private fun selectCell(cell: Cell, doAnimation: Boolean, isUser: Boolean = false): Boolean {
        if (selectionType == SelectionType.CELL && selectedRange.cell == cell) {
            // If it was user and behavior on selecting the same cell is CLEAR,
            // then we need to do it.
            if (isUser && clickOnCellSelectionBehavior == ClickOnCellSelectionBehavior.CLEAR) {
                clearSelection(fireEvent = true, doAnimation = true)

                return true
            }

            return false
        }

        // Check if cell is enabled
        if (!enabledCellRange.contains(cell)) {
            return false
        }

        // Call listeners and check if such selection is allowed.
        val listener = onSelectionListener
        if (listener != null) {
            val allowed = listener.onCellSelected(cell.index)
            if (!allowed) {
                return false
            }
        }

        if (hoverCell.isDefined) {
            clearHoverCellWithAnimation()
        }

        prevSelectionType = selectionType
        prevSelectedRange = selectedRange

        selectionType = SelectionType.CELL
        selectedRange = CellRange.cell(cell)

        // Start appropriate animation if it's necessary.
        if (doAnimation) {
            var animType = CELL_ALPHA_ANIMATION

            when (prevSelectionType) {
                SelectionType.CELL -> {
                    val prevCell = prevSelectedRange.cell

                    animType = if (prevCell.sameY(cell)) {
                        MOVE_CELL_ON_ROW_ANIMATION
                    } else {
                        if (prevCell.sameX(cell)) {
                            MOVE_CELL_ON_COLUMN_ANIMATION
                        } else {
                            DUAL_CELL_ALPHA_ANIMATION
                        }
                    }
                }
                SelectionType.WEEK -> {
                    animType = if (prevSelectedRange.contains(cell)) {
                        WEEK_TO_CELL_ON_ROW_ANIMATION
                    } else {
                        WEEK_TO_CELL_ANIMATION
                    }
                }
                SelectionType.MONTH -> {
                    animType = MONTH_TO_CELL_ANIMATION
                }
                else -> {}
            }

            startAnimation(animType)
        } else {
            invalidate()
        }

        return true
    }

    // Returns whether selection was actually changed
    private fun selectWeek(weekIndex: Int, doAnimation: Boolean): Boolean {
        // 1. Resolve week range.
        var start = Cell(weekIndex * 7)
        var end = start + 6

        val weekRange = CellRange(start, end).intersectionWidth(enabledCellRange)

        if (weekRange == CellRange.Invalid) {
            return false
        }

        start = weekRange.start
        end = weekRange.end

        // 2. If we're selecting the same range or range is cell, stop it or redirect to selectCell().
        if (selectionType == SelectionType.WEEK && selectedRange == weekRange) {
            return false
        } else if (end == start) {
            return selectCell(start, doAnimation)
        }

        // 3. Call listeners and check if such selection is allowed.
        val listener = onSelectionListener
        if (listener != null) {
            val allowed = listener.onWeekSelected(weekIndex, start.index, end.index)
            if (!allowed) {
                return false
            }
        }

        // 4. Update fields

        prevSelectionType = selectionType
        selectionType = SelectionType.WEEK

        selectedWeekIndex = weekIndex

        prevSelectedRange = selectedRange
        selectedRange = weekRange

        // 5. Start appropriate animation if it's necessary.
        if (doAnimation) {
            if (prevSelectionType == SelectionType.CELL && weekRange.contains(prevSelectedRange.cell)) {
                startAnimation(CELL_TO_WEEK_ON_ROW_ANIMATION)
            } else if (prevSelectionType == SelectionType.WEEK) {
                startAnimation(DUAL_WEEK_ANIMATION)
            }
        } else {
            invalidate()
        }

        return true
    }

    // Returns whether selection was actually changed
    fun selectMonth(doAnimation: Boolean, reselect: Boolean = false): Boolean {
        if (!reselect && selectionType == SelectionType.MONTH) {
            return false
        }

        prevSelectionType = selectionType
        selectionType = SelectionType.MONTH
        prevSelectedRange = selectedRange
        selectedRange = inMonthRange.intersectionWidth(enabledCellRange)

        // If selectedRange is empty, clear path.
        if (selectedRange == CellRange.Invalid) {
            customRangePath?.rewind()
        } else if (prevSelectedRange == selectedRange && prevSelectionType == SelectionType.MONTH) {
            return false
        }

        // Start appropriate animation if it's necessary.
        if (doAnimation) {
            if (prevSelectionType == SelectionType.CELL) {
                startAnimation(CELL_TO_MONTH_ANIMATION)
            } else {
                startAnimation(MONTH_ALPHA_ANIMATION)
            }
        } else {
            invalidate()
        }

        return true
    }

    // Returns whether selection was actually changed
    fun selectCustom(range: CellRange, startSelecting: Boolean): Boolean {
        val listener = onSelectionListener
        if (listener != null) {
            val allowed =
                listener.onCustomRangeSelected(range.start.index, range.end.index)
            if (!allowed) {
                stopSelectingCustomRange()
                return false
            }
        }

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

        prevSelectionType = selectionType
        selectionType = SelectionType.CUSTOM
        prevSelectedRange = selectedRange
        selectedRange = range.intersectionWidth(enabledCellRange)

        if (selectedRange == CellRange.Invalid) {
            customRangePath?.rewind()
        } else if (prevSelectedRange == selectedRange) {
            return false
        }

        invalidate()

        return true
    }

    private fun setHoverCell(cell: Cell) {
        if ((selectionType == SelectionType.CELL && selectedRange.cell == cell) || hoverCell == cell) {
            return
        }

        animationHoverCell = cell
        hoverCell = cell

        startAnimation(HOVER_ANIMATION)
    }

    fun clearHoverCellWithAnimation() {
        if (hoverCell.isDefined) {
            hoverCell = Cell.Undefined

            startAnimation(HOVER_ANIMATION or ANIMATION_REVERSE_BIT)
        }
    }

    fun clearSelection(fireEvent: Boolean, doAnimation: Boolean) {
        // Check if there's any select
        if (selectionType == SelectionType.NONE) {
            return
        }

        // Clear all fields.
        prevSelectionType = selectionType
        prevSelectedRange = selectedRange
        selectionType = SelectionType.NONE
        selectedWeekIndex = -1
        selectedRange = CellRange.Invalid

        // Fire event if it's demanded

        val listener = onSelectionListener
        if (fireEvent && listener != null) {
            listener.onSelectionCleared()
        }

        if (doAnimation) {
            startAnimation(CLEAR_SELECTION_ANIMATION or ANIMATION_REVERSE_BIT)
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

            startAnimation(
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

            startAnimation(
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

            startAnimation(
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

    private fun startAnimation(type: Int, handler: (() -> Unit)? = null, onEnd: Runnable? = null) {
        var animator = animator
        if (animator != null && animator.isRunning) {
            animator.end()
        }

        onAnimationEnd = onEnd
        animationHandler = handler
        animation = type

        if (animator == null) {
            animator = AnimationHelper.createFractionAnimator { value: Float ->
                animFraction = value
                animationHandler?.invoke()

                invalidate()
            }

            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    animation = NO_ANIMATION
                    onAnimationEnd?.run()

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

        if (type and ANIMATION_REVERSE_BIT != 0) {
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
        when (animation and ANIMATION_DATA_MASK) {
            MOVE_CELL_ON_ROW_ANIMATION -> {
                drawCellSelection(c, selectedRange.cell, animFraction, 1f)
            }
            MOVE_CELL_ON_COLUMN_ANIMATION -> {
                drawCellSelection(c, selectedRange.cell, 1f, animFraction)
            }
            DUAL_CELL_ALPHA_ANIMATION -> {
                // One cell is appearing, draw it with increasing alpha
                drawCellSelection(c, selectedRange.cell, 1f, 1f, alpha = animFraction)

                // Another cell is fading, draw it with decreasing alpha
                drawCellSelection(c, prevSelectedRange.cell, 1f, 1f, alpha = 1f - animFraction)
            }
            CELL_ALPHA_ANIMATION -> {
                drawCellSelection(c, selectedRange.cell, 1f, 1f, alpha = animFraction)
            }
            CELL_TO_WEEK_ON_ROW_ANIMATION -> {
                drawCellToWeekOnRowSelection(c, animFraction)
            }
            WEEK_TO_CELL_ON_ROW_ANIMATION -> {
                drawWeekToCellOnRowSelection(c, animFraction)
            }
            DUAL_WEEK_ANIMATION -> {
                // Selected week is appearing with two-way slide animation
                drawWeekFromCenterSelection(c, animFraction, selectedRange)

                // Previous selected week is fading with alpha animation (which decreases to 0)
                drawWeekFromCenterSelection(c, 1f, prevSelectedRange, alpha = 1f - animFraction)
            }
            WEEK_TO_CELL_ANIMATION -> {
                // Week to cell animation (not on row, it's another animation)

                // Previous selected week is fading with alpha animation
                drawWeekFromCenterSelection(c, 1f, prevSelectedRange, alpha = 1f - animFraction)

                // Newly selected cell is appearing with alpha animation
                drawCellSelection(c, selectedRange.cell, 1f, 1f, alpha = animFraction)
            }
            MONTH_ALPHA_ANIMATION -> {
                drawCustomRange(c, selectedRange, animFraction)
            }
            CLEAR_SELECTION_ANIMATION -> {
                drawSelectionNoAnimation(
                    c,
                    prevSelectionType,
                    prevSelectedRange,
                    alpha = animFraction
                )
            }
            CELL_TO_MONTH_ANIMATION -> {
                drawCellToMonthSelection(c)
            }
            MONTH_TO_CELL_ANIMATION -> {
                drawMonthToCellSelection(c)
            }
            else -> {
                drawSelectionNoAnimation(c, selectionType, selectedRange)
            }
        }
    }

    private fun drawSelectionNoAnimation(
        c: Canvas,
        type: SelectionType,
        range: CellRange,
        alpha: Float = 1f
    ) {
        when (type) {
            SelectionType.CELL -> drawCellSelection(c, range.cell, 1f, 1f, alpha)
            SelectionType.WEEK -> drawWeekFromCenterSelection(c, 1f, range, alpha)
            SelectionType.MONTH, SelectionType.CUSTOM -> drawCustomRange(c, range, alpha)
            else -> {}
        }
    }

    private fun drawCellToMonthSelection(c: Canvas) {
        drawCellMonthAnimation(c, animFraction, prevSelectedRange.cell, selectedRange)
    }

    private fun drawMonthToCellSelection(c: Canvas) {
        drawCellMonthAnimation(c, 1f - animFraction, selectedRange.cell, prevSelectedRange)
    }

    private fun drawCellMonthAnimation(
        c: Canvas,
        fraction: Float,
        cell: Cell,
        monthRange: CellRange
    ) {
        updateCustomRangePath(monthRange)

        val cellLeft = getCellLeft(cell)
        val cellTop = getCellTop(cell)

        val halfCellSize = cellSize * 0.5f
        val startX = cellLeft + halfCellSize
        val startY = cellTop + halfCellSize

        val finalRadius = getCircleRadiusForMonthAnimation(startX, startY)
        val currentRadius = lerp(halfCellSize, finalRadius, fraction)

        prepareSelectionFill(customRangePathBounds, alpha = 1f)

        c.withSave {
            clipPath(customRangePath!!)
            drawCircle(startX, startY, currentRadius, selectionPaint)
        }
    }

    private fun drawWeekSelection(
        c: Canvas,
        leftAnchor: Float,
        rightAnchor: Float,
        fraction: Float,
        weekRange: CellRange,
        alpha: Float = 1f
    ) {
        val start = weekRange.start
        val end = weekRange.end

        val rectLeft = getCellLeft(start)
        val rectTop = getCellTop(start)
        val rectRight = getCellRight(end)
        val rectBottom = rectTop + cellSize

        val left = lerp(leftAnchor, rectLeft, fraction)
        val right = lerp(rightAnchor, rectRight, fraction)

        prepareSelectionFill(left, rectTop, right, rectBottom, alpha)

        c.drawRoundRectCompat(
            left, rectTop, right, rectBottom,
            cellRoundRadius(), selectionPaint
        )
    }

    private fun drawWeekFromCenterSelection(
        c: Canvas,
        fraction: Float,
        range: CellRange,
        alpha: Float = 1f
    ) {
        val selStart = range.start.index
        val selEnd = range.end.index

        // It's merged from getCellLeft() and getCellRight() and Cell.gridX
        val midX =
            cr.hPadding + (selStart + selEnd - 14 * (selStart / 7) + 1).toFloat() * cellSize * 0.5f

        drawWeekSelection(c, midX, midX, fraction, range, alpha)
    }

    private fun drawWeekToCellOnRowSelection(c: Canvas, fraction: Float) {
        drawWeekSelectionOnRowFromCell(c, 1f - fraction, selectedRange.cell, prevSelectedRange)
    }

    private fun drawCellToWeekOnRowSelection(c: Canvas, fraction: Float) {
        drawWeekSelectionOnRowFromCell(c, fraction, prevSelectedRange.cell, selectedRange)
    }

    private fun drawWeekSelectionOnRowFromCell(
        c: Canvas,
        fraction: Float,
        cell: Cell,
        weekRange: CellRange
    ) {
        val cellLeft = getCellLeft(cell)
        val cellRight = cellLeft + cellSize

        drawWeekSelection(c, cellLeft, cellRight, fraction, weekRange)
    }

    private fun drawCustomRange(c: Canvas, range: CellRange, alpha: Float = 1f) {
        updateCustomRangePath(range)

        customRangePath?.let {
            prepareSelectionFill(customRangePathBounds, alpha)
            c.drawPath(it, selectionPaint)
        }
    }

    private fun drawCellSelection(
        c: Canvas,
        cell: Cell,
        xFraction: Float,
        yFraction: Float,
        alpha: Float = 1f
    ) {
        val prevSelectedCell = prevSelectedRange.cell

        val left: Float
        val top: Float
        val endLeft = getCellLeft(cell)
        val endTop = getCellTop(cell)

        left = if (xFraction != 1f) {
            lerp(getCellLeft(prevSelectedCell), endLeft, xFraction)
        } else {
            endLeft
        }

        top = if (yFraction != 1f) {
            lerp(getCellTop(prevSelectedCell), endTop, yFraction)
        } else {
            endTop
        }

        val right = left + cellSize
        val bottom = top + cellSize

        prepareSelectionFill(left, top, right, bottom, alpha)

        c.drawRoundRectCompat(
            left, top, right, bottom,
            cellRoundRadius(), selectionPaint
        )
    }

    private fun prepareSelectionFill(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        alpha: Float
    ) {
        if (selectionFillGradientBoundsType == SelectionFillGradientBoundsType.SHAPE) {
            selectionFill.setBounds(left, top, right, bottom)
        }

        selectionFill.applyToPaint(selectionPaint, alpha)
    }

    private fun prepareSelectionFill(bounds: PackedRectF, alpha: Float) {
        if (selectionFillGradientBoundsType == SelectionFillGradientBoundsType.SHAPE) {
            selectionFill.setBounds(bounds, RectangleShape)
        }

        selectionFill.applyToPaint(selectionPaint, alpha)
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
        val isHoverAnimation = (animation and ANIMATION_DATA_MASK) == HOVER_ANIMATION

        if ((isHoverAnimation && animationHoverCell.isDefined) || hoverCell.isDefined) {
            val cell = if (isHoverAnimation) animationHoverCell else hoverCell

            val left = getCellLeft(cell)
            val top = getCellTop(cell)

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

        for (i in 0..41) {
            val cell = Cell(i)

            val x = getCellLeft(cell)
            val y = getCellTop(cell)

            drawCell(c, x, y, cells[i].toInt(), resolveCellType(cell))
        }
    }

    private fun resolveCellType(cell: Cell): Int {
        var cellType = if (selectionType == SelectionType.CELL && selectedRange.cell == cell) {
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

    private fun cellTypeToColorStyle(type: Int) = when (type) {
        CELL_SELECTED, CELL_IN_MONTH -> COLOR_STYLE_IN_MONTH
        CELL_OUT_MONTH -> COLOR_STYLE_OUT_MONTH
        CELL_DISABLED -> COLOR_STYLE_DISABLED
        CELL_TODAY -> if ((type and CELL_HOVER_BIT) != 0)
            COLOR_STYLE_IN_MONTH
        else
            COLOR_STYLE_TODAY
        else -> throw IllegalArgumentException("type")
    }

    private fun drawCell(c: Canvas, x: Float, y: Float, day: Int, cellType: Int) {
        if (day > 0) {
            val colorStyle = cellTypeToColorStyle(cellType)

            val size = getDayNumberSize(day)
            val halfCellSize = cellSize * 0.5f

            val textX = x + halfCellSize - size.width * 0.5f
            val textY = y + halfCellSize + size.height * 0.5f

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
        var cb = decorAnimationHandler
        if (cb == null) {
            cb = this::handleDecorationAnimation
            decorAnimationHandler = cb
        }

        return cb
    }

    private fun handleDecorationAnimation() {
        if (animation and ANIMATION_DATA_MASK == DECOR_ANIMATION) {
            val state = decorVisualStates[decorAnimatedCell]

            if (state is CellDecor.VisualState.Transitive) {
                state.handleAnimation(animFraction, decorAnimFractionInterpolator!!)
            }
        }
    }

    private fun drawDecorations(c: Canvas) {
        decorVisualStates.forEachNotNull { cell, state ->
            val dx = getCellLeft(cell)
            val dy = getCellTop(cell)

            c.translate(dx, dy)

            state.visual().renderer().renderState(c, state)

            c.translate(-dx, -dy)
        }
    }

    private fun updateCustomRangePath(range: CellRange) {
        // Check whether we need to proceed
        if (customPathRange == range) {
            return
        }

        customPathRange = range

        var path = customRangePath

        if (path == null) {
            path = Path()
            customRangePath = path
        } else {
            path.rewind()
        }

        val start = range.start
        val end = range.end

        val radius = cellRoundRadius()

        if (start.sameY(end)) {
            val left = getCellLeft(start)
            val right = getCellRight(end)

            val top = getCellTop(start)
            val bottom = top + cellSize

            customRangePathBounds = PackedRectF(left, top, right, bottom)

            path.addRoundRectCompat(left, top, right, bottom, radius)
        } else {
            val firstCellOnRowX = cr.hPadding + (columnWidth - cellSize) * 0.5f
            val lastCellOnRowRight = cr.hPadding + columnWidth * 6.5f + cellSize * 0.5f

            val startLeft = getCellLeft(start)
            val startTop = getCellTop(start)
            val startBottom = startTop + cellSize

            val endRight = getCellRight(end)
            val endTop = getCellTop(end)
            val endBottom = endTop + cellSize

            val gridYDiff = end.gridY - start.gridY

            customRangePathBounds = PackedRectF(startLeft, startTop, endRight, endBottom)

            Radii.withRadius(radius) {
                lt()
                rt()
                lb(start.gridX != 0)
                rb(gridYDiff == 1 && end.gridX != 6)

                path.addRoundRectCompat(
                    startLeft, startTop, lastCellOnRowRight, startBottom, radii()
                )
            }

            Radii.withRadius(radius) {
                rb()
                lb()
                rt(end.gridX != 6)
                lt(gridYDiff == 1 && start.gridX != 0)

                path.addRoundRectCompat(
                    firstCellOnRowX,
                    if (gridYDiff == 1) startBottom else endTop,
                    endRight,
                    endBottom,
                    radii()
                )
            }

            if (gridYDiff > 1) {
                Radii.withRadius(radius) {
                    lt(start.gridX != 0)
                    rb(end.gridX != 6)

                    path.addRoundRectCompat(
                        firstCellOnRowX,
                        startBottom,
                        lastCellOnRowRight,
                        endTop,
                        radii()
                    )
                }
            }
        }
    }

    private fun getCircleRadiusForMonthAnimation(x: Float, y: Float): Float {
        // Find max radius for circle (with center in (x; y)) to fully fit in month selection.
        // Try distance to left, top, right, bottom corners.
        // Then try distance to start and end cells.

        val distToLeftCorner = x - cr.hPadding
        val distToRightCorner = width - distToLeftCorner
        val distToTopCorner = y - gridTop()

        val intersection = enabledCellRange.intersectionWidth(inMonthRange)
        if (intersection == CellRange.Invalid) {
            return Float.NaN
        }

        val startCell = intersection.start
        val endCell = intersection.end

        val startCellLeft = getCellLeft(startCell)
        val startCellTop = getCellTop(startCell)
        val endCellLeft = getCellLeft(endCell)
        val endCellBottom = getCellTop(endCell) + cellSize

        val distToStartCellSq = distanceSquare(startCellLeft, startCellTop, x, y)
        val distToEndCellSq = distanceSquare(endCellLeft, endCellBottom, x, y)

        var maxDist = max(distToLeftCorner, distToRightCorner)
        maxDist = max(maxDist, distToTopCorner)

        // Save on expensive sqrt() call: max(sqrt(a), sqrt(b)) => sqrt(max(a, b))
        maxDist = max(maxDist, sqrt(max(distToStartCellSq, distToEndCellSq)))

        return maxDist
    }

    private fun forceResetCustomPath() {
        // By setting it to CellRange.Invalid, up-to-date check in updateCustomRangePath() will fail
        // and by that, custom path will be updated on first demand.
        customPathRange = CellRange.Invalid
    }

    private fun onGridTopChanged() {
        if (selectionType == SelectionType.MONTH) {
            forceResetCustomPath()
        }

        updateGradientBoundsIfNeeded()

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

    private fun getCellLeft(cell: Cell): Float {
        return cr.hPadding + columnWidth * (cell.gridX + 0.5f) - cellSize * 0.5f
    }

    private fun getCellTop(cell: Cell): Float {
        val gridY = cell.gridY

        return gridTop() + gridY * cellSize + gridY * cr.yCellMargin
    }

    private fun getCellRight(cell: Cell): Float {
        return getCellLeft(cell) + cellSize
    }

    private fun isSelectionRangeContains(cell: Cell): Boolean {
        val selType = selectionType

        return selType != SelectionType.NONE && selType != SelectionType.CELL &&
                selectedRange.contains(cell)
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
        private const val CELL_TO_WEEK_ON_ROW_ANIMATION = 1
        private const val WEEK_TO_CELL_ON_ROW_ANIMATION = 2
        private const val MOVE_CELL_ON_ROW_ANIMATION = 3
        private const val MOVE_CELL_ON_COLUMN_ANIMATION = 4
        private const val HOVER_ANIMATION = 5
        private const val DUAL_CELL_ALPHA_ANIMATION = 6
        private const val CELL_ALPHA_ANIMATION = 7
        private const val DUAL_WEEK_ANIMATION = 8
        private const val WEEK_TO_CELL_ANIMATION = 9
        private const val CLEAR_SELECTION_ANIMATION = 10
        private const val MONTH_ALPHA_ANIMATION = 11
        private const val CELL_TO_MONTH_ANIMATION = 12
        private const val MONTH_TO_CELL_ANIMATION = 13
        private const val DECOR_ANIMATION = 14

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