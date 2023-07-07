package com.github.pelmenstar1.rangecalendar.gesture

import android.view.MotionEvent
import com.github.pelmenstar1.rangecalendar.CellMeasureManager
import com.github.pelmenstar1.rangecalendar.RangeCalendarCellPropertiesProvider

abstract class RangeCalendarGestureDetector {
    private var _measureManager: CellMeasureManager? = null
    private var _cellPropertiesProvider: RangeCalendarCellPropertiesProvider? = null
    private var _gestureEventHandler: RangeCalendarGestureEventHandler? = null

    val measureManager: CellMeasureManager
        get() = _measureManager ?: throwBindNotCalled()

    val cellPropertiesProvider: RangeCalendarCellPropertiesProvider
        get() = _cellPropertiesProvider ?: throwBindNotCalled()

    val gestureEventHandler: RangeCalendarGestureEventHandler
        get() = _gestureEventHandler ?: throwBindNotCalled()

    private fun throwBindNotCalled(): Nothing {
        throw RuntimeException("bind() should be called before accessing the property")
    }

    fun bind(
        measureManager: CellMeasureManager,
        cellPropertiesProvider: RangeCalendarCellPropertiesProvider,
        gestureEventHandler: RangeCalendarGestureEventHandler
    ) {
        _measureManager = measureManager
        _cellPropertiesProvider = cellPropertiesProvider
        _gestureEventHandler = gestureEventHandler
    }

    abstract fun processEvent(event: MotionEvent): Boolean

    protected fun selectRange(start: Int, end: Int, gestureType: SelectionByGestureType) =
        gestureEventHandler.selectRange(start, end, gestureType)

    protected fun selectMonth() =
        gestureEventHandler.selectMonth()

    protected fun isSelectableCell(cell: Int): Boolean =
        cellPropertiesProvider.isSelectableCell(cell)

    protected fun getCellAt(x: Float, y: Float): Int =
        measureManager.getCellAt(x, y)

    protected fun reportStartHovering(cell: Int) =
        gestureEventHandler.reportStartHovering(cell)

    protected fun reportStopHovering() =
        gestureEventHandler.reportStopHovering()

    protected fun disallowParentInterceptEvent () =
        gestureEventHandler.disallowParentInterceptEvent()
}