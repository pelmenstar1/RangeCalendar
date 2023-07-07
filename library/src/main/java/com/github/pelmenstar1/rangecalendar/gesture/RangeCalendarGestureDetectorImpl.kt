package com.github.pelmenstar1.rangecalendar.gesture

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.github.pelmenstar1.rangecalendar.selection.Cell
import com.github.pelmenstar1.rangecalendar.selection.CellRange

internal class RangeCalendarGestureDetectorImpl : RangeCalendarGestureDetector() {
    object Factory : RangeCalendarGestureDetectorFactory<RangeCalendarGestureDetectorImpl> {
        override val detectorClass: Class<RangeCalendarGestureDetectorImpl>
            get() = RangeCalendarGestureDetectorImpl::class.java

        override fun create() = RangeCalendarGestureDetectorImpl()
    }

    private class EventTimeoutHandler : Handler(Looper.getMainLooper()) {
        override fun dispatchMessage(msg: Message) {
            val instance = msg.obj as RangeCalendarGestureDetectorImpl

            when (msg.what) {
                MSG_HOVER_PRESS -> instance.reportStartHovering(msg.arg1)
                MSG_LONG_PRESS -> instance.onStartSelectingRange(msg.arg1)
            }
        }
    }

    private val timeoutHandler = EventTimeoutHandler()

    private var rangeStartCell = -1

    private var lastTouchTime = 0L
    private var lastTouchCell = -1

    private var isSelectingRange = false

    override fun processEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val x = event.x
        val y = event.y

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                val cell = getCellAt(x, y)

                if (cell >= 0 && isSelectableCell(cell)) {
                    val eTime = event.eventTime

                    val longPressTime = eTime + ViewConfiguration.getLongPressTimeout()
                    val hoverTime = eTime + ViewConfiguration.getTapTimeout()

                    val msg1 = Message.obtain().apply {
                        what = MSG_LONG_PRESS
                        obj = this@RangeCalendarGestureDetectorImpl
                        arg1 = cell
                    }

                    val msg2 = Message.obtain().apply {
                        what = MSG_HOVER_PRESS
                        obj = this@RangeCalendarGestureDetectorImpl
                        arg1 = cell
                    }

                    // Send messages to future in order to detect long-presses or hovering.
                    // If MotionEvent.ACTION_UP event happens, these messages will be cancelled.
                    timeoutHandler.sendMessageAtTime(msg1, longPressTime)
                    timeoutHandler.sendMessageAtTime(msg2, hoverTime)
                }
            }

            MotionEvent.ACTION_UP -> {
                if (!isSelectingRange) {
                    val cellIndex = getCellAt(x, y)

                    if (cellIndex >= 0 && cellPropertiesProvider.isSelectableCell(cellIndex)) {
                        val touchTime = event.downTime
                        val timeout = ViewConfiguration.getDoubleTapTimeout()

                        if (touchTime - lastTouchTime < timeout && lastTouchCell == cellIndex) {
                            val weekIndex = Cell(cellIndex).gridY
                            val (start, end) = CellRange.week(weekIndex)

                            selectRange(start.index, end.index, SelectionByGestureType.OTHER)
                        } else {
                            selectRange(
                                cellIndex,
                                cellIndex,
                                SelectionByGestureType.SINGLE_CELL_ON_CLICK
                            )

                            lastTouchCell = cellIndex
                            lastTouchTime = touchTime
                        }
                    }
                }

                cancelTimeoutMessages()
                reportStopHovering()

                isSelectingRange = false
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelTimeoutMessages()
                reportStopHovering()

                isSelectingRange = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (isSelectingRange) {
                    gestureEventHandler.disallowParentInterceptEvent()

                    val cell = getCellAt(x, y)

                    if (cell >= 0 && isSelectableCell(cell)) {
                        val (start, end) = CellRange(rangeStartCell, cell).normalize()

                        selectRange(start.index, end.index, SelectionByGestureType.LONG_SELECTION)
                    }
                }
            }
        }

        return true
    }

    private fun onStartSelectingRange(cell: Int) {
        isSelectingRange = true
        rangeStartCell = cell
        gestureEventHandler.reportStartSelectingRange()
    }

    private fun cancelTimeoutMessages() {
        timeoutHandler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val MSG_LONG_PRESS = 0
        private const val MSG_HOVER_PRESS = 1
    }
}