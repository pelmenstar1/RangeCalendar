package io.github.pelmenstar1.rangecalendar

import android.graphics.*
import androidx.annotation.ColorInt
import kotlin.math.sqrt

/**
 * Represents either a solid fill or gradient fill.
 */
class Fill(
    private val type: Int,
    private val color: Int = 0,
    private val gradientColors: IntArray? = null,
    private val gradientPositions: FloatArray? = null
) {
    private var shader: Shader? = null
    private var shaderBounds = PackedRectF(0)

    /**
     * Sets bounds in which the fill is applied
     */
    fun setBounds(bounds: RectF) {
        setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
    }

    /**
     * Sets bounds in which the fill is applied
     */
    fun setBounds(left: Float, top: Float, right: Float, bottom: Float) {
        setBounds(left, top, right, bottom) { PackedRectF(left, top, right, bottom) }
    }

    internal fun setBounds(bounds: PackedRectF) {
        setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom) { bounds }
    }

    private inline fun setBounds(
        left: Float, top: Float, right: Float, bottom: Float,
        computePackedBounds: () -> PackedRectF
    ) {
        if(type != TYPE_SOLID) {
            val packedBounds = computePackedBounds()
            if (shaderBounds != packedBounds) {
                shaderBounds = packedBounds

                setBoundsInternal(left, top, right, bottom)
            }
        }
    }

    private fun setBoundsInternal(left: Float, top: Float, right: Float, bottom: Float) {
        when (type) {
            TYPE_LINEAR_GRADIENT -> {
                shader = LinearGradient(
                    left, top, right, bottom,
                    gradientColors!!, gradientPositions!!,
                    Shader.TileMode.REPEAT
                )
            }
            TYPE_RADIAL_GRADIENT -> {
                val width = right - left
                val height = bottom - top

                val halfWidth = width * 0.5f
                val halfHeight = height * 0.5f

                val cx = left + halfWidth
                val cy = top + halfHeight

                val radius = if (width == height) {
                    halfWidth
                } else {
                    sqrt(halfWidth * halfWidth + halfHeight * halfHeight)
                }

                shader = RadialGradient(
                    cx, cy, radius,
                    gradientColors!!, gradientPositions!!,
                    Shader.TileMode.REPEAT
                )
            }
        }
    }

    internal fun applyToPaint(paint: Paint) {
        paint.style = Paint.Style.FILL

        if(type == TYPE_SOLID) {
            paint.color = color
            paint.shader = null
        } else {
            paint.shader = shader
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null || other.javaClass != javaClass) return false

        other as Fill

        return type == other.type &&
                color == other.color &&
                gradientColors.contentEquals(other.gradientColors) &&
                gradientPositions.contentEquals(other.gradientPositions)
    }

    override fun hashCode(): Int {
        var result = type
        result = result * 31 + color
        result = result * 31 + gradientColors.contentHashCode()
        result = result * 31 + gradientPositions.contentHashCode()

        return result
    }

    override fun toString(): String = when(type) {
        TYPE_SOLID ->
            "{ type = SOLID, color = #${color.toString(16)}}"
        TYPE_LINEAR_GRADIENT, TYPE_RADIAL_GRADIENT ->
            "{ type = ${if(type == TYPE_LINEAR_GRADIENT) "LINEAR_GRADIENT" else "RADIAL_GRADIENT"}, colors=${colorsToString(gradientColors!!)}}, positions=${gradientPositions.contentToString()} }"
        else -> ""
    }

    companion object {
        private val ZERO_ONE_ARRAY = floatArrayOf(0f, 1f)

        private const val TYPE_SOLID = 0
        private const val TYPE_LINEAR_GRADIENT = 1
        private const val TYPE_RADIAL_GRADIENT = 2

        private fun colorsToString(colors: IntArray): String {
            return buildString {
                append('[')
                colors.forEachIndexed { index, color ->
                    append('#')
                    append(color.toString(16))

                    if(index < colors.size - 1) {
                        append(", ")
                    }
                }
                append(']')
            }
        }

        /**
         * Creates a solid fill.
         *
         * @param color color of fill.
         */
        fun solid(@ColorInt color: Int): Fill {
            return Fill(
                type = TYPE_SOLID,
                color = color
            )
        }

        /**
         * Creates a linear gradient fill.
         *
         * @param startColor start color of gradient.
         * @param endColor end color of gradient.
         */
        fun linearGradient(@ColorInt startColor: Int, @ColorInt endColor: Int): Fill {
            return Fill(
                type = TYPE_LINEAR_GRADIENT,
                gradientColors = intArrayOf(startColor, endColor),
                gradientPositions = ZERO_ONE_ARRAY
            )
        }

        /**
         * Creates a linear gradient fill.
         *
         * @param colors colors of gradient
         * @param positions relative positions of each color, each element should be in range `[0; 1]`
         */
        fun linearGradient(colors: IntArray, positions: FloatArray): Fill {
            return Fill(
                type = TYPE_LINEAR_GRADIENT,
                gradientColors = colors,
                gradientPositions = positions
            )
        }

        /**
         * Creates a radial gradient fill.
         *
         * @param startColor start color of gradient.
         * @param endColor end color of gradient.
         */
        fun radialGradient(@ColorInt startColor: Int, @ColorInt endColor: Int): Fill {
            return Fill(
                type = TYPE_RADIAL_GRADIENT,
                gradientColors = intArrayOf(startColor, endColor),
                gradientPositions = ZERO_ONE_ARRAY
            )
        }

        /**
         * Creates a radial gradient fill.
         *
         * @param colors colors of gradient
         * @param positions relative positions of each color, each element should be in range `[0; 1]`
         */
        fun radialGradient(colors: IntArray, positions: FloatArray): Fill {
            return Fill(
                type = TYPE_RADIAL_GRADIENT,
                gradientColors = colors,
                gradientPositions = positions
            )
        }
    }
}