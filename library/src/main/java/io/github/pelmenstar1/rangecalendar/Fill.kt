package io.github.pelmenstar1.rangecalendar

import android.graphics.*
import androidx.annotation.ColorInt
import kotlin.math.sqrt

class Fill(
    private val type: Int,
    private val color: Int = 0,
    private val gradientColors: IntArray? = null,
    private val gradientPositions: FloatArray? = null
) {
    private var shader: Shader? = null

    fun setBounds(bounds: RectF) {
        setBounds(PackedRectF(bounds))
    }

    internal fun setBounds(bounds: PackedRectF) {
        val left = bounds.left
        val top = bounds.top
        val right = bounds.right
        val bottom = bounds.bottom

        when(type) {
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

                val radius = if(width == height) {
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

        fun solid(@ColorInt color: Int): Fill {
            return Fill(
                type = TYPE_SOLID,
                color = color
            )
        }

        fun linearGradient(@ColorInt startColor: Int, @ColorInt endColor: Int): Fill {
            return Fill(
                type = TYPE_LINEAR_GRADIENT,
                gradientColors = intArrayOf(startColor, endColor),
                gradientPositions = ZERO_ONE_ARRAY
            )
        }

        fun linearGradient(colors: IntArray, positions: FloatArray): Fill {
            return Fill(
                type = TYPE_LINEAR_GRADIENT,
                gradientColors = colors,
                gradientPositions = positions
            )
        }

        fun radialGradient(@ColorInt startColor: Int, @ColorInt endColor: Int): Fill {
            return Fill(
                type = TYPE_RADIAL_GRADIENT,
                gradientColors = intArrayOf(startColor, endColor),
                gradientPositions = ZERO_ONE_ARRAY
            )
        }

        fun radialGradient(colors: IntArray, positions: FloatArray): Fill {
            return Fill(
                type = TYPE_RADIAL_GRADIENT,
                gradientColors = colors,
                gradientPositions = positions
            )
        }
    }
}