package com.github.pelmenstar1.rangecalendar.selection

import com.github.pelmenstar1.rangecalendar.Fill
import com.github.pelmenstar1.rangecalendar.SelectionFillGradientBoundsType

/**
 * Contains a set of properties that might be needed to draw a selection.
 */
class SelectionRenderOptions {
    private var _fill: Fill? = null
    private var _fillGradientBoundsType: SelectionFillGradientBoundsType? = null
    private var _roundRadius = -1f
    private var _cellAnimType: CellAnimationType? = null

    /**
     * Fill of the selection.
     */
    var fill: Fill
        get() = _fill ?: throwPropertyValueNotSet()
        internal set (value) {
            _fill = value
        }

    /**
     * Specifies a way how to determine gradient bounds of the selection.
     */
    var fillGradientBoundsType: SelectionFillGradientBoundsType
        get() = _fillGradientBoundsType ?: throwPropertyValueNotSet()
        internal set(value) {
            _fillGradientBoundsType = value
        }

    /**
     * Round radius of the selection shape, measured in pixels.
     */
    var roundRadius: Float
        get() = _roundRadius
        internal set(value) {
            _roundRadius = value
        }

    /**
     * Specifies type of cell animation.
     */
    var cellAnimationType: CellAnimationType
        get() = _cellAnimType ?: throwPropertyValueNotSet()
        internal set(value) {
            _cellAnimType = value
        }

    internal fun getFillOrNull() = _fill
    internal fun getFillGradientBoundsTypeOrNull() = _fillGradientBoundsType
    internal fun getCellAnimationTypeOrNull() = _cellAnimType

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null || javaClass != other.javaClass) return false

        other as SelectionRenderOptions

        return _fill == other._fill &&
                _fillGradientBoundsType == other._fillGradientBoundsType &&
                _roundRadius == other._roundRadius &&
                _cellAnimType == other._cellAnimType
    }

    override fun hashCode(): Int {
        var result = _fill.hashCode()
        result = result * 31 + _fillGradientBoundsType.hashCode()
        result = result * 31 + _roundRadius.toBits()
        result = result * 31 + _cellAnimType.hashCode()

        return result
    }

    override fun toString(): String {
        return "SelectionRenderOptions(fill=${_fill}, fillGradientBoundsType=${_fillGradientBoundsType}, roundRadius=${_roundRadius}, cellAnimationType=${_cellAnimType})"
    }

    private fun throwPropertyValueNotSet(): Nothing {
        throw RuntimeException("Value of the property is not set yet")
    }
}