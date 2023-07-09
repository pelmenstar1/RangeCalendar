package com.github.pelmenstar1.rangecalendar.gesture

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.github.pelmenstar1.rangecalendar.Distance
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

    private class PinchInfo {
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

    private val pinchInfo = PinchInfo()

    override fun processEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked

        val x0 = event.x
        val y0 = event.y
        val pointerCount = event.pointerCount

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (pointerCount != 2) {
                    pinchInfo.invalidate()
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

                        if (lastDownTouchCell == cellIndex) {
                            if (isEnabledGesture { doubleTapWeek } &&
                                eventTime - lastUpTouchTime < doubleTapTimeout &&
                                lastUpTouchCell == cellIndex
                            ) {
                                selectWeek(weekIndex = cellIndex / 7)
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

        val weekPinchConf = gestureTypeOptions.get<PinchConfiguration> { horizontalPinchWeek }
        val monthPinchConf = gestureTypeOptions.get<PinchConfiguration> { diagonalPinchMonth }

        if (!pinchInfo.isStarted) {
            cancelTimeoutMessages()

            pinchInfo.apply {
                isStarted = true

                setPoint0(x0, y0)
                setPoint1(x1, y1)

                val angle = getLineAngle(x0, y0, x1, y1)
                startAngle = angle

                val enabledTypes = conf.enabledGestureTypes

                if (enabledTypes.contains { horizontalPinchWeek } && isWeekAngle(angle, weekPinchConf)) {
                    val cell0 = getCellAt(x0, y0)
                    val cell1 = getCellAt(x1, y1)

                    val weekIndex0 = cell0 / 7
                    val weekIndex1 = cell1 / 7

                    startWeekIndex = weekIndex0

                    // Week can't be selected if the first gesture makes week index undefined (cells on different rows)
                    isFinished = weekIndex0 != weekIndex1
                } else {
                    val isMonthEnabled = enabledTypes.contains { diagonalPinchMonth }

                    // Seems like this gesture is not defined. It's not week nor month.
                    isFinished = !isMonthEnabled || !isMonthAngle(angle, monthPinchConf)
                }
            }

            // Any selection can't be detected based on a single gesture.
            return
        }

        val angle = getLineAngle(x0, y0, x1, y1)
        val startAngle = pinchInfo.startAngle

        when {
            isWeekAngle(startAngle, monthPinchConf) && isWeekAngle(angle, monthPinchConf) -> {
                val xDist0 = abs(pinchInfo.startX0 - x0)
                val xDist1 = abs(pinchInfo.startX1 - x1)

                if (min(xDist0, xDist1) >= getAbsoluteDistance(weekPinchConf.minDistance)) {
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

            isMonthAngle(startAngle, monthPinchConf) && isMonthAngle(angle, monthPinchConf) -> {
                val dist0 = pinchInfo.getDistanceToStartPoint0(x0, y0)
                val dist1 = pinchInfo.getDistanceToStartPoint1(x1, y1)

                if (min(dist0, dist1) >= getAbsoluteDistance(monthPinchConf.minDistance)) {
                    selectMonth()

                    pinchInfo.isFinished = true
                }
            }

            else -> {
                // Seems like this gesture is not defined
                pinchInfo.isFinished = true
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

    private inline fun isEnabledGesture(getType: RangeCalendarDefaultGestureTypes.() -> RangeCalendarGestureType): Boolean {
        return configuration.enabledGestureTypes.contains(getType)
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

        // 45 degrees
        private const val MONTH_ANGLE = (PI / 4).toFloat()

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

        private fun isCellsOnSameRow(cellIndex0: Int, cellIndex1: Int): Boolean {
            return Cell(cellIndex0).gridY == Cell(cellIndex1).gridY
        }
    }
}