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
                grid.getGridIndexByPointOnScreen(x, y)
            } else INVALID_ID
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            virtualViewIds.addAll(INDCIES)
        }

        override fun onPopulateNodeForVirtualView(
            virtualViewId: Int,
            node: AccessibilityNodeInfoCompat
        ) {
            val gridY = virtualViewId / 7
            val gridX = virtualViewId - gridY * 7
            val x = grid.getCellLeft(gridX).toInt()
            val y = grid.getCellTop(gridY).toInt()
            val cellSize = grid.cellSize.toInt()

            tempRect.set(x, y, x + cellSize, y + cellSize)

            node.apply {
                @Suppress("DEPRECATION")
                setBoundsInParent(tempRect)

                contentDescription = getDayDescriptionForIndex(virtualViewId)
                text = CalendarResources.getDayText(grid.cells[virtualViewId].toInt())
                isSelected =
                    grid.selectionType == SelectionType.CELL && virtualViewId == grid.selectedCell
                isClickable = true

                isEnabled = if (ShortRange.contains(grid.enabledCellRange, virtualViewId)) {
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
                grid.selectCell(virtualViewId, doAnimation = false, isUser = true)

                true
            } else false
        }

        private fun getDayDescriptionForIndex(index: Int): CharSequence {
            val currentMonthStart = ShortRange.getStart(grid.inMonthRange)
            val currentMonthEnd = ShortRange.getEnd(grid.inMonthRange)

            val ym = grid.ym
            var year = ym.year
            var month = ym.month

            val day = grid.cells[index].toInt()
            if (index < currentMonthStart) {
                month--
                if (month == 0) {
                    year--
                    month = 12
                }
            } else if (index > currentMonthEnd) {
                month++
                if (month == 13) {
                    year++
                    month = 1
                }
            }

            tempCalendar.set(year, month - 1, day)

            return DateFormat.format(DATE_FORMAT, tempCalendar)
        }

        companion object {
            private const val DATE_FORMAT = "dd MMMM yyyy"

            private val INDCIES = ArrayList<Int>(42).also {
                for (i in 0..41) {
                    it.add(i)
                }
            }
        }
    }

    private class PressTimeoutHandler(
        gridView: RangeCalendarGridView
    ) : Handler(Looper.getMainLooper()) {
        private val ref = WeakReference(gridView)

        override fun handleMessage(msg: Message) {
            val obj = ref.get() ?: return

            when (msg.what) {
                MSG_LONG_PRESS -> obj.onCellLongPress(msg.arg1)
                MSG_HOVER_PRESS -> obj.setHoverIndex(msg.arg1)
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
    private var lastTouchCell = -1

    var onSelectionListener: OnSelectionListener? = null

    private var inMonthRange = 0
    private var enabledCellRange = ALL_SELECTED
    private var todayIndex = -1
    private var hoverIndex = -1
    private var animationHoverIndex = -1

    var ym = YearMonth(0)

    private var prevSelectionType = 0
    private var selectionType = 0
    private var prevSelectedCell = 0

    var selectedCell = 0
    private var prevSelectedRange = 0
    private var selectedRange = 0
    private var selectedWeekIndex = 0

    private var customRangePath: Path? = null
    private var customPathRange = 0
    private var customRangeStartCell = 0
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

    fun setInMonthRange(range: Int) {
        inMonthRange = range
        reselect()
    }

    fun setEnabledCellRange(range: Int) {
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

    fun setTodayIndex(index: Int) {
        todayIndex = index
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
        val savedSelectedCell = selectedCell
        val savedWeekIndex = selectedWeekIndex
        val savedRange = selectedRange

        clearSelection(fireEvent = false, doAnimation = true)

        when (savedSelectionType) {
            SelectionType.CELL -> selectCell(savedSelectedCell, doAnimation = true)
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
                    val index = getGridIndexByPointOnScreen(x, y)
                    if (ShortRange.contains(enabledCellRange, index)) {
                        val eTime = e.eventTime

                        val longPressTime = eTime + ViewConfiguration.getLongPressTimeout()
                        val hoverTime = eTime + HOVER_DELAY

                        val msg1 = Message.obtain().apply {
                            what = MSG_LONG_PRESS
                            arg1 = index
                        }
                        val msg2 = Message.obtain().apply {
                            what = MSG_HOVER_PRESS
                            arg1 = index
                        }

                        pressTimeoutHandler.sendMessageAtTime(msg1, longPressTime)
                        pressTimeoutHandler.sendMessageAtTime(msg2, hoverTime)
                    }

                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    performClick()
                    if (!isSelectingCustomRange && isXInActiveZone(x)) {
                        val index = getGridIndexByPointOnScreen(x, y)
                        val touchTime = e.downTime
                        if (ShortRange.contains(enabledCellRange, index)) {
                            if (lastTouchTime > 0 && touchTime - lastTouchTime < DOUBLE_TOUCH_MAX_MILLIS && lastTouchCell == index) {
                                selectWeek(index / 7, doAnimation = true)
                            } else {
                                selectCell(index, doAnimation = true, isUser = true)
                                sendClickEventToAccessibility(index)

                                lastTouchCell = index
                                lastTouchTime = touchTime
                            }
                        }
                    }

                    pressTimeoutHandler.removeCallbacksAndMessages(null)

                    hoverIndex = -1
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

                    val index = getGridIndexByPointOnScreen(x, y)

                    val newRange = if (index > customRangeStartCell) {
                        ShortRange.create(customRangeStartCell, index)
                    } else {
                        ShortRange.create(index, customRangeStartCell)
                    }

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
        customRangeStartCell = -1
        isSelectingCustomRange = false
    }

    private fun onCellLongPress(cell: Int) {
        customRangeStartCell = cell
        isSelectingCustomRange = true

        selectCustom(ShortRange.create(cell, cell), true)
    }

    override fun getFocusedRect(r: Rect) {
        val size = cellSize.toInt()
        when (selectionType) {
            SelectionType.CELL -> {
                val left = getCellLeft(selectedCell).toInt()
                val top = getCellTop(selectedCell).toInt()

                r.set(left, top, left + size, top + size)
            }
            SelectionType.WEEK -> {
                val startIndex = ShortRange.getStart(selectedRange)
                val left = getCellLeft(startIndex).toInt()
                val right = getCellRight(ShortRange.getEnd(selectedRange)).toInt()
                val top = getCellTop(startIndex).toInt()

                r.set(left, top, right, top + size)
            }

            else -> super.getFocusedRect(r)
        }
    }

    private fun sendClickEventToAccessibility(index: Int) {
        touchHelper.sendEventForVirtualView(index, AccessibilityEvent.TYPE_VIEW_CLICKED)
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
            SelectionType.CELL -> selectCell(data, doAnimation)
            SelectionType.WEEK -> selectWeek(data, doAnimation)
            SelectionType.MONTH -> selectMonth(doAnimation)
            SelectionType.CUSTOM -> selectCustom(data, false)
        }
    }

    private fun selectCell(index: Int, doAnimation: Boolean, isUser: Boolean = false) {
        if (selectionType == SelectionType.CELL && selectedCell == index) {
            if (isUser && clickOnCellSelectionBehavior == ClickOnCellSelectionBehavior.CLEAR) {
                clearSelection(fireEvent = true, doAnimation = true)
            }

            return
        }

        if (!ShortRange.contains(enabledCellRange, index)) {
            return
        }

        val listener = onSelectionListener
        if (listener != null) {
            val allowed = listener.onCellSelected(index)
            if (!allowed) {
                return
            }
        }

        if (hoverIndex >= 0) {
            clearHoverIndex()
        }

        prevSelectionType = selectionType
        prevSelectedCell = selectedCell
        selectionType = SelectionType.CELL
        selectedCell = index

        if (doAnimation) {
            var animType = CELL_ALPHA_ANIMATION

            when (prevSelectionType) {
                SelectionType.CELL -> {
                    val gridY = index / 7
                    val prevGridY = prevSelectedCell / 7

                    animType = if (prevGridY == gridY) {
                        MOVE_CELL_ON_ROW_ANIMATION
                    } else {
                        if (index % 7 == prevSelectedCell % 7) {
                            MOVE_CELL_ON_COLUMN_ANIMATION
                        } else {
                            DUAL_CELL_ALPHA_ANIMATION
                        }
                    }
                }
                SelectionType.WEEK -> {
                    val weekStart = ShortRange.getStart(selectedRange)
                    val weekEnd = ShortRange.getEnd(selectedRange)

                    animType = if (index in weekStart..weekEnd) {
                        CELL_TO_WEEK_ON_ROW_ANIMATION or ANIMATION_REVERSE_BIT
                    } else {
                        WEEK_TO_CELL_ANIMATION
                    }
                }
                SelectionType.MONTH -> {
                    animType = CELL_TO_MONTH_ANIMATION or ANIMATION_REVERSE_BIT
                }
            }

            startAnimation(animType)
        } else {
            invalidate()
        }
    }

    private fun selectWeek(weekIndex: Int, doAnimation: Boolean) {
        var start = weekIndex * 7
        var end = start + 6

        val weekRange = ShortRange.findIntersection(
            ShortRange.create(start, end),
            enabledCellRange,
            INVALID_RANGE
        )

        if (weekRange == INVALID_RANGE) {
            return
        }

        start = ShortRange.getStart(weekRange)
        end = ShortRange.getEnd(weekRange)

        if (selectionType == SelectionType.WEEK && selectedRange == weekRange) {
            return
        } else if (end == start) {
            selectCell(start, doAnimation)
            return
        }

        val listener = onSelectionListener
        if (listener != null) {
            val allowed = listener.onWeekSelected(weekIndex, start, end)
            if (!allowed) {
                return
            }
        }

        prevSelectionType = selectionType
        selectionType = SelectionType.WEEK
        selectedWeekIndex = weekIndex
        prevSelectedRange = selectedRange
        selectedRange = ShortRange.create(start, end)

        if (doAnimation) {
            if (prevSelectionType == SelectionType.CELL && selectedCell in start..end) {
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
        selectedRange = ShortRange.findIntersection(inMonthRange, enabledCellRange, INVALID_RANGE)

        if (selectedRange == INVALID_RANGE) {
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

    fun selectCustom(range: Int, startSelecting: Boolean) {
        val listener = onSelectionListener
        if (listener != null) {
            val allowed =
                listener.onCustomRangeSelected(ShortRange.getStart(range), ShortRange.getEnd(range))
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
        selectedRange = ShortRange.findIntersection(range, enabledCellRange, INVALID_RANGE)

        if (selectedRange == INVALID_RANGE) {
            customRangePath?.rewind()
        } else if (prevSelectedRange == selectedRange) {
            return
        }

        invalidate()
    }

    private fun setHoverIndex(index: Int) {
        if (selectionType == SelectionType.CELL && selectedCell == index || hoverIndex == index) {
            return
        }

        animationHoverIndex = index
        hoverIndex = index
        startAnimation(HOVER_ANIMATION)
    }

    fun clearHoverIndex() {
        if (hoverIndex >= 0) {
            hoverIndex = -1
            startAnimation(HOVER_ANIMATION or ANIMATION_REVERSE_BIT)
        }
    }

    fun clearSelection(fireEvent: Boolean, doAnimation: Boolean) {
        if (selectionType == SelectionType.NONE) {
            return
        }

        prevSelectionType = selectionType
        prevSelectedCell = selectedCell
        prevSelectedRange = selectedRange
        selectionType = SelectionType.NONE
        selectedCell = -1
        selectedWeekIndex = -1
        selectedRange = 0

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
            CELL_TO_WEEK_ON_ROW_ANIMATION -> {
                drawWeekCellSelection(c, animFraction)
            }
            MOVE_CELL_ON_ROW_ANIMATION -> {
                drawCellSelection(c, selectedCell, animFraction, 1f)
            }
            MOVE_CELL_ON_COLUMN_ANIMATION -> {
                drawCellSelection(c, selectedCell, 1f, animFraction)
            }
            DUAL_CELL_ALPHA_ANIMATION -> {
                val alpha = (animFraction * 255f).toInt()

                selectionPaint.alpha = alpha
                drawCellSelection(c, selectedCell, 1f, 1f)

                selectionPaint.alpha = 255 - alpha
                drawCellSelection(c, prevSelectedCell, 1f, 1f)

                selectionPaint.alpha = 255
            }
            CELL_ALPHA_ANIMATION -> {
                val alpha = (animFraction * 255f).toInt()

                selectionPaint.alpha = alpha
                drawCellSelection(c, selectedCell, 1f, 1f)
                selectionPaint.alpha = 255
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
                drawWeekFromCenterSelection(c, 1f, selectedRange)

                selectionPaint.alpha = alpha
                drawCellSelection(c, selectedCell, 1f, 1f)

                selectionPaint.alpha = 255
            }
            MONTH_ALPHA_ANIMATION -> {
                selectionPaint.alpha = (animFraction * 255f).toInt()
                drawCustomRange(c, selectedRange)

                selectionPaint.alpha = 255
            }
            CLEAR_SELECTION_ANIMATION -> {
                selectionPaint.alpha = (animFraction * 255).toInt()
                drawSelectionNoAnimation(c, prevSelectionType, prevSelectedCell, prevSelectedRange)

                selectionPaint.alpha = 255
            }
            CELL_TO_MONTH_ANIMATION -> {
                drawCellMonthSelection(c)
            }
            else -> {
                drawSelectionNoAnimation(c, selectionType, selectedCell, selectedRange)
            }
        }
    }

    private fun drawSelectionNoAnimation(c: Canvas, sType: Int, cell: Int, range: Int) {
        when (sType) {
            SelectionType.CELL -> drawCellSelection(c, cell, 1f, 1f)
            SelectionType.WEEK -> drawWeekFromCenterSelection(c, 1f, range)
            SelectionType.MONTH, SelectionType.CUSTOM -> drawCustomRange(c, range)
        }
    }

    private fun drawCellMonthSelection(c: Canvas) {
        updateCustomRangePath(selectedRange)

        val gridY = selectedCell / 7
        val gridX = selectedCell - gridY * 7

        val cellLeft = getCellLeft(gridX)
        val cellTop = getCellTop(gridY)

        val halfCellSize = cellSize * 0.5f
        val startX = cellLeft + halfCellSize
        val startY = cellTop + halfCellSize

        val finalRadius = getCircleRadiusForMonthAnimation(startX, startY)
        val currentRadius = lerp(
            halfCellSize,
            finalRadius,
            animFraction
        )

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
        weekRange: Int
    ) {
        val selStart = ShortRange.getStart(weekRange)
        val selEnd = ShortRange.getEnd(weekRange)

        val selY = selStart / 7
        val alignedSelX = selY * 7
        val selStartX = selStart - alignedSelX
        val selEndX = selEnd - alignedSelX

        val rectLeft = getCellLeft(selStartX)
        val rectTop = getCellTop(selY)
        val rectRight = getCellRight(selEndX)
        val rectBottom = rectTop + cellSize

        val left = lerp(leftAnchor, rectLeft, fraction)
        val right = lerp(rightAnchor, rectRight, fraction)

        c.drawRoundRectCompat(
            left, rectTop, right, rectBottom,
            rrRadius(), selectionPaint
        )
    }

    private fun drawWeekFromCenterSelection(c: Canvas, fraction: Float, range: Int) {
        val selStart = ShortRange.getStart(range)
        val selEnd = ShortRange.getEnd(range)
        val midX =
            cr.hPadding + (selStart + selEnd - 14 * (selStart / 7) + 1).toFloat() * cellSize * 0.5f

        drawWeekSelection(c, midX, midX, fraction, range)
    }

    private fun drawWeekCellSelection(c: Canvas, fraction: Float) {
        val cellLeft = getCellLeft(selectedCell % 7)
        val cellRight = cellLeft + cellSize

        drawWeekSelection(c, cellLeft, cellRight, fraction, selectedRange)
    }

    private fun drawCustomRange(c: Canvas, range: Int) {
        updateCustomRangePath(range)

        customRangePath?.let { c.drawPath(it, selectionPaint) }
    }

    private fun drawCellSelection(c: Canvas, cell: Int, xFraction: Float, yFraction: Float) {
        val prevGridY = prevSelectedCell / 7
        val prevGridX = prevSelectedCell - prevGridY * 7
        val gridY = cell / 7
        val gridX = cell - gridY * 7

        val left: Float
        val top: Float
        val endLeft = getCellLeft(gridX)
        val endTop = getCellTop(gridY)

        left = if (xFraction != 1f) {
            lerp(getCellLeft(prevGridX), endLeft, xFraction)
        } else {
            endLeft
        }

        top = if (yFraction != 1f) {
            lerp(getCellTop(prevGridY), endTop, yFraction)
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
        if ((isHoverAnimation && animationHoverIndex >= 0) || hoverIndex >= 0) {
            val index = if (isHoverAnimation) animationHoverIndex else hoverIndex

            val paint = if (isSelectionRangeContainsIndex(index))
                cellHoverOnSelectionPaint
            else
                cellHoverPaint

            val paintAlpha = paint.alpha
            val resultAlpha = if (isHoverAnimation)
                (paintAlpha * animFraction).toInt()
            else
                paintAlpha

            val gridY = index / 7
            val gridX = index % 7

            val left = getCellLeft(gridX)
            val top = getCellTop(gridY)

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
            val gridY = i / 7
            val gridX = i % 7

            val x = getCellLeft(gridX)
            val y = getCellTop(gridY)

            drawCell(c, x, y, cells[i].toInt(), resolveCellType(i))
        }
    }

    private fun resolveCellType(i: Int): Int {
        var cellType = if (selectionType == SelectionType.CELL && selectedCell == i) {
            CELL_SELECTED
        } else if (ShortRange.contains(enabledCellRange, i)) {
            if (ShortRange.contains(inMonthRange, i)) {
                if (i == todayIndex) {
                    if (isSelectionRangeContainsIndex(i)) {
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

        if (i == hoverIndex) {
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

    private fun updateCustomRangePath(range: Int) {
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

        val start = ShortRange.getStart(range)
        val end = ShortRange.getEnd(range)

        val startGridY = start / 7
        val startGridX = start % 7
        val endGridY = end / 7
        val endGridX = end % 7

        val radius = rrRadius()

        if (startGridY == endGridY) {
            val left = getCellLeft(startGridX)
            val right = getCellRight(endGridX)
            val top = getCellTop(startGridY)
            val bottom = top + cellSize

            path.addRoundRectCompat(left, top, right, bottom, radius)
        } else {
            val firstCellOnRowX = cr.hPadding + (columnWidth - cellSize) * 0.5f
            val lastCellOnRowRight = cr.hPadding + columnWidth * 6.5f + cellSize * 0.5f

            val startLeft = getCellLeft(startGridX)
            val startTop = getCellTop(startGridY)
            val startBottom = startTop + cellSize
            val endRight = getCellRight(endGridX)
            val endTop = getCellTop(endGridY)
            val endBottom = endTop + cellSize

            val gridYDiff = endGridY - startGridY

            Radii.withRadius(radius) {
                lt()
                rt()
                lb(startGridX != 0)
                rb(gridYDiff == 1 && endGridX != 6)

                path.addRoundRectCompat(
                    startLeft, startTop, lastCellOnRowRight, startBottom, radii()
                )
            }

            Radii.withRadius(radius) {
                rb()
                lb()
                rt(endGridX != 6)
                lt(gridYDiff == 1 && startGridX != 0)

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
                    lt(startGridX != 0)
                    rb(endGridX != 6)

                    tempRect.set(firstCellOnRowX, startBottom, lastCellOnRowRight, endTop)
                    if (startGridX == 0 && endGridX == 6) {
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

        val intersection =
            ShortRange.findIntersection(enabledCellRange, inMonthRange, INVALID_RANGE)
        if (intersection == INVALID_RANGE) {
            return Float.NaN
        }

        val startCell = ShortRange.getStart(intersection)
        val endCell = ShortRange.getEnd(intersection)

        val startCellGridY = startCell / 7
        val startCellGridX = startCell % 7
        val endCellGridY = endCell / 7
        val endCellGridX = endCell % 7

        val startCellLeft = getCellLeft(startCellGridX)
        val startCellTop = getCellTop(startCellGridY)
        val endCellLeft = getCellLeft(endCellGridX)
        val endCellBottom = getCellTop(endCellGridY) + cellSize

        val distToStartCellSq = distanceSq(startCellLeft, startCellTop, x, y)
        val distToEndCellSq = distanceSq(endCellLeft, endCellBottom, x, y)

        var maxDist = max(distToLeftCorner, distToRightCorner)
        maxDist = max(maxDist, distToTopCorner)
        maxDist = max(maxDist, sqrt(max(distToStartCellSq, distToEndCellSq)))

        return maxDist
    }

    private fun distanceSq(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val xDist = x2 - x1
        val yDist = y2 - y1

        return xDist * xDist + yDist * yDist
    }

    private fun forceResetCustomPath() {
        customPathRange = 0
    }

    private fun gridTop(): Float {
        val height = if (weekdayType == WeekdayType.SHORT) {
            cr.shortWeekdayRowHeight
        } else {
            cr.narrowWeekdayRowHeight
        }

        return height + cr.weekdayRowMarginBottom
    }

    private fun getCellLeft(gridX: Int): Float {
        return cr.hPadding + columnWidth * (gridX + 0.5f) - cellSize * 0.5f
    }

    private fun getCellTop(gridY: Int): Float {
        return gridTop() + gridY * cellSize + gridY * cr.yCellMargin
    }

    private fun getCellRight(gridX: Int): Float {
        return getCellLeft(gridX) + cellSize
    }

    private fun isSelectionRangeContainsIndex(index: Int): Boolean {
        val selType = selectionType

        return selType != SelectionType.NONE && selType != SelectionType.CELL &&
                ShortRange.contains(selectedRange, index)
    }

    fun getGridIndexByPointOnScreen(x: Float, y: Float): Int {
        val gridX = ((x - cr.hPadding) / columnWidth).toInt()
        val gridY = ((y - gridTop()) / (cellSize + cr.yCellMargin)).toInt()

        return gridY * 7 + gridX
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

        private val ALL_SELECTED = ShortRange.create(0, 42)
        private val INVALID_RANGE = ShortRange.create(-1, -1)

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
        private const val MOVE_CELL_ON_ROW_ANIMATION = 2
        private const val MOVE_CELL_ON_COLUMN_ANIMATION = 3
        private const val HOVER_ANIMATION = 4
        private const val DUAL_CELL_ALPHA_ANIMATION = 5
        private const val CELL_ALPHA_ANIMATION = 6
        private const val DUAL_WEEK_ANIMATION = 7
        private const val WEEK_TO_CELL_ANIMATION = 8
        private const val CLEAR_SELECTION_ANIMATION = 9
        private const val MONTH_ALPHA_ANIMATION = 10
        private const val CELL_TO_MONTH_ANIMATION = 11
    }
}