package com.github.pelmenstar1.rangecalendar.gesture

import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import com.github.pelmenstar1.rangecalendar.Distance
import com.github.pelmenstar1.rangecalendar.SelectionAcceptanceStatus
import com.github.pelmenstar1.rangecalendar.selection.CellRange
import com.github.pelmenstar1.rangecalendar.utils.getDistance
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

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

    private class PinchInfo {
        var isStarted = false
        var isFinished = false

        val startP0 = PointF()
        val startP1 = PointF()

        var startWeekIndex = 0

        var type = PINCH_TYPE_NONE
        var firstPointerRole = ROLE_NONE

        fun setStartLine(x0: Float, y0: Float, x1: Float, y1: Float) {
            startP0.set(x0, y0)
            startP1.set(x1, y1)
        }

        fun getDistanceToStartPoint0(x: Float, y: Float): Float {
            return getDistance(startP0.x, startP0.y, x, y)
        }

        fun getDistanceToStartPoint1(x: Float, y: Float): Float {
            return getDistance(startP1.x, startP1.y, x, y)
        }

        fun bothDistanceGreaterThanMinDistance(x0: Float, y0: Float, x1: Float, y1: Float, minDist: Float): Boolean {
            return getDistanceToStartPoint0(x0, y0) > minDist && getDistanceToStartPoint1(x1, y1) > minDist
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

    private val pinchInfo = PinchInfo()

    override fun processEvent(event: MotionEvent): Boolean {
        val x0 = event.x
        val y0 = event.y
        val pointerCount = event.pointerCount

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (pointerCount != 2) {
                    pinchInfo.invalidate()
                }

                if (pointerCount == 1) {
                    onSinglePointerDown(event)
                } else if (pointerCount == 2) {
                    onTwoPointersDownOrMove(event)
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (!isSelectingLongRange) {
                    val cellIndex = getCellAt(x0, y0)

                    if (cellIndex >= 0 && isSelectableCell(cellIndex)) {
                        val eventTime = event.eventTime
                        val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout()

                        if (lastDownTouchCell == cellIndex) {
                            if (isEnabledGesture { doubleTapWeek } &&
                                eventTime - lastUpTouchTime < doubleTapTimeout &&
                                lastUpTouchCell == cellIndex
                            ) {
                                selectWeek(weekIndex = cellIndex / 7)
                            } else if (isEnabledGesture { singleTapCell }) {
                                selectRange(cellIndex, cellIndex, SelectionByGestureType.SINGLE_CELL_ON_CLICK)
                            }
                        }

                        lastUpTouchTime = eventTime
                        lastUpTouchCell = cellIndex
                    }
                }

                cancelTimeoutMessages()
                reportStopHovering()

                pinchInfo.invalidate()

                isSelectingLongRange = false
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelTimeoutMessages()
                reportStopHovering()

                pinchInfo.invalidate()

                isSelectingLongRange = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (pointerCount != 2) {
                    pinchInfo.invalidate()
                }

                if (isSelectingLongRange && pointerCount == 1) {
                    disallowParentInterceptEvent()

                    val cell = getCellAt(x0, y0)

                    if (cell >= 0 && isSelectableCell(cell)) {
                        val range = CellRange(longRangeStartCell, cell).normalize()

                        selectRange(range, SelectionByGestureType.LONG_SELECTION)
                    }
                } else if (!isSelectingLongRange && pointerCount == 2) {
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
            val hoverTime = eventTime + ViewConfiguration.getTapTimeout()

            val msg2 = Message.obtain().apply {
                what = MSG_HOVER_PRESS
                obj = this@RangeCalendarGestureDetectorImpl
                arg1 = cell
            }

            lastDownTouchCell = cell

            // Send messages to future in order to detect long-presses or hovering.
            // If MotionEvent.ACTION_UP/ACTION_CANCEL/ACTION_MOVE (when two pointers down) event happens,
            // these messages are cancelled.
            timeoutHandler.sendMessageAtTime(msg2, hoverTime)

            // Detect long presses only when the gesture is enabled.
            if (isEnabledGesture { longPressRange }) {
                val msg1 = Message.obtain().apply {
                    what = MSG_LONG_PRESS
                    obj = this@RangeCalendarGestureDetectorImpl
                    arg1 = cell
                }

                val longPressTime = eventTime + ViewConfiguration.getLongPressTimeout()

                timeoutHandler.sendMessageAtTime(msg1, longPressTime)
            }
        }
    }

    private fun onTwoPointersDownOrMove(event: MotionEvent) {
        // Disallow parent to "see" the event as even when two pointers are down, the move event causes horizontal scrolling
        disallowParentInterceptEvent()

        if (pinchInfo.isFinished) {
            return
        }

        val x0 = event.getX(0)
        val y0 = event.getY(0)

        val x1 = event.getX(1)
        val y1 = event.getY(1)

        val conf = configuration
        val gestureTypeOptions = conf.gestureTypeOptions

        val weekPinchConf = gestureTypeOptions.get { horizontalPinchWeek }
        val monthPinchConf = gestureTypeOptions.get { diagonalPinchMonth }

        if (!pinchInfo.isStarted) {
            cancelTimeoutMessages()

            pinchInfo.apply {
                isStarted = true

                setStartLine(x0, y0, x1, y1)

                val angle = getLineAngle(x0, y0, x1, y1)

                val cell0 = getCellAt(x0, y0)
                val cell1 = getCellAt(x1, y1)

                val enabledTypes = conf.enabledGestureTypes

                // Currently, all supported pinch gestures require that two pointers to be on the grid.
                if (cell0 < 0 || cell1 < 0) {
                    isFinished = true
                    return
                }

                if (enabledTypes.contains { horizontalPinchWeek } && isWeekAngle(angle, weekPinchConf)) {
                    val weekIndex0 = cell0 / 7
                    val weekIndex1 = cell1 / 7

                    startWeekIndex = weekIndex0

                    // Week can't be selected if the first gesture makes week index undefined (cells are on different rows)
                    isFinished = weekIndex0 != weekIndex1

                    // Assign the role of left pointer to the first pointer if the first pointer
                    // is more on the left side than the right pointer. Otherwise, make it right pointer.
                    firstPointerRole = if (x0 < x1) ROLE_WEEK_LEFT else ROLE_WEEK_RIGHT

                    type = PINCH_TYPE_WEEK
                } else if (enabledTypes.contains { diagonalPinchMonth } && isMonthAngle(angle, monthPinchConf)) {
                    // Assign the role of left-bottom pointer to the first pointer if the first pointer
                    // is more on the left side than the right pointer. Giving the angle is somewhere 45 degrees,
                    // we check only x axis.
                    firstPointerRole = if (x0 < x1) ROLE_MONTH_LB else ROLE_MONTH_RT

                    type = PINCH_TYPE_MONTH
                } else {
                    // Seems like it's an unknown gesture.
                    isFinished = true
                    type = PINCH_TYPE_NONE
                }
            }

            // Any selection can't be detected based on a single gesture.
            return
        }

        val (startX0, startY0) = pinchInfo.startP0
        val (startX1, startY1) = pinchInfo.startP1

        val angle = getLineAngle(x0, y0, x1, y1)

        when (pinchInfo.type) {
            PINCH_TYPE_WEEK -> {
                if (!isWeekAngle(angle, weekPinchConf)) {
                    pinchInfo.isFinished = true
                    return
                }

                // Check if the pointers are "stretching", not "narrowing" based on the pointer roles.
                val isValidGesture = if (pinchInfo.firstPointerRole == ROLE_WEEK_LEFT) {
                    x0 < startX0 && x1 > startX1
                } else {
                    x0 > startX0 && x1 < startX1
                }

                // Do not finish the gesture if pointers are not acting the way they're expected to.
                // It might happen sometimes but it doesn't make the gesture wrong.
                if (isValidGesture) {
                    val minDist = getAbsoluteDistance(weekPinchConf.minDistance)

                    if (pinchInfo.bothDistanceGreaterThanMinDistance(x0, y0, x1, y1, minDist)) {
                        val cell0 = getCellAt(x0, y0)
                        val cell1 = getCellAt(x1, y1)

                        val weekIndex0 = cell0 / 7
                        val weekIndex1 = cell1 / 7

                        if (pinchInfo.startWeekIndex == weekIndex0 && weekIndex0 == weekIndex1) {
                            selectWeek(weekIndex0)
                        }

                        // Gesture is finished even if cells on different rows
                        pinchInfo.isFinished = true
                    }
                }
            }

            PINCH_TYPE_MONTH -> {
                if (!isMonthAngle(angle, monthPinchConf)) {
                    pinchInfo.isFinished = true
                    return
                }

                // Check if the pointers are "stretching", not "narrowing" based on the pointer roles.
                val isValidGesture = if (pinchInfo.firstPointerRole == ROLE_MONTH_RT) {
                    (x0 > startX0 && y0 < startY0) && (x1 < startX1 && y1 > startY1)
                } else {
                    (x0 < startX0 && y0 > startY0) && (x1 > startX1 && y1 < startY1)
                }

                // Do not finish the gesture if pointers are not acting the way they're expected to.
                // It might happen sometimes but it doesn't make the gesture wrong.
                if (isValidGesture) {
                    val minDist = getAbsoluteDistance(monthPinchConf.minDistance)

                    if (pinchInfo.bothDistanceGreaterThanMinDistance(x0, y0, x1, y1, minDist)) {
                        selectMonth()

                        pinchInfo.isFinished = true
                    }
                }
            }
        }
    }

    private fun getAbsoluteDistance(distance: Distance): Float {
        return when (distance) {
            is Distance.Absolute -> distance.value
            is Distance.Relative -> {
                distance.fraction * measureManager.getRelativeAnchorValue(distance.anchor)
            }
        }
    }

    private inline fun isEnabledGesture(getType: GetDefaultGestureType<*>): Boolean {
        return configuration.enabledGestureTypes.contains(getType)
    }

    private fun selectWeek(weekIndex: Int) {
        selectRange(CellRange.week(weekIndex), SelectionByGestureType.OTHER)
    }

    private fun onStartSelectingRange(cell: Int) {
        val status = selectRange(cell, cell, SelectionByGestureType.LONG_SELECTION)

        if (status != SelectionAcceptanceStatus.REJECTED) {
            isSelectingLongRange = true
            longRangeStartCell = cell
            gestureEventHandler.reportStartSelectingRange()
        }
    }

    private fun selectRange(range: CellRange, gestureType: SelectionByGestureType): SelectionAcceptanceStatus {
        return selectRange(range.start.index, range.end.index, gestureType)
    }

    private fun cancelTimeoutMessages() {
        timeoutHandler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val MSG_LONG_PRESS = 0
        private const val MSG_HOVER_PRESS = 1

        // 45 degrees
        private const val MONTH_ANGLE = (PI / 4).toFloat()

        private const val PINCH_TYPE_NONE = 0
        private const val PINCH_TYPE_WEEK = 1
        private const val PINCH_TYPE_MONTH = 2

        private const val ROLE_NONE = 0
        private const val ROLE_WEEK_LEFT = 1
        private const val ROLE_WEEK_RIGHT = 2
        private const val ROLE_MONTH_RT = 3
        private const val ROLE_MONTH_LB = 4

        private fun isWeekAngle(angle: Float, conf: PinchConfiguration): Boolean {
            val dev = conf.angleDeviation

            return angle > PI.toFloat() - dev || angle < dev
        }

        private fun isMonthAngle(angle: Float, conf: PinchConfiguration): Boolean {
            return abs(angle - MONTH_ANGLE) < conf.angleDeviation
        }

        private fun getLineAngle(x0: Float, y0: Float, x1: Float, y1: Float): Float {
            val dx = abs(x0 - x1)
            val dy = abs(y0 - y1)

            return atan2(dy, dx)
        }
    }
}