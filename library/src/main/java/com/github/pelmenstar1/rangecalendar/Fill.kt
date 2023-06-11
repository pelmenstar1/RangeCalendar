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
import androidx.core.graphics.alpha
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import androidx.core.graphics.component3
import androidx.core.graphics.component4
import com.github.pelmenstar1.rangecalendar.utils.appendColor
import com.github.pelmenstar1.rangecalendar.utils.appendColors
import com.github.pelmenstar1.rangecalendar.utils.getLazyValue
import com.github.pelmenstar1.rangecalendar.utils.withAlpha
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
 * val f1 = Fill.radialGradient(intArrayOf(1, 2), floatArrayOf(1, 0))
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

    private var boundsLeft = 0f
    private var boundsTop = 0f
    private var boundsRight = 0f
    private var boundsBottom = 0f

    private var boundsShape: Shape? = null

    private var shader: Shader? = null
    private var shaderAlpha = 1f

    // Initialized when needed.
    private var originGradientColorsAlphas: ByteArray? = null

    private val isZeroOnePositions = isZeroOneArray(gradientPositions)

    private fun createShader(alpha: Float): Shader {
        val shape = boundsShape
            ?: throw IllegalStateException("setBounds() must be called before calling applyToPaint()")

        val tempBox = getTempBox()
        getBounds(tempBox)

        val gradColors = gradientColors!!

        if (shaderAlpha != alpha) {
            shaderAlpha = alpha

            val originAlphas = getLazyValue(
                originGradientColorsAlphas,
                { extractAlphas(gradColors) }
            ) { originGradientColorsAlphas = it }

            combineAlphas(alpha, originAlphas, gradColors)
        }

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

    fun getBounds(outRect: RectF) {
        outRect.set(boundsLeft, boundsTop, boundsRight, boundsBottom)
    }

    /**
     * Sets bounds in which the fill is applied
     */
    @JvmOverloads
    fun setBounds(bounds: RectF, shape: Shape = RectangleShape) {
        setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom, shape)
    }

    /**
     * Sets bounds in which the fill is applied.
     */
    @JvmOverloads
    fun setBounds(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        shape: Shape = RectangleShape
    ) {
        val oldLeft = boundsLeft
        val oldTop = boundsTop
        val oldRight = boundsRight
        val oldBottom = boundsBottom
        val oldShape = boundsShape

        boundsLeft = left
        boundsTop = top
        boundsRight = right
        boundsBottom = bottom
        boundsShape = shape

        if (type != TYPE_SOLID && (oldLeft != left || oldTop != top || oldRight != right || oldBottom != bottom || oldShape != shape)) {
            val updateShader = when (orientation) {
                Orientation.LEFT_RIGHT, Orientation.RIGHT_LEFT -> {
                    abs(oldLeft - left) >= EPSILON || abs(oldRight - right) >= EPSILON
                }

                Orientation.TOP_BOTTOM, Orientation.BOTTOM_TOP -> {
                    abs(oldTop - top) >= EPSILON || abs(oldBottom - bottom) >= EPSILON
                }
            }

            if (updateShader) {
                shader = createShader(alpha = 1f)
            }
        }
    }

    /**
     * Applies options of [Fill] to specified paint.
     *
     * If [Fill] is gradient-like (created by [linearGradient] or [radialGradient]) and [alpha] is less than 1,
     * this method shouldn't be used to apply the options to the paint and draw something with the paint.
     * For this purpose, there's [drawWith] method. In Kotlin, `inline` version of the method can be used, which
     * makes it more performant than version with [Runnable] lambda.
     */
    @JvmOverloads
    fun applyToPaint(paint: Paint, alpha: Float = 1f) {
        paint.style = Paint.Style.FILL

        if (type == TYPE_SOLID) {
            paint.color = solidColor.withCombinedAlpha(alpha)
            paint.shader = null
        } else {
            var sh = shader
            if (shaderAlpha != alpha) {
                sh = createShader(alpha)
                shader = sh
            }

            paint.shader = sh
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
    fun drawWith(canvas: Canvas, paint: Paint, alpha: Float, block: Runnable) {
        drawWith(canvas, paint, alpha) { block.run() }
    }

    /**
     * Initializes [paint] with the current options of [Fill] and specified [alpha], then calls [block] lambda.
     *
     * It should be used to draw something on [canvas] using a [Fill] instance which might be gradient-like.
     *
     * Implementation notes: internally it uses [Canvas.saveLayerAlpha] to override alpha of the layer.
     * So, [block] lambda **can** create new layers, but it **should** balance them in the end of the [block].
     */
    inline fun drawWith(canvas: Canvas, paint: Paint, alpha: Float, block: Canvas.() -> Unit) {
        if (type == TYPE_SOLID || alpha == 1f) {
            applyToPaint(paint, alpha)

            canvas.block()
        } else {
            val box = getTempBox()
            getBounds(box)

            val alpha255 = (alpha * 255f + 0.5f).toInt()

            val count = if (Build.VERSION.SDK_INT >= 21) {
                canvas.saveLayerAlpha(box, alpha255)
            } else {
                paint.alpha = alpha255

                @Suppress("DEPRECATION")
                canvas.saveLayer(box, paint, Canvas.ALL_SAVE_FLAG)
            }

            try {
                canvas.block()
            } finally {
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
                    positionsEquals(gradientPositions, other.gradientPositions) &&
                    colorsEquals(
                        gradColors, originGradientColorsAlphas,
                        other.gradientColors!!, other.originGradientColorsAlphas
                    )

        }
    }

    override fun hashCode(): Int {
        var result = type

        val gradColors = gradientColors

        // gradColors == null means that the fill is solid.
        if (gradColors == null) {
            result = result * 31 + solidColor
        } else {
            result = result * 31 + colorsHashCode(gradColors, originGradientColorsAlphas)
            result = result * 31 + positionsHashCode(gradientPositions)
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
                appendColors(gradColors, originGradientColorsAlphas)

                append(", positions=")
                append(gradientPositions?.contentToString() ?: "[0.0, 1.0]")

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

        private var tempBoxHolder: RectF? = null
        private var tempPointHolder: PointF? = null

        @PublishedApi
        @JvmSynthetic
        internal fun getTempBox(): RectF =
            getLazyValue(tempBoxHolder, ::RectF) { tempBoxHolder = it }

        private fun getTempPoint(): PointF =
            getLazyValue(tempPointHolder, ::PointF) { tempPointHolder = it }

        private fun combineAlphas(alpha: Float, originAlphas: ByteArray, colors: IntArray) {
            for (i in colors.indices) {
                val originAlpha = originAlphas[i].toInt() and 0xFF

                colors[i] = colors[i].withCombinedAlpha(alpha, originAlpha)
            }
        }

        private fun colorsEquals(
            a: IntArray, aAlphas: ByteArray?,
            b: IntArray, bAlphas: ByteArray?
        ): Boolean {
            // Use optimized Arrays.equals() if both aAlphas and bAlphas are null which
            // means that alphas weren't changed.
            if (aAlphas == null && bAlphas == null) {
                return a.contentEquals(b)
            }

            val size = a.size
            if (size != b.size) return false

            for (i in 0 until size) {
                var aElement = a[i]
                if (aAlphas != null) {
                    aElement = aElement.withAlpha(aAlphas[i])
                }

                var bElement = b[i]
                if (bAlphas != null) {
                    bElement = bElement.withAlpha(bAlphas[i])
                }

                if (aElement != bElement) {
                    return false
                }
            }

            return true
        }

        private fun colorsHashCode(colors: IntArray, originAlphas: ByteArray?): Int {
            // Use optimized Arrays.hashCode() if originAlphas is null which means
            // that alphas of colors weren't changed.
            if (originAlphas == null) {
                return colors.contentHashCode()
            }

            var result = 1

            for (i in colors.indices) {
                val color = colors[i].withAlpha(originAlphas[i])

                result = result * 31 + color
            }

            return result
        }

        private fun isZeroOneArray(arr: FloatArray?) =
            arr == null || (arr.size == 2 && arr[0] == 0f && arr[1] == 1f)

        private fun positionsEquals(a: FloatArray?, b: FloatArray?): Boolean {
            if (a == null) return isZeroOneArray(b)
            if (b == null) return isZeroOneArray(a)

            return a.contentEquals(b)
        }

        private fun positionsHashCode(arr: FloatArray?): Int {
            return if (arr == null || isZeroOneArray(arr)) 0 else arr.contentHashCode()
        }

        private fun extractAlphas(colors: IntArray): ByteArray {
            return ByteArray(colors.size) { i -> colors[i].alpha.toByte() }
        }

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
                gradientPositions = null,
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
                gradientPositions = positions,
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