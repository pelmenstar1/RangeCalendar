package com.github.pelmenstar1.rangecalendar

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import androidx.annotation.ColorInt
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.component3
import androidx.core.graphics.component4
import com.github.pelmenstar1.rangecalendar.utils.appendColor
import com.github.pelmenstar1.rangecalendar.utils.appendColors
import com.github.pelmenstar1.rangecalendar.utils.getLazyValue
import com.github.pelmenstar1.rangecalendar.utils.withCombinedAlpha
import kotlin.math.abs

/**
 * Represents either a solid fill or gradient fill.
 *
 * This class is declared as `sealed` which means classes outside the library module cannot extend [Fill].
 * To create an instance of [Fill], use [Fill.solid], [Fill.linearGradient], [Fill.radialGradient] methods.
 *
 * **Comparison and representation**
 *
 * Gradient positions `null` and `[0, 1]` are visually the same and Fill's logic makes it same too.
 * It impacts [equals], [hashCode], [toString] that return the same results on [Fill] instances with same properties, except gradient positions.
 *
 * Example:
 * ```
 * val f1 = Fill.radialGradient(intArrayOf(1, 2), floatArrayOf(0, 1))
 * val f2 = Fill.radialGradient(intArrayOf(1, 2), null)
 *
 * val isEqual = f1 == f2 // true
 * ```
 */
