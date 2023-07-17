package com.github.pelmenstar1.rangecalendar.gesture

import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.github.pelmenstar1.rangecalendar.Distance
import com.github.pelmenstar1.rangecalendar.SelectionAcceptanceStatus
import com.github.pelmenstar1.rangecalendar.selection.CellRange
import com.github.pelmenstar1.rangecalendar.utils.getSquareDistance
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

        var startX0 = 0f
        var startY0 = 0f
        var startX1 = 0f
        var startY1 = 0f

        var startWeekIndex = 0

        var type = PINCH_TYPE_NONE
        var firstPointerRole = ROLE_NONE

        fun setStartLine(x0: Float, y0: Float, x1: Float, y1: Float) {
            startX0 = x0
            startX0 = y0
            startX1 = x1
            startX1 = y1
        }

        fun bothDistanceGreaterThanMinDistance(x0: Float, y0: Float, x1: Float, y1: Float, minDist: Float): Boolean {
            val sqMinDist = minDist * minDist

            return getSquareDistance(startX0, startY0, x0, y0) > sqMinDist &&
                    getSquareDistance(startX1, startY1, x1, y1) > sqMinDist
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
    private var isSelectingCustomRange = false

    private val pinchInfo = PinchInfo()

    override fun processEvent(event: MotionEvent): Boolean {
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
                if (!isSelectingCustomRange && pointerCount == 1) {
                    val cellIndex = getCellAt(event.x, event.y)

                    if (cellIndex >= 0 && isSelectableCell(cellIndex)) {
                        val eventTime = event.eventTime
                        val doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout()

                        if (lastDownTouchCell == cellIndex) {
                            val enabledTypes = configuration.enabledGestureTypes

                            if (enabledTypes.contains { doubleTapWeek } &&
                                eventTime - lastUpTouchTime < doubleTapTimeout &&
                                lastUpTouchCell == cellIndex
                            ) {
                                selectWeek(weekIndex = cellIndex / 7)
                            } else if (enabledTypes.contains { singleTapCell }) {
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

                isSelectingCustomRange = false
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelTimeoutMessages()
                reportStopHovering()

                pinchInfo.invalidate()

                isSelectingCustomRange = false
            }

            MotionEvent.ACTION_MOVE -> {
                if (pointerCount != 2) {
                    pinchInfo.invalidate()
                }

                if (isSelectingCustomRange) {
                    onPointersMoveWhenSelectingCustomRange(event)
                } else if (pointerCount == 2) {
                    onTwoPointersDownOrMove(event)
                }
            }
        }

        return true
    }

    private fun onPointersMoveWhenSelectingCustomRange(event: MotionEvent) {
        val conf = configuration
        val enabledTypes = conf.enabledGestureTypes

        val x0 = event.getX(0)
        val y0 = event.getY(0)

        when (event.pointerCount) {
            1 -> {
                if (!enabledTypes.contains { longPressRange }) {
                    return
                }

                disallowParentInterceptEvent()

                val cell = getCellAt(x0, y0)

                if (isSelectableCell(cell)) {
                    val range = CellRange(longRangeStartCell, cell).normalize()

                    selectRange(range, SelectionByGestureType.LONG_SELECTION)
                }
            }

            2 -> {
                if (!enabledTypes.contains { longPressTwoPointersRange }) {
                    return
                }

                disallowParentInterceptEvent()

                val x1 = event.getX(1)
                val y1 = event.getY(1)

                val cell0 = getCellAt(x0, y0)
                val cell1 = getCellAt(x1, y1)

                if (isSelectableCell(cell0) && isSelectableCell(cell1)) {
                    val range = CellRange(cell0, cell1).normalize()

                    selectRange(range, SelectionByGestureType.LONG_SELECTION)
                }
            }
        }
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
            if (isEnabledGesture { longPressRange } || isEnabledGesture { longPressTwoPointersRange }) {
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
        val conf = configuration
        val enabledTypes = conf.enabledGestureTypes

        val isPinchWeekEnabled = enabledTypes.contains { horizontalPinchWeek }
        val isPinchMonthEnabled = enabledTypes.contains { diagonalPinchMonth }

        if (!isPinchWeekEnabled && !isPinchMonthEnabled) {
            return
        }

        // Disallow parent to "see" the event as even when two pointers are down, the move event causes horizontal scrolling
        disallowParentInterceptEvent()

        if (pinchInfo.isFinished) {
            return
        }

        val x0 = event.getX(0)
        val y0 = event.getY(0)

        val x1 = event.getX(1)
        val y1 = event.getY(1)

        val gestureTypeOptions = conf.gestureTypeOptions
        val weekPinchConf = gestureTypeOptions.getOptionsAndCheck(isPinchWeekEnabled) { horizontalPinchWeek }
        val monthPinchConf = gestureTypeOptions.getOptionsAndCheck(isPinchMonthEnabled) { diagonalPinchMonth }

        if (!pinchInfo.isStarted) {
            cancelTimeoutMessages()

            pinchInfo.apply {
                isStarted = true

                setStartLine(x0, y0, x1, y1)

                val angle = getLineAngle(x0, y0, x1, y1)

                val cell0 = getCellAt(x0, y0)
                val cell1 = getCellAt(x1, y1)

                // Currently, all supported pinch gestures require that two pointers to be on the grid.
                if (cell0 < 0 || cell1 < 0) {
                    isFinished = true
                    return
                }

                // If either weekPinchConf or monthPinchConf is not null, it means that corresponding gesture type
                // is enabled.
                if (weekPinchConf != null && isWeekAngle(angle, weekPinchConf)) {
                    val weekIndex0 = cell0 / 7
                    val weekIndex1 = cell1 / 7

                    startWeekIndex = weekIndex0

                    // Week can't be selected if the first gesture makes week index undefined (cells are on different rows)
                    isFinished = weekIndex0 != weekIndex1

                    // Assign the role of left pointer to the first pointer if the first pointer
                    // is more on the left side than the right pointer. Otherwise, make it right pointer.
                    firstPointerRole = if (x0 < x1) ROLE_WEEK_LEFT else ROLE_WEEK_RIGHT

                    type = PINCH_TYPE_WEEK
                } else if (monthPinchConf != null && isMonthAngle(angle, monthPinchConf)) {
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

        val startX0 = pinchInfo.startX0
        val startY0 = pinchInfo.startY0
        val startX1 = pinchInfo.startX1
        val startY1 = pinchInfo.startY1

        val angle = getLineAngle(x0, y0, x1, y1)

        when (pinchInfo.type) {
            PINCH_TYPE_WEEK -> {
                // We can't have pinchInfo.type == PINCH_TYPE_WEEK and weekPinchConf that is null
                if (!isWeekAngle(angle, weekPinchConf!!)) {
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
                // We can't have pinchInfo.type == PINCH_TYPE_MONTH and monthPinchConf that is null
                if (!isMonthAngle(angle, monthPinchConf!!)) {
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
            isSelectingCustomRange = true
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

        private inline fun <reified TOptions : Any> RangeCalendarGestureTypeMap.getOptionsAndCheck(
            shouldExist: Boolean,
            getType: GetDefaultGestureType<TOptions>
        ): TOptions? {
            val type = RangeCalendarDefaultGestureTypes.getType()
            val result = getRaw(type)

            checkGestureOptions(result, shouldExist, type, expectedClass = TOptions::class.java)

            return result as TOptions?
        }

        private fun checkGestureOptions(
            options: Any?,
            shouldBeNotNull: Boolean,
            type: RangeCalendarGestureType<*>,
            expectedClass: Class<*>
        ) {
            if (shouldBeNotNull) {
                if (options == null) {
                    throw IllegalStateException("Gesture '$type' is enabled but there is no options provided")
                }

                if (options.javaClass != expectedClass) {
                    throw IllegalStateException("Options of type '$expectedClass' is expected for the gesture '$type' but provided: ${options.javaClass}")
                }
            }
        }

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