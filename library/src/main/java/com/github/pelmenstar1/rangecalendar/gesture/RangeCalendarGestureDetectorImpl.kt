package com.github.pelmenstar1.rangecalendar.gesture

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.github.pelmenstar1.rangecalendar.selection.Cell
import com.github.pelmenstar1.rangecalendar.selection.CellRange
import com.github.pelmenstar1.rangecalendar.utils.getDistance
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min

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

    private class ScaleInfo {
        var isStarted = false
        var isFinished = false

        var startX0 = 0f
        var startY0 = 0f

        var startX1 = 0f
        var startY1 = 0f

        var startAngle = 0f
        var startWeekIndex = 0

        fun setPoint0(x: Float, y: Float) {
            startX0 = x
            startY0 = y
        }

        fun setPoint1(x: Float, y: Float) {
            startX1 = x
            startY1 = y
        }

        fun getDistanceToStartPoint0(x: Float, y: Float): Float {
            return getDistance(startX0, startY0, x, y)
        }

        fun getDistanceToStartPoint1(x: Float, y: Float): Float {
            return getDistance(startX1, startY1, x, y)
        }

        fun invalidate() {
            isStarted = false
            isFinished = false
        }
    }

    private val timeoutHandler = EventTimeoutHandler()

    private var lastUpTouchTime = 0L

    private var lastUpTouchCell = -1
    private var lastDownTouchCell = -1

    private var longRangeStartCell = -1
    private var isSelectingLongRange = false

    private val scaleInfo = ScaleInfo()

    override fun processEvent(event: MotionEvent): Boolean {
        Log.i("GestureDetectorImpl", event.toString())

        val action = event.actionMasked

        val x0 = event.x
        val y0 = event.y
        val pointerCount = event.pointerCount

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (pointerCount != 2) {
                    scaleInfo.invalidate()
                }

                if (pointerCount == 1) {
                    onSinglePointerDown(event)
                } else if (pointerCount == 2) {
                    onTwoPointersDownOrMove(event)
                }
            }

            MotionEvent.ACTION_UP -> {
                if (!isSelectingLongRange) {
                    val cellIndex = getCellAt(x0, y0)

                    if (cellIndex >= 0 && isSelectableCell(cellIndex)) {
                        val eventTime = event.eventTime
                        val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout()

                        Log.i(
                            "GestureDetectorImpl",
                            "UP -> lastDownTouchCell: $lastDownTouchCell doubleTapTimeout: $doubleTapTimeout cellIndex: $cellIndex"
                        )

                        if (lastDownTouchCell == cellIndex) {
                            val elapsedFromLastUp = eventTime - lastUpTouchTime

                            if (elapsedFromLastUp < doubleTapTimeout && lastUpTouchCell == cellIndex) {
                                selectWeek(cellIndex)
                            } else {
                                selectRange(
                                    cellIndex, cellIndex,
                                    SelectionByGestureType.SINGLE_CELL_ON_CLICK
                                )
                            }
                        }

                        lastUpTouchTime = eventTime
                        lastUpTouchCell = cellIndex
                    }
                }

                cancelTimeoutMessages()
                reportStopHovering()

                scaleInfo.invalidate()

                isSelectingLongRange = false
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelTimeoutMessages()
                reportStopHovering()

                scaleInfo.invalidate()

                isSelectingLongRange = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (pointerCount != 2) {
                    scaleInfo.invalidate()
                }

                if (isSelectingLongRange && pointerCount == 1) {
                    disallowParentInterceptEvent()

                    val cell = getCellAt(x0, y0)

                    if (cell >= 0 && isSelectableCell(cell)) {
                        val (start, end) = CellRange(longRangeStartCell, cell).normalize()

                        selectRange(start.index, end.index, SelectionByGestureType.LONG_SELECTION)
                    }
                } else if (pointerCount == 2) {
                    onTwoPointersDownOrMove(event)
                }
            }
        }

        return true
    }

    private fun onSinglePointerDown(event: MotionEvent) {
        val x = event.x
        val y = event.y

        val cell = getCellAt(x, y)

        if (cell >= 0 && isSelectableCell(cell)) {
            val eventTime = event.eventTime

            val longPressTime = eventTime + ViewConfiguration.getLongPressTimeout()
            val hoverTime = eventTime + ViewConfiguration.getTapTimeout()

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

            lastDownTouchCell = cell

            // Send messages to future in order to detect long-presses or hovering.
            // If MotionEvent.ACTION_UP event happens, these messages will be cancelled.
            timeoutHandler.sendMessageAtTime(msg1, longPressTime)
            timeoutHandler.sendMessageAtTime(msg2, hoverTime)
        }
    }

    private fun onTwoPointersDownOrMove(event: MotionEvent) {
        disallowParentInterceptEvent()

        if (scaleInfo.isFinished) {
            return
        }

        val x0 = event.getX(0)
        val y0 = event.getY(0)

        val x1 = event.getX(1)
        val y1 = event.getY(1)

        if (!scaleInfo.isStarted) {
            cancelTimeoutMessages()

            scaleInfo.apply {
                isStarted = true

                setPoint0(x0, y0)
                setPoint1(x1, y1)

                val angle = getLineAngle(x0, y0, x1, y1)
                startAngle = angle

                if (isWeekAngle(angle)) {
                    val cell0 = getCellAt(x0, y0)
                    val cell1 = getCellAt(x1, y1)

                    val weekIndex0 = cell0 / 7
                    val weekIndex1 = cell1 / 7

                    startWeekIndex = weekIndex0

                    // Week can't be selected if the first gesture makes week index undefined (cells on different rows)
                    isFinished = weekIndex0 != weekIndex1
                }
            }

            // Any selection can't be detected based on a single gesture.
            return
        }

        val angle = getLineAngle(x0, y0, x1, y1)

        val minLength = 75
        val xDist0 = abs(scaleInfo.startX0 - x0)
        val xDist1 = abs(scaleInfo.startX1 - x1)

        Log.i(
            "GestureDetectorImpl",
            "angle: ${angle * (180f / PI)} scaleStartAngle: ${scaleInfo.startAngle * (180f / PI)} length0: $xDist0 length1: $xDist1"
        )

        if (isWeekAngle(scaleInfo.startAngle) && isWeekAngle(angle) && min(xDist0, xDist1) >= minLength) {
            val cell0 = getCellAt(x0, y0)
            val cell1 = getCellAt(x1, y1)

            val weekIndex0 = cell0 / 7
            val weekIndex1 = cell1 / 7

            if (weekIndex0 == weekIndex1) {
                selectWeek(weekIndex0)
            }

            // Gesture is finished even if cells on different rows
            scaleInfo.isFinished = true
        }
    }

    private fun selectWeek(weekIndex: Int) {
        val (start, end) = CellRange.week(weekIndex)

        selectRange(start.index, end.index, SelectionByGestureType.OTHER)
    }

    private fun onStartSelectingRange(cell: Int) {
        isSelectingLongRange = true
        longRangeStartCell = cell
        gestureEventHandler.reportStartSelectingRange()
    }

    private fun cancelTimeoutMessages() {
        timeoutHandler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val MSG_LONG_PRESS = 0
        private const val MSG_HOVER_PRESS = 1

        // in radians
        private const val ANGLE_ACCURACY = (15 * (PI / 180)).toFloat()

        private fun isWeekAngle(angle: Float): Boolean {
            return angle > PI.toFloat() - ANGLE_ACCURACY || angle < ANGLE_ACCURACY
        }

        private fun getLineAngle(x0: Float, y0: Float, x1: Float, y1: Float): Float {
            val dx = abs(x0 - x1)
            val dy = abs(y0 - y1)

            return atan2(dy, dx)
        }

        private fun isCellsOnSameRow(cellIndex0: Int, cellIndex1: Int): Boolean {
            return Cell(cellIndex0).gridY == Cell(cellIndex1).gridY
        }
    }
}