class Fill private constructor(
    val type: Int,

    private val solidColor: Int,
    private val gradientColors: IntArray?,
    private val gradientPositions: FloatArray?,
    private val orientation: Orientation
) {
    /**
     * Represents orientations for gradients.
     */
    enum class Orientation {
        /**
         * From top to bottom.
         */
        TOP_BOTTOM,

        /**
         * From bottom to top.
         */
        BOTTOM_TOP,

        /**
         * From left to right.
         */
        LEFT_RIGHT,

        /**
         * From right to left.
         */
        RIGHT_LEFT
    }

    private var shader: Shader? = null

    private val isZeroOnePositions = isZeroOneArray(gradientPositions)

    private var _width = 0f
    private var _height = 0f

    val width: Float
        get() = _width

    val height: Float
        get() = _height

    var shape: Shape = RectangleShape
        set(value) {
            if (field != value) {
                field = value

                updateShader()
            }
        }

    private fun createShader(): Shader {
        val tempBox = getTempRect().apply {
            right = _width
            bottom = _height
        }

        val gradColors = gradientColors!!

        return when (type) {
            TYPE_LINEAR_GRADIENT -> {
                shape.narrowBox(tempBox)

                createLinearShader(orientation, tempBox) { x0, y0, x1, y1 ->
                    // Prior to API 29 there was an optimization around creating a shader
                    // that allows to create the shader with two colors, that are distributed along
                    // the gradient line, more efficiently
                    // by using LinearGradient(x0, y0, x1, y1, color0, color1, Shader.TileMode) constructor
                    if (Build.VERSION.SDK_INT < 29 && isZeroOnePositions) {
                        LinearGradient(
                            x0, y0, x1, y1,
                            gradColors[0], gradColors[1],
                            Shader.TileMode.MIRROR
                        )
                    } else {
                        LinearGradient(
                            x0, y0, x1, y1,
                            gradColors, gradientPositions,
                            Shader.TileMode.MIRROR
                        )
                    }
                }
            }

            TYPE_RADIAL_GRADIENT -> {
                val tempPoint = getTempPoint()
                val radius = shape.computeCircumcircle(tempBox, tempPoint)
                val (cx, cy) = tempPoint

                // Same motivation as in linear gradients.
                if (Build.VERSION.SDK_INT < 29 && isZeroOnePositions) {
                    RadialGradient(
                        cx, cy, radius,
                        gradColors[0], gradColors[1],
                        Shader.TileMode.MIRROR
                    )
                } else {
                    RadialGradient(
                        cx, cy, radius,
                        gradColors, gradientPositions,
                        Shader.TileMode.MIRROR
                    )
                }
            }

            else -> throw RuntimeException("Invalid type of Fill")
        }
    }

    fun setSize(width: Float, height: Float) {
        val oldWidth = _width
        val oldHeight = _height

        _width = width
        _height = height

        if (type != TYPE_SOLID) {
            val needToUpdateShader = when (orientation) {
                Orientation.LEFT_RIGHT, Orientation.RIGHT_LEFT -> {
                    abs(oldWidth - width) >= EPSILON
                }

                Orientation.TOP_BOTTOM, Orientation.BOTTOM_TOP -> {
                    abs(oldHeight - height) >= EPSILON
                }
            }

            if (needToUpdateShader) {
                updateShader()
            }
        }
    }

    private fun updateShader() {
        shader = createShader()
    }

    /**
     * Applies options of [Fill] to specified [paint].
     *
     * [alpha] parameter specifies with what `alpha` value solid color should be applied to the [paint]. If the fill is not solid,
     * [alpha] parameter doesn't change anything.
     */
    @JvmOverloads
    fun applyToPaint(paint: Paint, alpha: Float = 1f) {
        paint.style = Paint.Style.FILL

        if (type == TYPE_SOLID) {
            paint.color = solidColor.withCombinedAlpha(alpha)
            paint.shader = null
        } else {
            paint.shader = shader
        }
    }

    /**
     * Initializes [paint] with the current options of [Fill] and specified [alpha], then calls [block] lambda.
     *
     * It should be used to draw something on [canvas] using a [Fill] instance which might be gradient-like.
     * For Kotlin, there's `inline` version of the method which makes it more performant.
     *
     * Implementation notes: internally it uses [Canvas.saveLayerAlpha] to override alpha of the layer.
     * So, [block] lambda **can** create new layers, but it **should** balance them in the end of the [block].
     */
    fun drawWith(canvas: Canvas, shapeBounds: RectF, paint: Paint, alpha: Float, block: Runnable) {
        drawWith(canvas, shapeBounds, paint, alpha) { block.run() }
    }

    /**
     * Initializes [paint] with the current options of [Fill] and specified [alpha], then calls [block] lambda.
     *
     * It should be used to draw something on [canvas] using a [Fill] instance which might be gradient-like.
     *
     * Implementation notes: internally it uses [Canvas.saveLayerAlpha] to override alpha of the layer.
     * So, [block] lambda **can** create new layers, but it **should** balance them in the end of the [block].
     */
    inline fun drawWith(canvas: Canvas, shapeBounds: RectF, paint: Paint, alpha: Float, block: Canvas.() -> Unit) {
        applyToPaint(paint, alpha)

        var count = -1

        // alpha doesn't work in applyToPaint() if fill is not solid. We need
        // to use a layer with different alpha to draw gradient. However, we don't need
        // it in case alpha is 1 (we can draw it as is), or 0 (we don't need to draw anything)
        if (type != TYPE_SOLID && (alpha > 0f && alpha < 1f)) {
            val alpha255 = (alpha * 255f + 0.5f).toInt()

            count = if (Build.VERSION.SDK_INT >= 21) {
                canvas.saveLayerAlpha(shapeBounds, alpha255)
            } else {
                paint.alpha = alpha255

                // Use deprecated method because it's the only available method when API < 21
                @Suppress("DEPRECATION")
                canvas.saveLayer(shapeBounds, paint, Canvas.ALL_SAVE_FLAG)
            }
        }

        try {
            if (alpha > 0) {
                canvas.block()
            }
        } finally {
            if (count >= 0) {
                canvas.restoreToCount(count)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null || javaClass != other.javaClass) return false

        other as Fill

        if (type != other.type) return false

        val gradColors = gradientColors

        // gradColors == null means that the fill is solid
        return if (gradColors == null) {
            solidColor == other.solidColor
        } else {
            orientation == other.orientation &&
                    gradientPositions.contentEquals(other.gradientPositions) &&
                    gradColors.contentEquals(other.gradientColors)

        }
    }

    override fun hashCode(): Int {
        var result = type

        val gradColors = gradientColors

        // gradColors == null means that the fill is solid.
        if (gradColors == null) {
            result = result * 31 + solidColor
        } else {
            result = result * 31 + gradColors.contentHashCode()
            result = result * 31 + gradientPositions.contentHashCode()
            result = result * 31 + orientation.ordinal
        }

        return result
    }

    override fun toString(): String {
        val type = type

        return buildString(64) {
            append("Fill(type=")

            val typeStr = when (type) {
                TYPE_SOLID -> "SOLID"
                TYPE_LINEAR_GRADIENT -> "LINEAR_GRADIENT"
                TYPE_RADIAL_GRADIENT -> "RADIAL_GRADIENT"
                else -> throw RuntimeException("Invalid type of Fill")
            }

            append(typeStr)

            val gradColors = gradientColors

            // gradColors == null means that the fill is solid
            if (gradColors == null) {
                append(", color=")
                appendColor(solidColor)
            } else {
                append(", colors=")
                appendColors(gradColors)

                append(", positions=")
                append(gradientPositions!!.contentToString())

                if (type == TYPE_LINEAR_GRADIENT) {
                    append(", orientation=")
                    append(orientation.name)
                }
            }

            append(')')
        }
    }

    companion object {
        private const val EPSILON = 0.1f

        const val TYPE_SOLID = 0
        const val TYPE_LINEAR_GRADIENT = 1
        const val TYPE_RADIAL_GRADIENT = 2

        private val zeroOneArray = floatArrayOf(0f, 1f)

        private var tempRectHolder: RectF? = null
        private var tempPointHolder: PointF? = null

        @PublishedApi
        @JvmSynthetic
        internal fun getTempRect(): RectF =
            getLazyValue(tempRectHolder, ::RectF) { tempRectHolder = it }

        private fun getTempPoint(): PointF =
            getLazyValue(tempPointHolder, ::PointF) { tempPointHolder = it }

        private fun isZeroOneArray(arr: FloatArray?) =
            arr != null && (arr.size == 2 && arr[0] == 0f && arr[1] == 1f)

        /**
         * Creates a solid fill.
         *
         * @param color color of fill.
         */
        @JvmStatic
        fun solid(@ColorInt color: Int) = Fill(
            type = TYPE_SOLID,
            solidColor = color,
            gradientColors = null,
            gradientPositions = null,
            orientation = Orientation.LEFT_RIGHT
        )

        // Places bounds' components in appropriate order to achieve the desired effect of using Orientation.
        private inline fun <T : Shader> createLinearShader(
            orientation: Orientation,
            bounds: RectF,
            create: (x0: Float, y0: Float, x1: Float, y1: Float) -> T
        ): T {
            val (left, top, right, bottom) = bounds

            val x0: Float
            val y0: Float
            val x1: Float
            val y1: Float

            when (orientation) {
                Orientation.LEFT_RIGHT -> {
                    x0 = left; y0 = top
                    x1 = right; y1 = top
                }

                Orientation.RIGHT_LEFT -> {
                    x0 = right; y0 = top
                    x1 = left; y1 = top
                }

                Orientation.TOP_BOTTOM -> {
                    x0 = left; y0 = top
                    x1 = left; y1 = bottom
                }

                Orientation.BOTTOM_TOP -> {
                    x0 = left; y0 = bottom
                    x1 = left; y1 = top
                }
            }

            return create(x0, y0, x1, y1)
        }

        private fun createGradient(
            type: Int,
            @ColorInt startColor: Int,
            @ColorInt endColor: Int,
            orientation: Orientation,
        ): Fill {
            return Fill(
                type,
                solidColor = 0,
                gradientColors = intArrayOf(startColor, endColor),
                gradientPositions = zeroOneArray,
                orientation = orientation
            )
        }

        private fun createGradient(
            type: Int,
            colors: IntArray,
            positions: FloatArray?,
            orientation: Orientation
        ): Fill {
            if (positions != null && colors.size != positions.size) {
                throw IllegalArgumentException("colors and positions must have equal length")
            }

            if (colors.size < 2) {
                throw IllegalArgumentException("colors length < 2")
            }

            return Fill(
                type,
                solidColor = 0,
                gradientColors = colors.copyOf(),
                gradientPositions = positions ?: zeroOneArray,
                orientation = orientation
            )
        }

        /**
         * Creates a linear gradient fill using start and end color specified by color int.
         *
         * @param startColor start color of gradient.
         * @param endColor end color of gradient.
         * @param orientation orientation of gradient.
         */
        @JvmStatic
        fun linearGradient(
            @ColorInt startColor: Int,
            @ColorInt endColor: Int,
            orientation: Orientation
        ): Fill {
            return createGradient(TYPE_LINEAR_GRADIENT, startColor, endColor, orientation)
        }

        /**
         * Creates a linear gradient fill using array of colors specified by color ints and relative positions for colors.
         *
         * @param colors colors of gradient
         * @param positions relative positions of each color, each element should be in range `[0; 1]`
         * @param orientation orientation of gradient.
         */
        @JvmStatic
        fun linearGradient(
            colors: IntArray,
            positions: FloatArray?,
            orientation: Orientation
        ): Fill {
            return createGradient(TYPE_LINEAR_GRADIENT, colors, positions, orientation)
        }

        /**
         * Creates a radial gradient fill using start and end color specified by color int.
         *
         * @param startColor start color of gradient.
         * @param endColor end color of gradient.
         */
        @JvmStatic
        fun radialGradient(@ColorInt startColor: Int, @ColorInt endColor: Int): Fill {
            return createGradient(
                TYPE_RADIAL_GRADIENT,
                startColor, endColor,
                Orientation.LEFT_RIGHT
            )
        }

        /**
         * Creates a radial gradient fill.
         *
         * @param colors colors of gradient
         * @param positions relative positions of each color, each element should be in range `[0; 1]`
         */
        @JvmStatic
        fun radialGradient(colors: IntArray, positions: FloatArray?): Fill {
            return createGradient(TYPE_RADIAL_GRADIENT, colors, positions, Orientation.LEFT_RIGHT)
        }
    }
}