package io.github.pelmenstar1.rangecalendar

import android.animation.Animator
import android.annotation.SuppressLint
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.content.Context
import android.graphics.*
import androidx.core.view.ViewCompat
import android.os.*
import android.text.format.DateFormat
import android.view.View
import androidx.annotation.ColorInt
import androidx.core.graphics.withSave
import androidx.customview.widget.ExploreByTouchHelper
import io.github.pelmenstar1.rangecalendar.selection.Cell
import io.github.pelmenstar1.rangecalendar.selection.CellRange
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.max
import kotlin.math.sqrt

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
            flags = flags or (1 shl shift)
        }

        private fun setFlag(shift: Int, condition: Boolean) {
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
            // The idea: to create mask which is 0xFFFFFFFF if bit at specified position (shift) is set, otherwise mask is 0
            return -0x1 * (flags shr shift and 1)
        }

        inline fun withRadius(radius: Float, block: Radii.() -> Unit) {
            clear()
            currentRadius = radius
            block(this)
        }

        fun radii(): FloatArray {
            val radiusBits = java.lang.Float.floatToRawIntBits(currentRadius)

            initCorner(radiusBits, 0, LT_SHIFT)
            initCorner(radiusBits, 2, RT_SHIFT)
            initCorner(radiusBits, 4, RB_SHIFT)
            initCorner(radiusBits, 6, LB_SHIFT)

            return value
        }

        private fun initCorner(radiusBits: Int, offset: Int, shift: Int) {
            value[offset] = java.lang.Float.intBitsToFloat(radiusBits and createMask(shift))
            value[offset + 1] = value[offset]
        }
    }

    object PackedSelectionInfo {
        fun create(type: Int, data: Int, withAnimation: Boolean): Long {
            return (data.toLong() shl 32) or
                    (type.toLong() shl 24) or
                    ((if (withAnimation) 1L else 0L) shl 16)
        }

        fun getType(packed: Long): Int {
            return (packed shr 24).toInt() and 0xFF
        }

        fun getData(packed: Long): Int {
            return (packed shr 32).toInt()
        }

        fun getWithAnimation(packed: Long): Boolean {
            return packed shr 16 and 0xFF == 1L
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
            virtualViewIds.addAll(INDCIES)
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

            private val INDCIES = ArrayList<Int>(42).also {
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

    private val tempRect = RectF()

    internal var cellSize: Float
    private var columnWidth = 0f
    private var rrRadiusRatio = DEFAULT_RR_RADIUS_RATIO

    private val dayNumberPaint: Paint
    private val weekdayPaint: Paint
    private val selectionPaint: Paint
    private val cellHoverPaint: Paint
    private val cellHoverOnSelectionPaint: Paint

    private var inMonthColor: Int
    private var outMonthColor: Int
    private var disabledDayNumberColor: Int
    private var todayColor: Int

    private var lastTouchTime: Long = -1
    private var lastTouchCell = Cell.Undefined

    var onSelectionListener: OnSelectionListener? = null

    private var inMonthRange = CellRange.Invalid
    private var enabledCellRange = ALL_SELECTED
    private var todayCell = Cell.Undefined
    private var hoverCell = Cell.Undefined
    private var animationHoverCell = Cell.Undefined

    var ym = YearMonth(0)

    private var prevSelectionType = 0
    private var selectionType = 0

    private var prevSelectedRange = CellRange(0)
    private var selectedRange = CellRange(0)

    private var selectedWeekIndex = 0

    private var customRangePath: Path? = null
    private var customPathRange = CellRange.Invalid
    private var customRangeStartCell = Cell.Undefined
    private var isSelectingCustomRange = false

    private var animation = 0
    private var animFraction = 0f
    private var animator: ValueAnimator? = null

    var isFirstDaySunday = false
    private val touchHelper: TouchHelper

    private var weekdayType = WeekdayType.SHORT
    var clickOnCellSelectionBehavior = 0
    var commonAnimationDuration = DEFAULT_COMMON_ANIM_DURATION
    var hoverAnimationDuration = DEFAULT_HOVER_ANIM_DURATION
    var commonAnimationInterpolator: TimeInterpolator = LINEAR_INTERPOLATOR
    var hoverAnimationInterpolator: TimeInterpolator = LINEAR_INTERPOLATOR

    private val vibrator: Vibrator
    var vibrateOnSelectingCustomRange = true

    private var gradient: LinearGradient? = null
    private var gradientColors: IntArray? = null

    private val pressTimeoutHandler = PressTimeoutHandler(this)

    init {
        val defTextColor = cr.textColor
        val colorPrimary = cr.colorPrimary
        val dayNumberTextSize = cr.dayNumberTextSize

        touchHelper = TouchHelper(this)
        ViewCompat.setAccessibilityDelegate(this, touchHelper)

        cellSize = cr.cellSize
        inMonthColor = defTextColor
        outMonthColor = cr.outMonthTextColor
        disabledDayNumberColor = cr.disabledTextColor
        todayColor = colorPrimary

        background = null

        selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorPrimary
            style = Paint.Style.FILL
        }

        dayNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = dayNumberTextSize
            color = defTextColor
        }

        weekdayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = cr.weekdayTextSize
            color = defTextColor
            typeface = Typeface.DEFAULT_BOLD
        }

        cellHoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = cr.hoverColor
        }

        cellHoverOnSelectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = cr.colorPrimaryDark
        }

        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager

            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun setSelectionColor(@ColorInt color: Int) {
        selectionPaint.color = color
        invalidate()
    }

    fun setDayNumberTextSize(size: Float) {
        dayNumberPaint.textSize = size
        invalidate()
    }

    fun setInMonthDayNumberColor(@ColorInt color: Int) {
        inMonthColor = color
        invalidate()
    }

    fun setOutMonthDayNumberColor(@ColorInt color: Int) {
        outMonthColor = color
        invalidate()
    }

    fun setDisabledDayNumberColor(@ColorInt color: Int) {
        disabledDayNumberColor = color
        invalidate()
    }

    fun setTodayColor(@ColorInt color: Int) {
        todayColor = color
        invalidate()
    }

    fun setDayNameColor(@ColorInt color: Int) {
        weekdayPaint.color = color
        invalidate()
    }

    fun setDayNameTextSize(size: Float) {
        weekdayPaint.textSize = size
        invalidate()
    }

    fun setHoverColor(@ColorInt color: Int) {
        cellHoverPaint.color = color
        invalidate()
    }

    fun setHoverOnSelectionColor(@ColorInt color: Int) {
        cellHoverOnSelectionPaint.color = color
        invalidate()
    }

    fun getCellSize(): Float {
        return cellSize
    }

    fun setCellSize(size: Float) {
        require(size > 0f) { "size <= 0" }

        cellSize = size

        refreshColumnWidth()
        requestLayout()
    }

    var roundRectRadiusRatio: Float
        get() = rrRadiusRatio
        set(ratio) {
            require(ratio >= 0f) { "ratio < 0" }

            rrRadiusRatio = ratio.coerceAtMost(0.5f)

            if (selectionType == SelectionType.MONTH) {
                forceResetCustomPath()
            }

            invalidate()
        }

    fun setInMonthRange(range: CellRange) {
        inMonthRange = range
        reselect()
    }

    fun setEnabledCellRange(range: CellRange) {
        enabledCellRange = range

        reselect()
    }

    fun setWeekdayType(@WeekdayTypeInt _type: Int) {
        // There is no narrow weekdays before API < 24
        val type = WeekdayType.resolve(_type)

        require(type == WeekdayType.SHORT || type == WeekdayType.NARROW) {
            "Invalid weekday type. Use constants from WeekdayType"
        }

        weekdayType = type

        if (selectionType == SelectionType.MONTH) {
            forceResetCustomPath()
        }

        invalidate()
        touchHelper.invalidateRoot()
    }

    fun setTodayCell(cell: Cell) {
        todayCell = cell
        invalidate()
    }

    fun setGradientEnabled(state: Boolean) {
        if (state) {
            if (gradientColors == null) {
                gradientColors = intArrayOf(cr.colorPrimary, cr.colorPrimaryDark)
            }

            updateGradient()
        } else {
            // Don't clear gradientColors because it should be used if gradient is enabled next time.
            selectionPaint.shader = null
            gradient = null
            invalidate()
        }
    }

    fun setGradientColors(@ColorInt start: Int, @ColorInt end: Int) {
        var colors = gradientColors
        if (colors == null) {
            colors = IntArray(2)
            gradientColors = colors
        }

        if (colors[0] != start || colors[1] != end) {
            colors[0] = start
            colors[1] = end

            updateGradient()
        }
    }

    private fun updateGradient() {
        val colors = gradientColors ?: return

        val fw = width.toFloat()
        val fh = height.toFloat()

        gradient = LinearGradient(
            cr.hPadding, gridTop(),
            fw - cr.hPadding, fh,
            colors, null,
            Shader.TileMode.CLAMP
        )
        selectionPaint.shader = gradient

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
        }
    }

    private fun rrRadius(): Float = cellSize * rrRadiusRatio

    private fun refreshColumnWidth() {
        val width = width.toFloat()

        columnWidth = (width - cr.hPadding * 2) * (1f / 7)
        invalidate()
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

        if (selectionType == SelectionType.MONTH) {
            forceResetCustomPath()
        }

        updateGradient()
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

                    pressTimeoutHandler.removeCallbacksAndMessages(null)

                    hoverCell = Cell.Undefined
                    stopSelectingCustomRange()

                    invalidate()
                }
                MotionEvent.ACTION_CANCEL -> {
                    pressTimeoutHandler.removeCallbacksAndMessages(null)

                    clearHoverIndex()
                    stopSelectingCustomRange()

                    invalidate()
                }
                MotionEvent.ACTION_MOVE -> if (isSelectingCustomRange && isXInActiveZone(x) && y > gridTop()) {
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

    fun select(packed: Long) {
        select(
            PackedSelectionInfo.getType(packed),
            PackedSelectionInfo.getData(packed),
            PackedSelectionInfo.getWithAnimation(packed)
        )
    }

    fun select(type: Int, data: Int, doAnimation: Boolean) {
        when (type) {
            SelectionType.CELL -> selectCell(Cell(data), doAnimation)
            SelectionType.WEEK -> selectWeek(data, doAnimation)
            SelectionType.MONTH -> selectMonth(doAnimation)
            SelectionType.CUSTOM -> selectCustom(CellRange(data), false)
        }
    }

    private fun selectCell(cell: Cell, doAnimation: Boolean, isUser: Boolean = false) {
        if (selectionType == SelectionType.CELL && selectedRange.cell == cell) {
            if (isUser && clickOnCellSelectionBehavior == ClickOnCellSelectionBehavior.CLEAR) {
                clearSelection(fireEvent = true, doAnimation = true)
            }

            return
        }

        if (!enabledCellRange.contains(cell)) {
            return
        }

        val listener = onSelectionListener
        if (listener != null) {
            val allowed = listener.onCellSelected(cell.index)
            if (!allowed) {
                return
            }
        }

        if (hoverCell.isDefined) {
            clearHoverIndex()
        }

        prevSelectionType = selectionType
        prevSelectedRange = selectedRange

        selectionType = SelectionType.CELL
        selectedRange = CellRange.cell(cell)

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
            }

            startAnimation(animType)
        } else {
            invalidate()
        }
    }

    private fun selectWeek(weekIndex: Int, doAnimation: Boolean) {
        var start = Cell(weekIndex * 7)
        var end = start + 6

        val weekRange = CellRange(start, end).intersectionWidth(enabledCellRange)

        if (weekRange == CellRange.Invalid) {
            return
        }

        start = weekRange.start
        end = weekRange.end

        if (selectionType == SelectionType.WEEK && selectedRange == weekRange) {
            return
        } else if (end == start) {
            selectCell(start, doAnimation)
            return
        }

        val listener = onSelectionListener
        if (listener != null) {
            val allowed = listener.onWeekSelected(weekIndex, start.index, end.index)
            if (!allowed) {
                return
            }
        }

        prevSelectionType = selectionType
        selectionType = SelectionType.WEEK

        selectedWeekIndex = weekIndex

        prevSelectedRange = selectedRange
        selectedRange = weekRange

        if (doAnimation) {
            if (prevSelectionType == SelectionType.CELL && weekRange.contains(prevSelectedRange.cell)) {
                startAnimation(CELL_TO_WEEK_ON_ROW_ANIMATION)
            } else if (prevSelectionType == SelectionType.WEEK) {
                startAnimation(DUAL_WEEK_ANIMATION)
            }
        } else {
            invalidate()
        }
    }

    fun selectMonth(doAnimation: Boolean) {
        selectMonth(doAnimation, reselect = false)
    }

    fun selectMonth(doAnimation: Boolean, reselect: Boolean) {
        if (!reselect && selectionType == SelectionType.MONTH) {
            return
        }

        prevSelectionType = selectionType
        selectionType = SelectionType.MONTH
        prevSelectedRange = selectedRange
        selectedRange = inMonthRange.intersectionWidth(enabledCellRange)

        if (selectedRange == CellRange.Invalid) {
            customRangePath?.rewind()
        } else if (prevSelectedRange == selectedRange && prevSelectionType == SelectionType.MONTH) {
            return
        }

        if (doAnimation) {
            if (prevSelectionType == SelectionType.CELL) {
                startAnimation(CELL_TO_MONTH_ANIMATION)
            } else {
                startAnimation(MONTH_ALPHA_ANIMATION)
            }
        } else {
            invalidate()
        }
    }

    fun selectCustom(range: CellRange, startSelecting: Boolean) {
        val listener = onSelectionListener
        if (listener != null) {
            val allowed =
                listener.onCustomRangeSelected(range.start.index, range.end.index)
            if (!allowed) {
                stopSelectingCustomRange()
                return
            }
        }

        if (vibrateOnSelectingCustomRange && startSelecting) {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        VIBRATE_DURATION.toLong(),
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(VIBRATE_DURATION.toLong())
            }
        }

        // Clear hover here and not in onCellLongPress() because custom range might be disallowed and
        // hover will be cleared but it shouldn't
        clearHoverIndex()
        prevSelectionType = selectionType
        selectionType = SelectionType.CUSTOM
        prevSelectedRange = selectedRange
        selectedRange = range.intersectionWidth(enabledCellRange)

        if (selectedRange == CellRange.Invalid) {
            customRangePath?.rewind()
        } else if (prevSelectedRange == selectedRange) {
            return
        }

        invalidate()
    }

    private fun setHoverCell(cell: Cell) {
        if ((selectionType == SelectionType.CELL && selectedRange.cell == cell) || hoverCell == cell) {
            return
        }

        animationHoverCell = cell
        hoverCell = cell

        startAnimation(HOVER_ANIMATION)
    }

    fun clearHoverIndex() {
        if (hoverCell.isDefined) {
            hoverCell = Cell.Undefined

            startAnimation(HOVER_ANIMATION or ANIMATION_REVERSE_BIT)
        }
    }

    fun clearSelection(fireEvent: Boolean, doAnimation: Boolean) {
        if (selectionType == SelectionType.NONE) {
            return
        }

        prevSelectionType = selectionType
        prevSelectedRange = selectedRange
        selectionType = SelectionType.NONE
        selectedWeekIndex = -1
        selectedRange = CellRange.Invalid

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

    private fun startAnimation(type: Int) {
        var animator = animator
        if (animator != null && animator.isRunning) {
            animator.end()
        }

        animation = type

        if (animator == null) {
            animator = AnimationHelper.createFractionAnimator { value: Float ->
                animFraction = value
                invalidate()
            }
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) {
                    animation = NO_ANIMATION
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
        drawSelection(c)
        drawWeekdayRow(c)
        drawHover(c)
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
                val alpha = (animFraction * 255f).toInt()

                selectionPaint.alpha = alpha
                drawCellSelection(c, selectedRange.cell, 1f, 1f)

                selectionPaint.alpha = 255 - alpha
                drawCellSelection(c, prevSelectedRange.cell, 1f, 1f)

                selectionPaint.alpha = 255
            }
            CELL_ALPHA_ANIMATION -> {
                val alpha = (animFraction * 255f).toInt()

                selectionPaint.alpha = alpha
                drawCellSelection(c, selectedRange.cell, 1f, 1f)
                selectionPaint.alpha = 255
            }
            CELL_TO_WEEK_ON_ROW_ANIMATION -> {
                drawCellToWeekOnRowSelection(c, animFraction)
            }
            WEEK_TO_CELL_ON_ROW_ANIMATION -> {
                drawWeekToCellOnRowSelection(c, animFraction)
            }
            DUAL_WEEK_ANIMATION -> {
                drawWeekFromCenterSelection(c, animFraction, selectedRange)
                selectionPaint.alpha = 255 - (animFraction * 255).toInt()

                drawWeekFromCenterSelection(c, 1f, prevSelectedRange)
                selectionPaint.alpha = 255
            }
            WEEK_TO_CELL_ANIMATION -> {
                val alpha = (animFraction * 255).toInt()

                selectionPaint.alpha = 255 - alpha
                drawWeekFromCenterSelection(c, 1f, prevSelectedRange)

                selectionPaint.alpha = alpha
                drawCellSelection(c, selectedRange.cell, 1f, 1f)

                selectionPaint.alpha = 255
            }
            MONTH_ALPHA_ANIMATION -> {
                selectionPaint.alpha = (animFraction * 255f).toInt()
                drawCustomRange(c, selectedRange)

                selectionPaint.alpha = 255
            }
            CLEAR_SELECTION_ANIMATION -> {
                selectionPaint.alpha = (animFraction * 255).toInt()
                drawSelectionNoAnimation(c, prevSelectionType, prevSelectedRange)

                selectionPaint.alpha = 255
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

    private fun drawSelectionNoAnimation(c: Canvas, sType: Int, range: CellRange) {
        when (sType) {
            SelectionType.CELL -> drawCellSelection(c, range.cell, 1f, 1f)
            SelectionType.WEEK -> drawWeekFromCenterSelection(c, 1f, range)
            SelectionType.MONTH, SelectionType.CUSTOM -> drawCustomRange(c, range)
        }
    }

    private fun drawCellToMonthSelection(c: Canvas) {
        drawCellMonthAnimation(c, animFraction, prevSelectedRange.cell, selectedRange)
    }

    private fun drawMonthToCellSelection(c: Canvas) {
        drawCellMonthAnimation(c, 1f - animFraction, selectedRange.cell, prevSelectedRange)
    }

    private fun drawCellMonthAnimation(c: Canvas, fraction: Float, cell: Cell, monthRange: CellRange) {
        updateCustomRangePath(monthRange)

        val cellLeft = getCellLeft(cell)
        val cellTop = getCellTop(cell)

        val halfCellSize = cellSize * 0.5f
        val startX = cellLeft + halfCellSize
        val startY = cellTop + halfCellSize

        val finalRadius = getCircleRadiusForMonthAnimation(startX, startY)
        val currentRadius = lerp(halfCellSize, finalRadius, fraction)

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
        weekRange: CellRange
    ) {
        val start = weekRange.start
        val end = weekRange.end

        val rectLeft = getCellLeft(start)
        val rectTop = getCellTop(start)
        val rectRight = getCellRight(end)
        val rectBottom = rectTop + cellSize

        val left = lerp(leftAnchor, rectLeft, fraction)
        val right = lerp(rightAnchor, rectRight, fraction)

        c.drawRoundRectCompat(
            left, rectTop, right, rectBottom,
            rrRadius(), selectionPaint
        )
    }

    private fun drawWeekFromCenterSelection(c: Canvas, fraction: Float, range: CellRange) {
        val selStart = range.start.index
        val selEnd = range.end.index

        val midX =
            cr.hPadding + (selStart + selEnd - 14 * (selStart / 7) + 1).toFloat() * cellSize * 0.5f

        drawWeekSelection(c, midX, midX, fraction, range)
    }

    private fun drawWeekToCellOnRowSelection(c: Canvas, fraction: Float) {
        drawWeekSelectionOnRowFromCell(c, 1f - fraction, selectedRange.cell, prevSelectedRange)
    }

    private fun drawCellToWeekOnRowSelection(c: Canvas, fraction: Float) {
        drawWeekSelectionOnRowFromCell(c, fraction, prevSelectedRange.cell, selectedRange)
    }

    private fun drawWeekSelectionOnRowFromCell(c: Canvas, fraction: Float, cell: Cell, weekRange: CellRange) {
        val cellLeft = getCellLeft(cell)
        val cellRight = cellLeft + cellSize

        drawWeekSelection(c, cellLeft, cellRight, fraction, weekRange)
    }

    private fun drawCustomRange(c: Canvas, range: CellRange) {
        updateCustomRangePath(range)

        customRangePath?.let { c.drawPath(it, selectionPaint) }
    }

    private fun drawCellSelection(c: Canvas, cell: Cell, xFraction: Float, yFraction: Float) {
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

        c.drawRoundRectCompat(
            left, top, left + cellSize, top + cellSize,
            rrRadius(), selectionPaint
        )
    }

    private fun drawWeekdayRow(c: Canvas) {
        var x = cr.hPadding
        val offset = if (weekdayType == WeekdayType.SHORT)
            CalendarResources.SHORT_WEEKDAYS_OFFSET
        else
            CalendarResources.NARROW_WEEKDAYS_OFFSET

        val startIndex = if (isFirstDaySunday) 0 else 1

        val halfColumnWidth = columnWidth * 0.5f

        for (i in offset + startIndex until offset + 7) {
            drawWeekday(c, i, x + halfColumnWidth)

            x += columnWidth
        }

        if (!isFirstDaySunday) {
            drawWeekday(c, offset, x + halfColumnWidth)
        }
    }

    private fun drawWeekday(c: Canvas, index: Int, midX: Float) {
        val textX = midX - (cr.weekdayWidths[index] / 2).toFloat()

        c.drawText(cr.weekdays[index], textX, cr.shortWeekdayRowHeight, weekdayPaint)
    }

    private fun drawHover(c: Canvas) {
        val isHoverAnimation = (animation and ANIMATION_DATA_MASK) == HOVER_ANIMATION
        if ((isHoverAnimation && animationHoverCell.isDefined) || hoverCell.isDefined) {
            val cell = if (isHoverAnimation) animationHoverCell else hoverCell

            val paint = if (isSelectionRangeContains(cell))
                cellHoverOnSelectionPaint
            else
                cellHoverPaint

            val paintAlpha = paint.alpha
            val resultAlpha = if (isHoverAnimation)
                (paintAlpha * animFraction).toInt()
            else
                paintAlpha

            val left = getCellLeft(cell)
            val top = getCellTop(cell)

            paint.alpha = resultAlpha

            c.drawRoundRectCompat(
                left, top, left + cellSize, top + cellSize,
                rrRadius(), paint
            )

            paint.alpha = paintAlpha
        }
    }

    private fun drawCells(c: Canvas) {
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

    private fun drawCell(c: Canvas, x: Float, y: Float, day: Int, cellType: Int) {
        val color = when (cellType and CELL_DATA_MASK) {
            CELL_SELECTED, CELL_IN_MONTH -> inMonthColor
            CELL_OUT_MONTH -> outMonthColor
            CELL_DISABLED -> disabledDayNumberColor
            CELL_TODAY -> if (cellType and CELL_HOVER_BIT != 0) inMonthColor else todayColor
            else -> 0
        }

        if (day > 0) {
            val size = cr.dayNumberSizes[day - 1]
            val halfCellSize = cellSize * 0.5f

            val textX = x + halfCellSize - (PackedSize.getWidth(size) / 2).toFloat()
            val textY = y + halfCellSize + (PackedSize.getHeight(size) / 2).toFloat()

            dayNumberPaint.color = color
            c.drawText(CalendarResources.getDayText(day), textX, textY, dayNumberPaint)
        }
    }

    private fun updateCustomRangePath(range: CellRange) {
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

        val radius = rrRadius()

        if (start.sameY(end)) {
            val left = getCellLeft(start)
            val right = getCellRight(end)

            val top = getCellTop(start)
            val bottom = top + cellSize

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

                    tempRect.set(firstCellOnRowX, startBottom, lastCellOnRowRight, endTop)
                    if (start.gridX == 0 && end.gridX == 6) {
                        path.addRect(tempRect, Path.Direction.CW)
                    } else {
                        path.addRoundRect(tempRect, radii(), Path.Direction.CW)
                    }
                }
            }
        }
    }

    private fun getCircleRadiusForMonthAnimation(x: Float, y: Float): Float {
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
        maxDist = max(maxDist, sqrt(max(distToStartCellSq, distToEndCellSq)))

        return maxDist
    }

    private fun forceResetCustomPath() {
        customPathRange = CellRange.Invalid
    }

    private fun gridTop(): Float {
        val height = if (weekdayType == WeekdayType.SHORT) {
            cr.shortWeekdayRowHeight
        } else {
            cr.narrowWeekdayRowHeight
        }

        return height + cr.weekdayRowMarginBottom
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

    private fun isXInActiveZone(x: Float): Boolean {
        return x >= cr.hPadding && x <= width - cr.hPadding
    }

    private fun Canvas.drawRoundRectCompat(
        left: Float, top: Float, right: Float, bottom: Float,
        radius: Float,
        paint: Paint
    ) {
        tempRect.set(left, top, right, bottom)
        drawRoundRect(tempRect, radius, radius, paint)
    }

    private fun Path.addRoundRectCompat(
        left: Float, top: Float, right: Float, bottom: Float,
        radii: FloatArray
    ) {
        tempRect.set(left, top, right, bottom)
        addRoundRect(tempRect, radii, Path.Direction.CW)
    }

    private fun Path.addRoundRectCompat(
        left: Float, top: Float, right: Float, bottom: Float,
        radius: Float
    ) {
        tempRect.set(left, top, right, bottom)
        addRoundRect(tempRect, radius, radius, Path.Direction.CW)
    }

    companion object {
        const val DEFAULT_RR_RADIUS_RATIO = 0.5f

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
        private const val VIBRATE_DURATION = 50
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
    }
}