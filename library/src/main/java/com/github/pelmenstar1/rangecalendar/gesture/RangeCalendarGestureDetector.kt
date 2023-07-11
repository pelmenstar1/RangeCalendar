package com.github.pelmenstar1.rangecalendar.gesture

import android.view.MotionEvent
import com.github.pelmenstar1.rangecalendar.CellMeasureManager
import com.github.pelmenstar1.rangecalendar.RangeCalendarCellPropertiesProvider

/**
 * Responsible for detecting various gestures using supplied motion events and reporting about them.
 */
abstract class RangeCalendarGestureDetector {
    private var _measureManager: CellMeasureManager? = null
    private var _cellPropertiesProvider: RangeCalendarCellPropertiesProvider? = null
    private var _gestureEventHandler: RangeCalendarGestureEventHandler? = null
    private var _configuration: RangeCalendarGestureConfiguration? = null

    val measureManager: CellMeasureManager
        get() = _measureManager ?: throwBindNotCalled()

    val cellPropertiesProvider: RangeCalendarCellPropertiesProvider
        get() = _cellPropertiesProvider ?: throwBindNotCalled()

    val gestureEventHandler: RangeCalendarGestureEventHandler
        get() = _gestureEventHandler ?: throwBindNotCalled()

    val configuration: RangeCalendarGestureConfiguration
        get() = _configuration ?: throwBindNotCalled()

    private fun throwBindNotCalled(): Nothing {
        throw RuntimeException("bind() should be called before accessing the property")
    }

    /**
     * Binds [CellMeasureManager], [RangeCalendarCellPropertiesProvider], [RangeCalendarGestureEventHandler], [RangeCalendarGestureConfiguration] to the detector.
     *
     * This method is not expected to be called outside the library.
     */
    fun bind(
        measureManager: CellMeasureManager,
        cellPropertiesProvider: RangeCalendarCellPropertiesProvider,
        gestureEventHandler: RangeCalendarGestureEventHandler,
        configuration: RangeCalendarGestureConfiguration
    ) {
        _measureManager = measureManager
        _cellPropertiesProvider = cellPropertiesProvider
        _gestureEventHandler = gestureEventHandler
        _configuration = configuration
    }

    /**
     * Processes specified motion event
     */
    abstract fun processEvent(event: MotionEvent): Boolean

    /**
     * Shortcut for `gestureEventHandler.selectRange(start, end, gestureType)`
     */
    protected fun selectRange(start: Int, end: Int, gestureType: SelectionByGestureType) =
        gestureEventHandler.selectRange(start, end, gestureType)

    /**
     * Shortcut for `gestureEventHandler.selectMonth()`
     */
    protected fun selectMonth() =
        gestureEventHandler.selectMonth()

    /**
     * Shortcut for `cellPropertiesProvider.isSelectableCell(cell)`
     */
    protected fun isSelectableCell(cell: Int): Boolean =
        cellPropertiesProvider.isSelectableCell(cell)

    /**
     * Shortcut for `measureManager.getCellAt(x, y, CellMeasureManager.Coordinate.VIEW)`.
     *
     * The relativity is [CellMeasureManager.CoordinateRelativity.VIEW] by default as the coordinates in [MotionEvent]s are relative to the view.
     */
    protected fun getCellAt(x: Float, y: Float): Int =
        measureManager.getCellAt(x, y, CellMeasureManager.CoordinateRelativity.VIEW)

    /**
     * Shortcut for `gestureEventHandler.reportStartHovering(cell)`
     */
    protected fun reportStartHovering(cell: Int) =
        gestureEventHandler.reportStartHovering(cell)

    /**
     * Shortcut for `gestureEventHandler.reportStopHovering()`
     */
    protected fun reportStopHovering() =
        gestureEventHandler.reportStopHovering()

    /**
     * Shortcut for `gestureEventHandler.disallowParentInterceptEvent()`
     */
    protected fun disallowParentInterceptEvent() =
        gestureEventHandler.disallowParentInterceptEvent()
}