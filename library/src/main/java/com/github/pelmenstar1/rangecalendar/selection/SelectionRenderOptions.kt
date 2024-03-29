package com.github.pelmenstar1.rangecalendar.selection

import com.github.pelmenstar1.rangecalendar.Border
import com.github.pelmenstar1.rangecalendar.Fill
import com.github.pelmenstar1.rangecalendar.SelectionFillGradientBoundsType

/**
 * Contains a set of properties that might be needed to draw a selection.
 *
 * Note: value of [fillState] doesn't affect [equals], [hashCode], [toString] because it's can't be compared and converted to string.
 */
class SelectionRenderOptions {
    private var _fill: Fill? = null
    private var _fillState: Fill.State? = null
    private var _fillGradientBoundsType: SelectionFillGradientBoundsType? = null
    private var _cellAnimType: CellAnimationType? = null

    /**
     * Fill of the selection.
     *
     * @throws RuntimeException if the value of the property is unset.
     */
    var fill: Fill
        get() = _fill ?: throwPropertyValueNotSet()
        internal set (value) {
            _fill = value
        }

    var fillState: Fill.State
        get() = _fillState ?: throwPropertyValueNotSet()
        internal set(value) {
            _fillState = value
        }

    /**
     * Specifies a way how to determine gradient bounds of the selection.
     *
     * @throws RuntimeException if the value of the property is unset.
     */
    var fillGradientBoundsType: SelectionFillGradientBoundsType
        get() = _fillGradientBoundsType ?: throwPropertyValueNotSet()
        internal set(value) {
            _fillGradientBoundsType = value
        }

    /**
     * Round radius of the selection shape, measured in pixels. By default, the value is `-1`
     */
    var roundRadius: Float = -1f

    var outMonthAlpha: Float = 1f

    /**
     * Specifies type of cell animation.
     *
     * @throws RuntimeException if the value of the property is unset.
     */
    var cellAnimationType: CellAnimationType
        get() = _cellAnimType ?: throwPropertyValueNotSet()
        internal set(value) {
            _cellAnimType = value
        }

    var border: Border? = null

    internal fun getFillOrNull() = _fill
    internal fun getFillStateOrNull() = _fillState
    internal fun getFillGradientBoundsTypeOrNull() = _fillGradientBoundsType
    internal fun getCellAnimationTypeOrNull() = _cellAnimType

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null || javaClass != other.javaClass) return false

        other as SelectionRenderOptions

        return _fill == other._fill &&
                _fillGradientBoundsType == other._fillGradientBoundsType &&
                roundRadius == other.roundRadius &&
                outMonthAlpha == other.outMonthAlpha &&
                _cellAnimType == other._cellAnimType &&
                border == other.border
    }

    override fun hashCode(): Int {
        var result = _fill.hashCode()
        result = result * 31 + _fillGradientBoundsType.hashCode()
        result = result * 31 + roundRadius.toBits()
        result = result * 31 + outMonthAlpha.toBits()
        result = result * 31 + _cellAnimType.hashCode()
        result = result * 31 + border.hashCode()

        return result
    }

    override fun toString(): String {
        return "SelectionRenderOptions(fill=${_fill}, fillGradientBoundsType=${_fillGradientBoundsType}, roundRadius=$roundRadius, outMonthAlpha=$outMonthAlpha, cellAnimationType=${_cellAnimType}, border=$border)"
    }

    private fun throwPropertyValueNotSet(): Nothing {
        throw RuntimeException("Value of the property is not set yet")
    }
}