package com.github.pelmenstar1.rangecalendar

import android.graphics.Typeface
import com.github.pelmenstar1.rangecalendar.gesture.RangeCalendarGestureConfiguration
import com.github.pelmenstar1.rangecalendar.gesture.RangeCalendarGestureDetectorImpl
import com.github.pelmenstar1.rangecalendar.selection.CellAnimationType
import com.github.pelmenstar1.rangecalendar.selection.DefaultSelectionManager

internal typealias GetCalendarStyleProp = RangeCalendarStyleData.Companion.() -> Int

internal class RangeCalendarStyleData {
    private val propInts = IntArray(INT_PROPS_COUNT)
    private val propObjects = arrayOfNulls<Any>(OBJECT_PROPS_COUNT)

    fun getPackedInt(propIndex: Int) = PackedInt(propInts[propIndex])

    fun getInt(propIndex: Int) = getPackedInt(propIndex).value
    fun getFloat(propIndex: Int) = getPackedInt(propIndex).float()
    fun getBoolean(propIndex: Int) = getPackedInt(propIndex).boolean()

    inline fun getPackedInt(getProp: GetCalendarStyleProp) = getPackedInt(getProp(Companion))

    inline fun getInt(getProp: GetCalendarStyleProp) = getPackedInt(getProp).value
    inline fun getFloat(getProp: GetCalendarStyleProp) = getPackedInt(getProp).float()
    inline fun getBoolean(getProp: GetCalendarStyleProp) = getPackedInt(getProp).boolean()

    @Suppress("UNCHECKED_CAST")
    fun <T> getObject(propIndex: Int) = propObjects[propIndex - OBJECT_PROP_START] as T

    inline fun <T> getObject(getProp: GetCalendarStyleProp) = getObject<T>(getProp(Companion))

    fun set(propIndex: Int, value: Int) = set(propIndex, PackedInt(value))
    fun set(propIndex: Int, value: Float) = set(propIndex, PackedInt(value))
    fun set(propIndex: Int, value: Boolean) = set(propIndex, PackedInt(value))
    fun set(propIndex: Int, value: Enum<*>) = set(propIndex, PackedInt(value.ordinal))

    fun set(propIndex: Int, packed: PackedInt): Boolean {
        val old = propInts[propIndex]
        propInts[propIndex] = packed.value

        return old != packed.value
    }

    fun set(propIndex: Int, value: Any?): Boolean {
        val objIndex = propIndex - OBJECT_PROP_START

        val old = propObjects[objIndex]
        propObjects[objIndex] = value

        return old != value
    }

    inline fun forEachProperty(block: (propIndex: Int) -> Unit) {
        for (propIndex in 0 until INT_PROPS_COUNT) {
            block(propIndex)
        }

        for (propIndex in OBJECT_PROP_START until (OBJECT_PROP_START + OBJECT_PROPS_COUNT)) {
            block(propIndex)
        }
    }

    companion object {
        const val INT_PROPS_COUNT = 21
        const val OBJECT_PROPS_COUNT = 10

        const val OBJECT_PROP_START = 32

        // These are properties that can be stored in int32
        const val DAY_NUMBER_TEXT_SIZE = 0
        const val WEEKDAY_TEXT_SIZE = 1
        const val CELL_ROUND_RADIUS = 2
        const val CELL_WIDTH = 3
        const val CELL_HEIGHT = 4
        const val WEEKDAY_TYPE = 5
        const val CLICK_ON_CELL_SELECTION_BEHAVIOR = 6
        const val COMMON_ANIMATION_DURATION = 7
        const val HOVER_ANIMATION_DURATION = 8
        const val VIBRATE_ON_SELECTING_RANGE = 9
        const val SELECTION_FILL_GRADIENT_BOUNDS_TYPE = 10
        const val CELL_ANIMATION_TYPE = 11
        const val IN_MONTH_TEXT_COLOR = 12
        const val OUT_MONTH_TEXT_COLOR = 13
        const val DISABLED_TEXT_COLOR = 14
        const val TODAY_TEXT_COLOR = 15
        const val WEEKDAY_TEXT_COLOR = 16
        const val HOVER_ALPHA = 17
        const val SHOW_ADJACENT_MONTHS = 18
        const val IS_SELECTION_ANIMATED_BY_DEFAULT = 19
        const val IS_HOVER_ANIMATION_ENABLED = 20

        // These are properties that can be stored as an object
        const val COMMON_ANIMATION_INTERPOLATOR = 32
        const val HOVER_ANIMATION_INTERPOLATOR = 33
        const val DECOR_DEFAULT_LAYOUT_OPTIONS = 34
        const val SELECTION_FILL = 35
        const val SELECTION_MANAGER = 36
        const val CELL_ACCESSIBILITY_INFO_PROVIDER = 37
        const val WEEKDAY_TYPEFACE = 38
        const val WEEKDAYS = 39
        const val GESTURE_DETECTOR_FACTORY = 40
        const val GESTURE_CONFIGURATION = 41

        fun isObjectProperty(propIndex: Int) = propIndex >= OBJECT_PROP_START

        fun default(cr: CalendarResources): RangeCalendarStyleData {
            return RangeCalendarStyleData().apply {
                // colors
                set(IN_MONTH_TEXT_COLOR, cr.textColor)
                set(OUT_MONTH_TEXT_COLOR, cr.outMonthTextColor)
                set(DISABLED_TEXT_COLOR, cr.disabledTextColor)
                set(TODAY_TEXT_COLOR, cr.colorPrimary)
                set(WEEKDAY_TEXT_COLOR, cr.textColor)
                set(HOVER_ALPHA, cr.hoverAlpha)

                // sizes
                set(DAY_NUMBER_TEXT_SIZE, cr.dayNumberTextSize)
                set(WEEKDAY_TEXT_SIZE, cr.weekdayTextSize)
                set(WEEKDAY_TYPE, WeekdayType.SHORT)
                set(CELL_ROUND_RADIUS, Float.POSITIVE_INFINITY)
                set(CELL_WIDTH, cr.cellSize)
                set(CELL_HEIGHT, cr.cellSize)

                // typefaces
                set(WEEKDAY_TYPEFACE, Typeface.DEFAULT_BOLD)

                // animations
                set(COMMON_ANIMATION_DURATION, 250)
                set(HOVER_ANIMATION_DURATION, 100)

                set(COMMON_ANIMATION_INTERPOLATOR, LINEAR_INTERPOLATOR)
                set(HOVER_ANIMATION_INTERPOLATOR, LINEAR_INTERPOLATOR)

                set(CELL_ANIMATION_TYPE, CellAnimationType.ALPHA)

                // selection
                set(SELECTION_FILL, Fill.solid(cr.colorPrimary))
                set(SELECTION_MANAGER, DefaultSelectionManager())
                set(
                    SELECTION_FILL_GRADIENT_BOUNDS_TYPE,
                    SelectionFillGradientBoundsType.GRID
                )

                // other stuff
                set(VIBRATE_ON_SELECTING_RANGE, true)
                set(SHOW_ADJACENT_MONTHS, true)
                set(WEEKDAYS, null)
                set(
                    CLICK_ON_CELL_SELECTION_BEHAVIOR,
                    ClickOnCellSelectionBehavior.NONE
                )
                set(IS_SELECTION_ANIMATED_BY_DEFAULT, true)
                set(IS_HOVER_ANIMATION_ENABLED, true)

                set(GESTURE_DETECTOR_FACTORY, RangeCalendarGestureDetectorImpl.Factory)
                set(GESTURE_CONFIGURATION, RangeCalendarGestureConfiguration.default())
            }
        }
    }
}