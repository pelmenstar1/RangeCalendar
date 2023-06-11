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
import com.github.pelmenstar1.rangecalendar.utils.appendColors
import com.github.pelmenstar1.rangecalendar.utils.equalsWithPrecision
import com.github.pelmenstar1.rangecalendar.utils.getLazyValue
import com.github.pelmenstar1.rangecalendar.utils.withAlpha
import com.github.pelmenstar1.rangecalendar.utils.withCombinedAlpha

/**
 * Represents either a solid fill or gradient fill.
 *
 * This class is declared as `sealed` which means classes outside the library module cannot extend [Fill].
 * To create an instance of [Fill], use [Fill.solid], [Fill.linearGradient], [Fill.radialGradient] methods.
 */
sealed class Fill(val type: Int) {
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

    private class Solid(private val color: Int) : Fill(TYPE_SOLID) {
        override fun applyToPaint(paint: Paint, alpha: Float) {
            paint.style = Paint.Style.FILL
            paint.color = color.withCombinedAlpha(alpha)
            paint.shader = null
        }

        override fun onBoundsChanged(
            oldLeft: Float, oldTop: Float, oldRight: Float, oldBottom: Float,
            newLeft: Float, newTop: Float, newRight: Float, newBottom: Float,
            shape: Shape
        ) {
            // No-op
        }

        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other == null || javaClass != other.javaClass) return false

            other as Solid

            return color == other.color
        }

        override fun hashCode(): Int = color

        override fun toString(): String {
            return String.format("Fill(type=SOLID, color=#%08X)", color)
        }
    }

    internal class Gradient(
        type: Int,

        // Stores whether the total number of colors is 2 and gradientPositions equals to null or [0, 1]
        @JvmField
        internal val isTwoColors: Boolean,
        private val gradientColors: IntArray,
        private val gradientPositions: FloatArray?,
        private val orientation: Orientation
    ) : Fill(type) {
        private var shaderAlpha = 1f

        // Initialized when needed.
        private var originGradientColorsAlphas: ByteArray? = null

        private var shader: Shader? = null

        fun createShader(alpha: Float): Shader {
            val shape = boundsShape
                ?: throw IllegalStateException("setBounds() must be called before calling applyToPaint()")

            val tempBox = getTempBox()
            getBounds(tempBox)

            val gradColors = gradientColors

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
                        if (Build.VERSION.SDK_INT < 29 && isTwoColors) {
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
                    if (Build.VERSION.SDK_INT < 29 && isTwoColors) {
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

        override fun applyToPaint(paint: Paint, alpha: Float) {
            paint.style = Paint.Style.FILL

            if (shaderAlpha != alpha) {
                shader = createShader(alpha)
            }

            paint.shader = shader
        }

        override fun onBoundsChanged(
            oldLeft: Float, oldTop: Float, oldRight: Float, oldBottom: Float,
            newLeft: Float, newTop: Float, newRight: Float, newBottom: Float,
            shape: Shape
        ) {
            val shouldUpdateShader = shouldUpdateShaderOnBoundsChange(
                oldLeft, oldTop, oldRight, oldBottom,
                newLeft, newTop, newRight, newBottom
            )

            if (shouldUpdateShader) {
                shader = createShader(alpha = 1f)
            }
        }

        private fun shouldUpdateShaderOnBoundsChange(
            oldLeft: Float, oldTop: Float, oldRight: Float, oldBottom: Float,
            newLeft: Float, newTop: Float, newRight: Float, newBottom: Float
        ): Boolean {
            val oldComp1: Float
            val newComp1: Float
            val oldComp2: Float
            val newComp2: Float

            when (orientation) {
                Orientation.LEFT_RIGHT, Orientation.RIGHT_LEFT -> {
                    oldComp1 = oldLeft; newComp1 = newLeft
                    oldComp2 = oldRight; newComp2 = newRight
                }

                Orientation.TOP_BOTTOM, Orientation.BOTTOM_TOP -> {
                    oldComp1 = oldTop; newComp1 = newTop
                    oldComp2 = oldBottom; newComp2 = newBottom
                }
            }

            return !oldComp1.equalsWithPrecision(newComp1, EPSILON) ||
                    !oldComp2.equalsWithPrecision(newComp2, EPSILON)
        }

        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other == null || javaClass != other.javaClass) return false

            other as Gradient

            if (type != other.type) return false

            if (!colorsEquals(
                    gradientColors, originGradientColorsAlphas,
                    other.gradientColors, other.originGradientColorsAlphas
                )
            ) return false

            if (!gradientPositions.contentEquals(other.gradientPositions)) return false
            if (orientation != other.orientation) return false

            return true
        }

        override fun hashCode(): Int {
            var result = type
            result = 31 * result + colorsHashCode(gradientColors, originGradientColorsAlphas)
            result = 31 * result + gradientPositions.contentHashCode()

            return result
        }

        override fun toString(): String {
            val type = type

            return buildString(64) {
                append("Fill(type=")

                if (type == TYPE_LINEAR_GRADIENT) {
                    append("LINEAR_GRADIENT")
                } else {
                    append("RADIAL_GRADIENT")
                }

                append(", colors=")
                appendColors(gradientColors, originGradientColorsAlphas)

                gradientPositions?.also {
                    append(", positions=")
                    append(it.contentToString())
                }

                if (type == TYPE_LINEAR_GRADIENT) {
                    append(", orientation=")
                    append(orientation.name)
                }

                append(')')
            }
        }
    }

    private var boundsLeft = 0f
    private var boundsTop = 0f
    private var boundsRight = 0f
    private var boundsBottom = 0f

    @JvmField
    protected var boundsShape: Shape? = null

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

    protected abstract fun onBoundsChanged(
        oldLeft: Float, oldTop: Float, oldRight: Float, oldBottom: Float,
        newLeft: Float, newTop: Float, newRight: Float, newBottom: Float,
        shape: Shape
    )

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

        if (oldLeft != left || oldTop != top || oldRight != right || oldBottom != bottom || boundsShape != shape) {
            boundsLeft = left
            boundsTop = top
            boundsRight = right
            boundsBottom = bottom
            boundsShape = shape

            onBoundsChanged(oldLeft, oldTop, oldRight, oldBottom, left, top, right, bottom, shape)
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
    abstract fun applyToPaint(paint: Paint, alpha: Float = 1f)

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

        private fun extractAlphas(colors: IntArray): ByteArray {
            return ByteArray(colors.size) { i -> colors[i].alpha.toByte() }
        }

        private fun checkColorsAndPositions(colors: IntArray, positions: FloatArray?) {
            if (positions != null && colors.size != positions.size) {
                throw IllegalArgumentException("colors and positions must have equal length")
            }

            if (colors.size < 2) {
                throw IllegalArgumentException("colors length <= 2")
            }
        }

        /**
         * Creates a solid fill.
         *
         * @param color color of fill.
         */
        @JvmStatic
        fun solid(@ColorInt color: Int): Fill = Solid(color)

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
            return Gradient(
                type,
                isTwoColors = true,
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
            checkColorsAndPositions(colors, positions)

            // positions' length is also 2, because colors.size == positions.size
            // that was checked by checkColorsAndPositions
            val isTwoColors =
                colors.size == 2 && (positions == null || (positions[0] == 0f && positions[1] == 1f))

            return Gradient(
                type,
                isTwoColors = isTwoColors,
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