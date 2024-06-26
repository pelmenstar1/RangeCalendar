package com.github.pelmenstar1.rangecalendar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.*
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.core.graphics.withTranslation
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityEventCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import com.github.pelmenstar1.rangecalendar.decoration.*
import com.github.pelmenstar1.rangecalendar.gesture.RangeCalendarGestureConfiguration
import com.github.pelmenstar1.rangecalendar.gesture.RangeCalendarGestureDetector
import com.github.pelmenstar1.rangecalendar.gesture.RangeCalendarGestureDetectorFactory
import com.github.pelmenstar1.rangecalendar.gesture.RangeCalendarGestureEventHandler
import com.github.pelmenstar1.rangecalendar.gesture.SelectionByGestureType
import com.github.pelmenstar1.rangecalendar.selection.*
import com.github.pelmenstar1.rangecalendar.utils.VibratorCompat
import com.github.pelmenstar1.rangecalendar.utils.ceilToInt
import com.github.pelmenstar1.rangecalendar.utils.getLazyValue
import com.github.pelmenstar1.rangecalendar.utils.getTextBoundsArray
import com.github.pelmenstar1.rangecalendar.utils.toIntAlpha
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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
        fun accept(range: CellRange): Boolean
    }

    private fun interface TickCallback {
        fun onTick(fraction: Float)
    }

    private class TouchHelper(
        private val grid: RangeCalendarGridView
    ) : ExploreByTouchHelper(grid) {
        private val tempRect = Rect()

        override fun getVirtualViewAt(x: Float, y: Float): Int {
            val cellIndex = grid.getCellByPointOnScreen(x, y, CellMeasureManager.CoordinateRelativity.VIEW)
            if (cellIndex >= 0) {
                return cellIndex
            }

            return INVALID_ID
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            for (i in 0 until GridConstants.CELL_COUNT) {
                virtualViewIds.add(i)
            }
        }

        override fun onPopulateNodeForVirtualView(
            virtualViewId: Int,
            node: AccessibilityNodeInfoCompat
        ) {
            node.apply {
                val cell = Cell(virtualViewId)

                grid.fillCellBounds(cell, tempRect)

                @Suppress("DEPRECATION")
                setBoundsInParent(tempRect)

                contentDescription = getDayDescriptionForIndex(virtualViewId)
                text = CalendarResources.getDayText(grid.cells[virtualViewId].toInt())

                isSelected = grid.currentSelState.contains(cell)
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
                    gestureType = SelectionByGestureType.SINGLE_CELL_ON_CLICK,
                    withAnimation = false,
                    checkGate = true
                )

                true
            } else false
        }

        private fun getDayDescriptionForIndex(index: Int): CharSequence {
            val provider = grid.cellAccessibilityProvider!!

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
    }

    private class CellMeasureManagerImpl(private val view: RangeCalendarGridView) :
        CellMeasureManager {
        override val cellWidth: Float
            get() = view.cellWidth

        override val cellHeight: Float
            get() = view.cellHeight

        override val roundRadius: Float
            get() = view.cellRoundRadius()

        override fun getCellLeft(cellIndex: Int): Float =
            view.getCellLeftRelativeToGrid(Cell(cellIndex))

        override fun getCellTop(cellIndex: Int): Float =
            view.getCellTopRelativeToGrid(Cell(cellIndex))

        override fun getCellDistance(cellIndex: Int): Float =
            view.getCellDistance(Cell(cellIndex))

        override fun getCellAndPointByDistance(distance: Float, outPoint: PointF): Int =
            view.getCellAndPointByCellDistanceRelativeToGrid(distance, outPoint)

        override fun getCellDistanceByPoint(x: Float, y: Float): Float =
            view.getCellDistanceByPoint(x, y)

        override fun getCellAt(x: Float, y: Float, relativity: CellMeasureManager.CoordinateRelativity): Int =
            view.getCellByPointOnScreen(x, y, relativity)

        override fun getRelativeAnchorValue(anchor: Distance.RelativeAnchor): Float =
            view.getRelativeAnchorValue(anchor)
    }

    private class CellPropertiesProviderImpl(
        private val view: RangeCalendarGridView
    ) : RangeCalendarCellPropertiesProvider {
        override fun isSelectableCell(cell: Int) =
            view.isSelectableCell(Cell(cell))
    }

    private class GestureEventHandlerImpl(
        private val view: RangeCalendarGridView
    ) : RangeCalendarGestureEventHandler {
        override fun selectRange(start: Int, end: Int, gestureType: SelectionByGestureType): SelectionAcceptanceStatus {
            return view.selectRange(
                range = CellRange(start, end),
                requestRejectedBehaviour = SelectionRequestRejectedBehaviour.PRESERVE_CURRENT_SELECTION,
                checkGate = true,
                gestureType
            )
        }

        override fun selectMonth(): SelectionAcceptanceStatus {
            return view.selectMonthByGesture()
        }

        override fun disallowParentInterceptEvent() {
            view.parent?.requestDisallowInterceptTouchEvent(true)
        }

        override fun reportStartSelectingRange() {
            view.onStartSelectingRange()
        }

        override fun reportStartHovering(cell: Int) {
            view.setHoverCell(Cell(cell))
        }

        override fun reportStopHovering() {
            view.clearHoverCell()
        }
    }

    val cells = ByteArray(GridConstants.CELL_COUNT)

    private val dayNumberPaint: Paint
    private val weekdayPaint: Paint
    private val selectionPaint: Paint
    private val cellHoverPaint: Paint

    private var inMonthRange = CellRange.Invalid
    private var enabledCellRange = CellRange.All
    private var todayCell = Cell.Undefined
    private var hoverCell = Cell.Undefined

    private var cellWidth: Float = 0f
    private var cellHeight: Float = 0f

    // In case of clearing hover cell with animation, we need to know the previous cell before the clearing.
    private var animatedHoverCell = Cell.Undefined

    var ym = YearMonth(0)

    var onSelectionListener: OnSelectionListener? = null
    var selectionGate: SelectionGate? = null

    private var selectionTransitionHandler: TickCallback? = null
    private var onSelectionTransitionEnd: (() -> Unit)? = null

    private var selectionTransitiveState: SelectionState.Transitive? = null

    private var selectionManager: SelectionManager = DefaultSelectionManager()
    private var selectionRenderer = selectionManager.renderer

    private var prevSelState: SelectionState? = null
    private var currentSelState: SelectionState? = null

    private val selectionRenderOptions = SelectionRenderOptions()

    private var hoverAnimationHandler: TickCallback? = null

    private val cellMeasureManager = CellMeasureManagerImpl(this)
    private val gridInfo = CalendarGridInfo()
    private val cellPropertiesProvider = CellPropertiesProviderImpl(this)
    private val gestureEventHandler = GestureEventHandlerImpl(this)

    private var gestureDetectorFactory: RangeCalendarGestureDetectorFactory? = null
    private var gestureDetector: RangeCalendarGestureDetector? = null
    private var gestureConfig: RangeCalendarGestureConfiguration? = null

    private var animType = 0
    private var animator: ValueAnimator? = null

    private var animationHandler: TickCallback? = null
    private var onAnimationEnd: (() -> Unit)? = null

    private val touchHelper = TouchHelper(this)

    private val weekdayRow: WeekdayRow

    private var isDayNumberMeasurementsDirty = false
    private var dayNumberSizes = cr.defaultDayNumberSizes

    private val vibrator = VibratorCompat(context)

    internal var decorations: DecorGroupedList? = null
    private var decorRegion = PackedIntRange.Undefined

    private val decorVisualStates = CellDataArray<CellDecor.VisualState>()
    private val decorLayoutOptionsArray = CellDataArray<DecorLayoutOptions>()
    private val cellInfo = CellInfo()

    private var decorAnimFractionInterpolator: DecorAnimationFractionInterpolator? = null
    private var decorAnimatedCell = Cell.Undefined
    private var decorAnimatedRange = PackedIntRange(0)
    private var decorAnimationHandler: TickCallback? = null
    private var decorDefaultLayoutOptions: DecorLayoutOptions? = null

    private var showAdjacentMonths = true

    private var cellAccessibilityProvider: RangeCalendarCellAccessibilityInfoProvider? = null

    private val tempRect = RectF()

    init {
        ViewCompat.setAccessibilityDelegate(this, touchHelper)

        background = null

        selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        dayNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        weekdayPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        cellHoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        weekdayRow = WeekdayRow(cr.defaultWeekdayData, weekdayPaint)
    }

    private fun hoverAlpha(): Float = style.getFloat { HOVER_ALPHA }

    private fun clickOnCellSelectionBehavior() =
        ClickOnCellSelectionBehavior.ofOrdinal(style.getInt { CLICK_ON_CELL_SELECTION_BEHAVIOR })

    private fun vibrateOnSelectingRange() = style.getBoolean { VIBRATE_ON_SELECTING_RANGE }

    private fun commonAnimationDuration() = style.getInt { COMMON_ANIMATION_DURATION }
    private fun hoverAnimationDuration() = style.getInt { HOVER_ANIMATION_DURATION }

    private fun commonAnimationInterpolator() =
        style.getObject<TimeInterpolator> { COMMON_ANIMATION_INTERPOLATOR }

    private fun hoverAnimationInterpolator() =
        style.getObject<TimeInterpolator> { HOVER_ANIMATION_INTERPOLATOR }

    private fun isSelectionAnimatedByDefault() =
        style.getBoolean { IS_SELECTION_ANIMATED_BY_DEFAULT }

    private fun isHoverAnimationEnabled() = style.getBoolean { IS_HOVER_ANIMATION_ENABLED }

    private fun updateSelectionStateConfiguration() {
        val manager = selectionManager
        var needsToInvalidate = false

        prevSelState?.let {
            needsToInvalidate = true
            manager.updateConfiguration(it, cellMeasureManager, gridInfo)
        }

        currentSelState?.let {
            needsToInvalidate = true
            manager.updateConfiguration(it, cellMeasureManager, gridInfo)
        }

        if (needsToInvalidate) {
            invalidate()
        }
    }

    fun onAllStylePropertiesPossiblyChanged() {
        onDayNumberTextSizeChanged(style.getFloat { DAY_NUMBER_TEXT_SIZE })
        onWeekdayTextSizeChanged(style.getFloat { WEEKDAY_TEXT_SIZE })
        onWeekdayPropertiesChanged(
            style.getEnum({ WEEKDAY_TYPE }, WeekdayType::ofOrdinal),
            style.getFloat { WEEKDAY_TEXT_SIZE },
            style.getInt { WEEKDAY_TEXT_COLOR },
            style.getObject { WEEKDAY_TYPEFACE },
            style.getObject { WEEKDAYS }
        )

        onCellSizeComponentChanged()
        onCellRoundRadiusChanged(style.getFloat { CELL_ROUND_RADIUS })
        onCellAnimationTypeChanged(style.getEnum({ CELL_ANIMATION_TYPE }, CellAnimationType::ofOrdinal))

        onShowAdjacentMonthsChanged(style.getBoolean { SHOW_ADJACENT_MONTHS })

        onDecorationDefaultLayoutOptionsChanged(style.getObject { DECOR_DEFAULT_LAYOUT_OPTIONS })

        onSelectionFillChanged(style.getObject { SELECTION_FILL })
        onOutMonthSelectionAlphaChanged(style.getFloat { OUT_MONTH_SELECTION_ALPHA })
        onSelectionFillGradientBoundsTypeChanged(
            style.getEnum({ SELECTION_FILL_GRADIENT_BOUNDS_TYPE }, SelectionFillGradientBoundsType::ofOrdinal)
        )

        onSelectionManagerChanged(style.getObject { SELECTION_MANAGER })
        onCellAccessibilityInfoProviderChanged(style.getObject { CELL_ACCESSIBILITY_INFO_PROVIDER })

        onGestureConfigurationChanged(style.getObject { GESTURE_CONFIGURATION })
        onGestureDetectorFactoryChanged(style.getObject { GESTURE_DETECTOR_FACTORY })
        onSelectionBorderChanged(style.getObject { SELECTION_BORDER })
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

                CELL_WIDTH, CELL_HEIGHT -> onCellSizeComponentChanged()
                CELL_ROUND_RADIUS -> onCellRoundRadiusChanged(data.float())
                CELL_ANIMATION_TYPE -> onCellAnimationTypeChanged(data.enum(CellAnimationType::ofOrdinal))

                OUT_MONTH_SELECTION_ALPHA -> onOutMonthSelectionAlphaChanged(data.float())

                SELECTION_FILL_GRADIENT_BOUNDS_TYPE ->
                    onSelectionFillGradientBoundsTypeChanged(data.enum(SelectionFillGradientBoundsType::ofOrdinal))

                WEEKDAY_TEXT_SIZE -> onWeekdayTextSizeChanged(data.float())
                WEEKDAY_TYPE -> onWeekdayTypeChanged(data.enum(WeekdayType::ofOrdinal))
                WEEKDAY_TEXT_COLOR -> onWeekdayTextColorChanged(data.value)

                SHOW_ADJACENT_MONTHS -> onShowAdjacentMonthsChanged(data.boolean())
            }
        }
    }

    private fun onStylePropertyObjectChanged(propIndex: Int) {
        val data = PackedObject(style.getObject(propIndex))

        with(RangeCalendarStyleData.Companion) {
            when (propIndex) {
                DECOR_DEFAULT_LAYOUT_OPTIONS -> onDecorationDefaultLayoutOptionsChanged(data.value())

                SELECTION_FILL -> onSelectionFillChanged(data.value())
                SELECTION_MANAGER -> onSelectionManagerChanged(data.value())

                CELL_ACCESSIBILITY_INFO_PROVIDER -> onCellAccessibilityInfoProviderChanged(data.value())

                WEEKDAY_TYPEFACE -> onWeekdayTypefaceChanged(data.value())
                WEEKDAYS -> onWeekdaysChanged(data.value())

                GESTURE_DETECTOR_FACTORY -> onGestureDetectorFactoryChanged(data.value())
                GESTURE_CONFIGURATION -> onGestureConfigurationChanged(data.value())
                SELECTION_BORDER -> onSelectionBorderChanged(data.value())
            }
        }
    }

    private fun onSelectionManagerChanged(manager: SelectionManager?) {
        val resolvedManager = manager ?: DefaultSelectionManager()

        if (resolvedManager === manager) {
            return
        }

        prevSelState = copySelectionState(resolvedManager, prevSelState)
        currentSelState = copySelectionState(resolvedManager, currentSelState)

        selectionManager = resolvedManager
        selectionRenderer = resolvedManager.renderer

        invalidate()
    }

    private fun copySelectionState(manager: SelectionManager, state: SelectionState?): SelectionState? {
        return state?.let {
            manager.createState(it.rangeStart, it.rangeEnd, cellMeasureManager, gridInfo)
        }
    }

    private fun setNewSelectionState(state: SelectionState?) {
        prevSelState = currentSelState
        currentSelState = state
    }

    private fun onDayNumberTextSizeChanged(newSize: Float) {
        if (dayNumberPaint.textSize != newSize) {
            dayNumberPaint.textSize = newSize

            isDayNumberMeasurementsDirty = true

            refreshAllDecorVisualStates()
            invalidate()
        }
    }

    private fun onWeekdayTextSizeChanged(newSize: Float) {
        if (weekdayPaint.textSize != newSize) {
            weekdayPaint.textSize = newSize
            weekdayRow.onMeasurementsChanged()

            onGridTopChanged()
        }
    }

    private fun onWeekdayTypefaceChanged(typeface: Typeface?) {
        if (weekdayPaint.typeface != typeface) {
            weekdayPaint.typeface = typeface
            weekdayRow.onMeasurementsChanged()

            onGridTopChanged()
        }
    }

    private fun onWeekdaysChanged(weekdays: Array<out String>?) {
        if (!weekdayRow.weekdays.contentEquals(weekdays)) {
            weekdayRow.weekdays = weekdays

            onGridTopChanged()
        }
    }

    private fun onWeekdayTextColorChanged(color: Int) {
        if (weekdayPaint.color != color) {
            weekdayPaint.color = color

            invalidate()
        }
    }

    private fun onWeekdayTypeChanged(type: WeekdayType) {
        if (weekdayRow.type != type) {
            weekdayRow.type = type

            onGridTopChanged()
        }
    }

    private fun onWeekdayPropertiesChanged(
        type: WeekdayType,
        textSize: Float,
        textColor: Int,
        typeface: Typeface?,
        weekdays: Array<out String>?
    ) {
        var isMeasurementsChanged = false

        if (weekdayPaint.textSize != textSize) {
            weekdayPaint.textSize = textSize
            isMeasurementsChanged = true
        }

        if (weekdayPaint.typeface != typeface) {
            weekdayPaint.typeface = typeface
            isMeasurementsChanged = true
        }

        if (!weekdayRow.weekdays.contentEquals(weekdays)) {
            weekdayRow.weekdays = weekdays
            isMeasurementsChanged = true
        }

        if (weekdayRow.type != type) {
            weekdayRow.type = type
            isMeasurementsChanged = true
        }

        if (isMeasurementsChanged) {
            weekdayRow.onMeasurementsChanged()
            onGridTopChanged()
        }

        if (weekdayPaint.color != textColor) {
            weekdayPaint.color = textColor
            invalidate()
        }
    }

    fun onCellSizeComponentChanged() {
        val newCellWidth = style.getFloat { CELL_WIDTH }
        val newCellHeight = style.getFloat { CELL_HEIGHT }

        if (newCellWidth != cellWidth || newCellHeight != cellHeight) {
            cellWidth = newCellWidth
            cellHeight = newCellHeight

            selectionRenderOptions.roundRadius = cellRoundRadius()

            refreshAllDecorVisualStates()
            updateSelectionStateConfiguration()

            // Size depends on cellWidth and cellHeight
            requestLayout()
        }
    }

    private fun onSelectionFillChanged(fill: Fill) {
        if (selectionRenderOptions.getFillOrNull() != fill) {
            selectionRenderOptions.fill = fill
            selectionRenderOptions.fillState = fill.createState()

            onSelectionFillOrGradientBoundsTypeChanged()
        }
    }

    private fun onOutMonthSelectionAlphaChanged(alpha: Float) {
        if (selectionRenderOptions.outMonthAlpha != alpha) {
            selectionRenderOptions.outMonthAlpha = alpha

            invalidate()
        }
    }

    private fun onSelectionFillGradientBoundsTypeChanged(type: SelectionFillGradientBoundsType) {
        if (selectionRenderOptions.getFillGradientBoundsTypeOrNull() != type) {
            selectionRenderOptions.fillGradientBoundsType = type

            onSelectionFillOrGradientBoundsTypeChanged()
        }
    }

    private fun onSelectionFillOrGradientBoundsTypeChanged() {
        updateGradientBoundsIfNeeded()

        invalidate()
    }

    private fun onSelectionBorderChanged(value: Border?) {
        if (selectionRenderOptions.border != value) {
            selectionRenderOptions.border = value

            invalidate()
        }
    }

    private fun onCellAnimationTypeChanged(type: CellAnimationType) {
        if (selectionRenderOptions.getCellAnimationTypeOrNull() != type) {
            selectionRenderOptions.cellAnimationType = type
        }
    }

    fun onCellRoundRadiusChanged(roundRadius: Float) {
        if (selectionRenderOptions.roundRadius != roundRadius) {
            selectionRenderOptions.roundRadius = fixRoundRadius(roundRadius)

            refreshAllDecorVisualStates()

            invalidate()
        }
    }

    private fun onShowAdjacentMonthsChanged(value: Boolean) {
        if (showAdjacentMonths != value) {
            showAdjacentMonths = value

            // showAdjacentMonths directly affects on the selection range, so we need to update it.
            updateSelectionRange()
        }
    }

    private fun onCellAccessibilityInfoProviderChanged(provider: RangeCalendarCellAccessibilityInfoProvider) {
        if (cellAccessibilityProvider != provider) {
            cellAccessibilityProvider = provider

            // Some accessibility-related properties might be changed.
            touchHelper.invalidateRoot()
        }
    }

    private fun onGestureDetectorFactoryChanged(factory: RangeCalendarGestureDetectorFactory) {
        if (gestureDetectorFactory === factory) {
            return
        }

        gestureDetectorFactory = factory
        gestureDetector = factory.create()

        bindGestureDetector()
    }

    private fun onGestureConfigurationChanged(conf: RangeCalendarGestureConfiguration) {
        if (gestureConfig != conf) {
            gestureConfig = conf

            bindGestureDetector()
        }
    }

    private fun bindGestureDetector() {
        gestureDetector?.bind(cellMeasureManager, cellPropertiesProvider, gestureEventHandler, gestureConfig!!)
    }

    fun setInMonthRange(range: CellRange) {
        if (inMonthRange != range) {
            inMonthRange = range
            gridInfo.inMonthRange = range

            updateSelectionRange()
        }
    }

    fun setEnabledCellRange(range: CellRange) {
        val oldRange = enabledCellRange

        if (oldRange != range) {
            enabledCellRange = range

            updateSelectionRange()
            invalidateAccessibilityOutIntersectionRanges(
                oldRange,
                range,
                AccessibilityEventCompat.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION
            )
        }
    }

    fun setTodayCell(cell: Cell) {
        if (todayCell != cell) {
            todayCell = cell

            invalidate()
        }
    }

    fun setFirstDayOfWeek(firstDayOfWeek: CompatDayOfWeek) {
        weekdayRow.firstDayOfWeek = firstDayOfWeek

        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val preferredWidth = ceilToInt(cellWidth * GridConstants.COLUMN_COUNT)
        val preferredHeight = ceilToInt(gridTop() + gridHeight())

        setMeasuredDimension(
            resolveSize(preferredWidth, widthMeasureSpec),
            resolveSize(preferredHeight, heightMeasureSpec)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        updateGradientBoundsIfNeeded()

        // Selection state relies on measurements that might be changed with different width or height.
        // For example, getCellLeft() gives different results with various view's width.
        updateSelectionStateConfiguration()
    }

    private fun updateGradientBoundsIfNeeded() {
        val options = selectionRenderOptions

        if (options.getFillGradientBoundsTypeOrNull() == SelectionFillGradientBoundsType.GRID) {
            options.getFillStateOrNull()?.setSize(rowWidth(), gridHeight())
        }
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        return touchHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return touchHelper.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val detector = gestureDetector ?: throw RuntimeException("gesture detector is null")

        return detector.processEvent(e)
    }

    private fun sendClickEventToAccessibility(cell: Cell) {
        touchHelper.sendEventForVirtualView(cell.index, AccessibilityEvent.TYPE_VIEW_CLICKED)
    }

    private fun invalidateAccessibilityNodesRange(start: Int, endInclusive: Int, changeTypes: Int) {
        val helper = touchHelper

        for (i in start..endInclusive) {
            helper.invalidateVirtualView(i, changeTypes)
        }
    }

    private fun invalidateAccessibilityOutIntersectionRanges(
        oldRange: CellRange,
        newRange: CellRange,
        changeTypes: Int
    ) {
        val oldStart = oldRange.start.index
        val oldEnd = oldRange.end.index
        val newStart = newRange.start.index
        val newEnd = newRange.end.index

        if (oldRange.hasIntersectionWith(newRange)) {
            invalidateAccessibilityNodesRange(
                start = min(oldStart, newStart),
                endInclusive = max(oldStart, newStart) - 1,
                changeTypes
            )

            invalidateAccessibilityNodesRange(
                start = min(oldEnd, newEnd) + 1,
                endInclusive = max(oldEnd, newEnd) - 1,
                changeTypes
            )
        } else {
            invalidateAccessibilityNodesRange(oldStart, oldEnd, changeTypes)
            invalidateAccessibilityNodesRange(newStart, newEnd, changeTypes)
        }
    }

    private fun updateSelectionRange() {
        currentSelState?.let {
            selectRange(
                it.range,
                requestRejectedBehaviour = SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION,
                checkGate = true
            )
        }
    }

    fun onStartSelectingRange() {
        if (vibrateOnSelectingRange()) {
            vibrator.vibrateTick()
        }
    }

    fun select(
        range: CellRange,
        requestRejectedBehaviour: SelectionRequestRejectedBehaviour,
        withAnimation: Boolean,
        fireEvent: Boolean,
        checkGate: Boolean
    ) {
        // Do not check whether the gate accepts the selection as the RangeCalendarPagerAdapter checked it before.
        selectRange(
            range,
            requestRejectedBehaviour,
            checkGate,
            gestureType = null,
            fireEvent,
            withAnimation
        )
    }

    private fun selectRange(
        range: CellRange,
        requestRejectedBehaviour: SelectionRequestRejectedBehaviour,
        checkGate: Boolean,
        gestureType: SelectionByGestureType? = null,
        fireEvent: Boolean = true,
        withAnimation: Boolean = isSelectionAnimatedByDefault(),
    ): SelectionAcceptanceStatus {
        val currentSelRange = currentSelState?.range ?: CellRange.Invalid

        val isSameCellSelection = currentSelRange.isSingleCell && currentSelRange == range

        // Check if user clicks on the same cell and clear selection if necessary.
        if (gestureType == SelectionByGestureType.SINGLE_CELL_ON_CLICK && isSameCellSelection && clickOnCellSelectionBehavior() == ClickOnCellSelectionBehavior.CLEAR) {
            clearSelection(fireEvent = true, withAnimation)

            return SelectionAcceptanceStatus.REJECTED
        }

        if (checkGate && selectionGate?.accept(range) == false) {
            clearSelectionToMatchBehaviour(requestRejectedBehaviour, withAnimation)

            return SelectionAcceptanceStatus.REJECTED
        }

        // Clear hover here and not in onCellLongPress() because custom range might be disallowed and
        // hover will be cleared but it shouldn't.
        clearHoverCell()

        var intersection = range.intersectionWith(enabledCellRange)
        if (!showAdjacentMonths) {
            intersection = intersection.intersectionWith(inMonthRange)
        }

        if (intersection == CellRange.Invalid) {
            clearSelectionToMatchBehaviour(requestRejectedBehaviour, withAnimation)

            return SelectionAcceptanceStatus.REJECTED
        } else if (currentSelRange == intersection) {
            return SelectionAcceptanceStatus.ACCEPTED_SAME_RANGE
        }

        val newState = selectionManager.createState(intersection, cellMeasureManager, gridInfo)
        setNewSelectionState(newState)

        if (fireEvent) {
            onSelectionListener?.onSelection(intersection)
        }

        invalidateAccessibilityOutIntersectionRanges(
            currentSelRange,
            intersection,
            AccessibilityEventCompat.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION
        )

        if (withAnimation) {
            startSelectionTransition()
        } else {
            invalidate()
        }

        return SelectionAcceptanceStatus.ACCEPTED
    }

    fun selectMonthByGesture(): SelectionAcceptanceStatus {
        return selectRange(
            range = inMonthRange,
            requestRejectedBehaviour = SelectionRequestRejectedBehaviour.PRESERVE_CURRENT_SELECTION,
            checkGate = true,
            gestureType = SelectionByGestureType.OTHER
        )
    }

    private fun clearSelectionToMatchBehaviour(
        value: SelectionRequestRejectedBehaviour,
        withAnimation: Boolean
    ) {
        if (value == SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION) {
            clearSelection(fireEvent = true, withAnimation)
        }
    }

    private fun createSelectionTransitionHandler(): TickCallback {
        return TickCallback { fraction ->
            selectionTransitiveState?.let { state ->
                selectionManager.transitionController.handleTransition(
                    state,
                    cellMeasureManager,
                    fraction
                )
            }
        }
    }

    private fun getSelectionTransitionHandler(): TickCallback {
        return getLazyValue(
            selectionTransitionHandler,
            ::createSelectionTransitionHandler
        ) { selectionTransitionHandler = it }
    }

    private fun getSelectionOnEndHandler(): () -> Unit {
        return getLazyValue(
            onSelectionTransitionEnd,
            { { selectionTransitiveState = null } },
            { onSelectionTransitionEnd = it }
        )
    }

    private fun startSelectionTransition() {
        val handler = getSelectionTransitionHandler()
        val onEnd = getSelectionOnEndHandler()

        val selManager = selectionManager
        val measureManager = cellMeasureManager

        val isSelectionAnimRunning = animType == SELECTION_ANIMATION

        val prevSelState = prevSelState
        val currentSelState = currentSelState
        val prevTransitiveState = selectionTransitiveState

        var newTransitiveState: SelectionState.Transitive? = null

        if (isSelectionAnimRunning && prevTransitiveState != null) {
            newTransitiveState = selManager.joinTransition(prevTransitiveState, currentSelState, measureManager)
        }

        if (newTransitiveState == null) {
            newTransitiveState = selManager.createTransition(
                prevSelState, currentSelState,
                measureManager,
                selectionRenderOptions
            )
        }

        if (newTransitiveState == null) {
            // We can't create simple transition between states. We have nothing to do except calling invalidate()
            // to redraw.
            invalidate()

            return
        }

        // Cancel animation instead of ending it because ending the animation causes end value of the animation to be assigned
        // but we don't need that because old selectionTransitiveState should not be mutated after joining transitions
        //
        // Call cancelCalendarAnimation() before changing selectionTransitiveState. Otherwise, it'd mutate newTransitiveState
        // which is undesired.
        cancelCalendarAnimation()

        selectionTransitiveState = newTransitiveState

        startCalendarAnimation(
            SELECTION_ANIMATION,
            isReversed = false,
            handler, onEnd,
            endPrevAnimation = false
        )
    }

    private fun setHoverCell(cell: Cell) {
        if (currentSelState.isSingleCell(cell) || hoverCell == cell) {
            return
        }

        animatedHoverCell = cell
        hoverCell = cell

        if (isHoverAnimationEnabled()) {
            startHoverAnimation(isReversed = false)
        } else {
            invalidate()
        }
    }

    fun clearHoverCell() {
        if (hoverCell.isDefined) {
            hoverCell = Cell.Undefined

            if (isHoverAnimationEnabled()) {
                startHoverAnimation(isReversed = true)
            } else {
                invalidate()
            }
        }
    }

    private fun handleHoverAnimation(fraction: Float) {
        cellHoverPaint.alpha = (hoverAlpha() * fraction).toIntAlpha()
    }

    private fun startHoverAnimation(isReversed: Boolean) {
        val handler = getLazyValue(
            hoverAnimationHandler,
            { TickCallback { handleHoverAnimation(it) } },
        ) { hoverAnimationHandler = it }

        startCalendarAnimation(HOVER_ANIMATION, isReversed, handler)
    }

    fun clearSelection(fireEvent: Boolean, withAnimation: Boolean) {
        // No sense to clear selection if there's none.
        if (currentSelState == null) {
            return
        }

        setNewSelectionState(null)

        // Fire event if it's demanded
        if (fireEvent) {
            onSelectionListener?.onSelectionCleared()
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
    fun onDecorationDefaultLayoutOptionsChanged(options: DecorLayoutOptions?) {
        if (decorDefaultLayoutOptions != options) {
            decorDefaultLayoutOptions = options

            decorVisualStates.forEachNotNull { cell, value ->
                val endState = createDecorVisualState(value.visual().stateHandler(), cell)

                decorVisualStates[cell] = endState
            }

            invalidate()
        }
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
                isReversed = false,
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
                isReversed = false,
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
                isReversed = false,
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

    private fun cancelCalendarAnimation() {
        animator?.let {
            if (it.isRunning) {
                it.cancel()
            }
        }
    }

    // It could be startAnimation(), but this name would interfere with View's startAnimation(Animation)
    private fun startCalendarAnimation(
        type: Int,
        isReversed: Boolean,
        handler: TickCallback?,
        onEnd: (() -> Unit)? = null,
        endPrevAnimation: Boolean = true
    ) {
        var animator = animator

        if (endPrevAnimation) {
            endCalendarAnimation()
        }

        animType = type
        onAnimationEnd = onEnd
        animationHandler = handler

        if (animator == null) {
            animator = AnimationHelper.createFractionAnimator { fraction ->
                animationHandler?.onTick(fraction)

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

        if (type == HOVER_ANIMATION) {
            animator.duration = hoverAnimationDuration().toLong()
            animator.interpolator = hoverAnimationInterpolator()
        } else {
            animator.duration = commonAnimationDuration().toLong()
            animator.interpolator = commonAnimationInterpolator()
        }

        if (isReversed) {
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

    private fun drawSelection(canvas: Canvas) {
        val renderer = selectionRenderer

        if (animType == SELECTION_ANIMATION) {
            selectionTransitiveState?.let {
                canvas.withTranslation(x = cr.hPadding, y = gridTop()) {
                    renderer.drawTransition(canvas, it, selectionRenderOptions)
                }
            }
        } else {
            currentSelState?.let { state ->
                canvas.withTranslation(x = cr.hPadding, y = gridTop()) {
                    renderer.draw(canvas, state, selectionRenderOptions)
                }
            }
        }
    }

    private fun drawWeekdayRow(c: Canvas) {
        weekdayRow.draw(c, cr.hPadding, columnWidth())
    }

    private fun drawHover(c: Canvas) {
        val isHoverAnimation = animType == HOVER_ANIMATION

        if (hoverCell.isDefined || isHoverAnimation) {
            // If this is hover-animation, we need to use animatedHoverCell instead of hoverCell in case the animation
            // is triggered because of clearing the hover.
            val cell = if (isHoverAnimation) animatedHoverCell else hoverCell

            val halfCellWidth = cellWidth * 0.5f
            val cellHeight = cellHeight

            val centerX = getCellCenterX(cell)
            val left = centerX - halfCellWidth
            val right = centerX + halfCellWidth

            val top = getCellTop(cell)
            val bottom = top + cellHeight
            val radius = selectionRenderOptions.roundRadius

            c.drawRoundRect(
                left, top, right, bottom,
                radius, radius,
                cellHoverPaint
            )
        }
    }

    private fun drawCells(c: Canvas) {
        measureDayNumberTextSizesIfNecessary()

        val startIndex: Int
        val endIndex: Int

        if (showAdjacentMonths) {
            startIndex = 0
            endIndex = GridConstants.CELL_COUNT - 1
        } else {
            val (start, end) = inMonthRange

            startIndex = start.index
            endIndex = end.index
        }

        val columnWidth = columnWidth()
        val cellHeight = cellHeight
        val halfCellHeight = cellHeight * 0.5f

        val transitiveSelState = selectionTransitiveState
        val currentSelRange = currentSelState?.range ?: CellRange.Invalid

        val rect = tempRect

        for (i in startIndex..endIndex) {
            val cell = Cell(i)
            val day = cells[i].toInt()

            if (day > 0) {
                val centerX = getCellCenterX(cell, columnWidth)
                val centerY = getCellTop(cell) + halfCellHeight

                val (textWidth, textHeight) = getDayNumberSize(day)
                val halfTextWidth = textWidth * 0.5f
                val halfTextHeight = textHeight * 0.5f

                val textX = centerX - halfTextWidth
                val textY = centerY + halfTextHeight

                val isSelectionOverlaysCellText = if (transitiveSelState != null) {
                    rect.set(
                        textX, centerY - halfTextHeight,
                        centerX + halfTextWidth, textY
                    )

                    // Coordinates in selection are relative to the grid. Translate the rect.
                    rect.offset(-cr.hPadding, -gridTop())

                    transitiveSelState.overlaysRect(rect)
                } else {
                    cell in currentSelRange
                }

                val cellType = when {
                    isSelectionOverlaysCellText -> CELL_SELECTED
                    enabledCellRange.contains(cell) -> {
                        if (inMonthRange.contains(cell)) {
                            if (cell == todayCell) CELL_TODAY else CELL_IN_MONTH
                        } else {
                            CELL_OUT_MONTH
                        }
                    }

                    else -> CELL_DISABLED
                }

                drawCell(c, textX, textY, day, cellType)
            }
        }
    }

    private fun drawCell(c: Canvas, textX: Float, textY: Float, day: Int, cellType: Int) {
        val propIndex = when (cellType) {
            CELL_SELECTED, CELL_IN_MONTH -> RangeCalendarStyleData.IN_MONTH_TEXT_COLOR
            CELL_OUT_MONTH -> RangeCalendarStyleData.OUT_MONTH_TEXT_COLOR
            CELL_TODAY -> RangeCalendarStyleData.TODAY_TEXT_COLOR
            CELL_DISABLED -> RangeCalendarStyleData.DISABLED_TEXT_COLOR

            else -> throw IllegalArgumentException("type")
        }

        dayNumberPaint.color = style.getInt(propIndex)

        c.drawText(CalendarResources.getDayText(day), textX, textY, dayNumberPaint)
    }

    private fun measureDayNumberTextSizesIfNecessary() {
        if (isDayNumberMeasurementsDirty) {
            isDayNumberMeasurementsDirty = false

            var sizes = dayNumberSizes
            if (sizes.array === cr.defaultDayNumberSizes.array) {
                sizes = PackedSizeArray(31)
                dayNumberSizes = sizes
            }

            dayNumberPaint.getTextBoundsArray(CalendarResources.DAYS) { i, width, height ->
                sizes[i] = PackedSize(width, height)
            }
        }
    }

    private fun getDecorAnimationHandler(): TickCallback {
        return getLazyValue(
            decorAnimationHandler,
            { TickCallback { handleDecorationAnimation(it) } },
            { decorAnimationHandler = it }
        )
    }

    private fun handleDecorationAnimation(fraction: Float) {
        if (animType == DECOR_ANIMATION) {
            val state = decorVisualStates[decorAnimatedCell]

            if (state is CellDecor.VisualState.Transitive) {
                state.handleAnimation(fraction, decorAnimFractionInterpolator!!)
            }
        }
    }

    private fun drawDecorations(c: Canvas) {
        val columnWidth = columnWidth()

        decorVisualStates.forEachNotNull { cell, state ->
            val dx = getCellLeft(cell, columnWidth)
            val dy = getCellTop(cell)

            c.translate(dx, dy)
            state.visual().renderer().renderState(c, state)
            c.translate(-dx, -dy)
        }
    }

    private fun onGridTopChanged() {
        // Gradient bounds if gradient bounds type is GRID, depends on gridTop()
        updateGradientBoundsIfNeeded()

        // Height of the view depends on gridTop()
        requestLayout()

        // Y coordinates of cells depend on gridTop(), so we need to refresh accessibility info
        touchHelper.invalidateRoot()
    }

    private fun gridTop(): Float {
        return weekdayRow.height + cr.weekdayRowMarginBottom
    }

    private fun gridHeight(): Float {
        return cellHeight * GridConstants.ROW_COUNT
    }

    private fun cellRoundRadius(): Float {
        return fixRoundRadius(style.getFloat { CELL_ROUND_RADIUS })
    }

    private fun fixRoundRadius(value: Float): Float {
        return min(value, min(cellWidth, cellHeight) * 0.5f)
    }

    // Width of the view without horizontal paddings (left and right)
    private fun rowWidth(): Float {
        return width - cr.hPadding * 2f
    }

    private fun columnWidth(): Float {
        return rowWidth() * (1f / GridConstants.COLUMN_COUNT)
    }

    private fun getCellCenterX(cell: Cell) = getCellCenterX(cell, columnWidth())

    private fun getCellCenterX(cell: Cell, columnWidth: Float): Float {
        return cr.hPadding + columnWidth * (cell.gridX + 0.5f)
    }

    private fun getCellLeft(cell: Cell) = getCellLeft(cell, columnWidth())

    private fun getCellLeft(cell: Cell, columnWidth: Float): Float {
        return getCellCenterX(cell, columnWidth) - cellWidth * 0.5f
    }

    private fun getCellRight(cell: Cell, columnWidth: Float): Float {
        return getCellCenterX(cell, columnWidth) + cellWidth * 0.5f
    }

    private fun getCellTop(cell: Cell): Float {
        return gridTop() + cell.gridY * cellHeight
    }

    private fun getCellLeftRelativeToGrid(cell: Cell): Float {
        return columnWidth() * (cell.gridX + 0.5f) - cellWidth * 0.5f
    }

    private fun getCellTopRelativeToGridByGridY(gridY: Int): Float {
        return cellHeight * gridY
    }

    private fun getCellTopRelativeToGrid(cell: Cell): Float {
        return getCellTopRelativeToGridByGridY(cell.gridY)
    }

    private fun fillCellBounds(cell: Cell, bounds: Rect) {
        val halfCw = cellWidth * 0.5f

        val centerX = getCellCenterX(cell)

        val left = centerX - halfCw
        val right = centerX + halfCw

        val top = getCellTop(cell)
        val bottom = top + cellHeight

        bounds.set(left.toInt(), top.toInt(), ceilToInt(right), ceilToInt(bottom))
    }

    private fun fillRangeOnRowBounds(start: Cell, end: Cell, bounds: Rect) {
        val columnWidth = columnWidth()

        val left = getCellLeft(start, columnWidth)
        val top = getCellTop(start)
        val right = getCellRight(end, columnWidth)
        val bottom = top + cellHeight

        bounds.set(left.toInt(), top.toInt(), ceilToInt(right), ceilToInt(bottom))
    }

    private fun getCellDistance(cell: Cell): Float {
        val rw = rowWidth()

        // Find a x-axis of the cell but without horizontal padding.
        // Also merge rw * cell.gridY to the rw * ((1f / 7f) * (cell.gridX + 0.5f))
        return rw * ((1f / GridConstants.COLUMN_COUNT) * (cell.gridX + 0.5f) + cell.gridY) - cellWidth * 0.5f
    }

    private fun getCellDistanceByPoint(x: Float, y: Float): Float {
        return (y / cellHeight) * rowWidth() + x
    }

    private fun getCellAndPointByCellDistanceRelativeToGrid(distance: Float, outPoint: PointF): Int {
        val rw = rowWidth()

        val fGridY = distance / rw
        val gridY = fGridY.toInt()
        val cellTop = getCellTopRelativeToGridByGridY(gridY)

        val xOnRow = distance - gridY * rw

        val gridX = (GridConstants.COLUMN_COUNT * (fGridY - gridY)).toInt()

        outPoint.x = xOnRow
        outPoint.y = cellTop

        return Cell(gridX, gridY).index
    }

    private fun getCellByPointOnScreen(x: Float, y: Float, relativity: CellMeasureManager.CoordinateRelativity): Int {
        var translatedX = x
        var translatedY = y

        val hPadding = cr.hPadding
        val gridTop = gridTop()
        val cellHeight = cellHeight

        val rowWidth = rowWidth()
        val gridHeight = cellHeight * GridConstants.ROW_COUNT

        if (relativity == CellMeasureManager.CoordinateRelativity.VIEW) {
            // Translate to grid's coordinates
            translatedX -= hPadding
            translatedY -= gridTop
        }

        if (translatedX !in 0f..rowWidth || translatedY !in 0f..gridHeight) {
            return -1
        }

        val gridX = ((GridConstants.COLUMN_COUNT * translatedX) / rowWidth).toInt()
        val gridY = (translatedY / cellHeight).toInt()

        return Cell(gridX, gridY).index
    }

    private fun getRelativeAnchorValue(anchor: Distance.RelativeAnchor): Float {
        return when (anchor) {
            Distance.RelativeAnchor.WIDTH -> rowWidth()
            Distance.RelativeAnchor.HEIGHT -> gridHeight()
            Distance.RelativeAnchor.MIN_DIMENSION -> min(rowWidth(), gridHeight())
            Distance.RelativeAnchor.MAX_DIMENSION -> max(rowWidth(), gridHeight())
            Distance.RelativeAnchor.DIAGONAL -> {
                val w = rowWidth()
                val h = gridHeight()

                sqrt(w * w + h * h)
            }
        }
    }

    private fun isSelectableCell(cell: Cell): Boolean {
        return enabledCellRange.contains(cell) && (showAdjacentMonths || inMonthRange.contains(cell))
    }

    private fun getDayNumberSize(day: Int) = dayNumberSizes[day - 1]
    private fun getDayNumberSize(cell: Cell) = getDayNumberSize(cells[cell.index].toInt())

    companion object {
        private const val TAG = "RangeCalendarGridView"

        private const val CELL_IN_MONTH = 0
        private const val CELL_OUT_MONTH = 1
        private const val CELL_SELECTED = 2
        private const val CELL_DISABLED = 3
        private const val CELL_TODAY = 4

        private const val NO_ANIMATION = 0
        private const val SELECTION_ANIMATION = 1
        private const val HOVER_ANIMATION = 2
        private const val DECOR_ANIMATION = 3
    }
}