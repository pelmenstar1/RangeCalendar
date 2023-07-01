package com.github.pelmenstar1.rangecalendar

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.ColorInt
import androidx.core.graphics.component1
import androidx.core.graphics.component2
import com.github.pelmenstar1.rangecalendar.utils.appendColor
import com.github.pelmenstar1.rangecalendar.utils.appendColors
import com.github.pelmenstar1.rangecalendar.utils.ceilToInt
import com.github.pelmenstar1.rangecalendar.utils.getLazyValue
import com.github.pelmenstar1.rangecalendar.utils.withCombinedAlpha

/**
 * Represents either a solid fill or gradient fill.
 *
 * This class is declared as `sealed` which means classes outside the library module cannot extend [Fill].
 * To create an instance of [Fill], use [Fill.solid], [Fill.linearGradient], [Fill.radialGradient], [Fill.shader], [Fill.drawable] methods.
 *
 * **Comparison and representation**
 *
 * Gradient positions `null` and `[0, 1]` are visually the same in [LinearGradient] and [RadialGradient] and Fill's logic makes it same too.
 * It impacts [equals], [hashCode], [toString] that return the same results on [Fill] instances with same properties, except gradient positions.
 *
 * Example:
 * ```
 * val f1 = Fill.radialGradient(intArrayOf(1, 2), floatArrayOf(0f, 1f))
 * val f2 = Fill.radialGradient(intArrayOf(1, 2), null)
 *
 * val isEqual = f1 == f2 // true
 * ```
 */
class Fill private constructor(
    val type: Int,

    private val solidColor: Int = 0,
    private val gradientColors: IntArray? = null,
    private val gradientPositions: FloatArray? = null,
    private val gradientOrientation: Orientation = Orientation.LEFT_RIGHT,

    private val shaderFactory: ShaderFactory? = null,
    val drawable: Drawable? = null,
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

    /**
     * Responsible for creating [Shader] instance.
     */
    fun interface ShaderFactory {
        /**
         * Creates a [Shader] instance. The shader is expected to be created relative to the zero point.
         *
         * @param width current width of the fill instance.
         * @param height current height of the fill instance.
         * @param shape current shape of the fill instance. May be used for more precise shader's properties calculation.
         */
        fun create(width: Float, height: Float, shape: Shape): Shader
    }

    private var shader: Shader? = null

    private var _width = 0f
    private var _height = 0f

    /**
     * Gets width of the size of the shape, fill is used to draw.
     */
    val width: Float
        get() = _width

    /**
     * Gets height of the size of the shape, fill is used to draw.
     */
    val height: Float
        get() = _height

    /**
     * Gets or sets shape, fill is used to draw.
     * If fill is shader-like, shader is updated (re-created) as shader properties depend on type of shape.
     */
    var shape: Shape = RectangleShape
        set(value) {
            if (field != value) {
                field = value

                if (isShaderLike) {
                    updateShader()
                }
            }
        }

    /**
     * Gets whether fill is shader-like, i.e. created by [linearGradient], [radialGradient], [shader].
     */
    val isShaderLike: Boolean
        get() = (type and TYPE_SHADER_LIKE_BIT) != 0

    /**
     * Gets whether fill's type is [TYPE_DRAWABLE].
     */
    val isDrawableType: Boolean
        get() = type == TYPE_DRAWABLE

    private fun createShader(): Shader {
        val width = _width
        val height = _height
        val positions = gradientPositions

        return when (type) {
            TYPE_LINEAR_GRADIENT -> {
                val gradColors = gradientColors!!

                createLinearShader(gradientOrientation, width, height) { x0, y0, x1, y1 ->
                    // Prior to API 29 there was an optimization around creating a shader
                    // that allows to create the shader with two colors, that are distributed along
                    // the gradient line, more efficiently
                    // by using LinearGradient(x0, y0, x1, y1, color0, color1, Shader.TileMode) constructor
                    if (Build.VERSION.SDK_INT < 29 && isZeroOneArray(positions)) {
                        LinearGradient(
                            x0, y0, x1, y1,
                            gradColors[0], gradColors[1],
                            Shader.TileMode.MIRROR
                        )
                    } else {
                        LinearGradient(
                            x0, y0, x1, y1,
                            gradColors, positions,
                            Shader.TileMode.MIRROR
                        )
                    }
                }
            }

            TYPE_RADIAL_GRADIENT -> {
                val gradColors = gradientColors!!

                val tempBox = getTempBox().apply {
                    // left and top are always zero.
                    right = width
                    bottom = height
                }

                val tempPoint = getTempPoint()

                val radius = shape.computeCircumcircle(tempBox, tempPoint)
                val (cx, cy) = tempPoint

                // Same motivation as in linear gradients.
                if (Build.VERSION.SDK_INT < 29 && isZeroOneArray(positions)) {
                    RadialGradient(
                        cx, cy, radius,
                        gradColors[0], gradColors[1],
                        Shader.TileMode.MIRROR
                    )
                } else {
                    RadialGradient(
                        cx, cy, radius,
                        gradColors, positions,
                        Shader.TileMode.MIRROR
                    )
                }
            }

            TYPE_SHADER -> {
                shaderFactory!!.create(width, height, shape)
            }

            else -> throwInvalidType(type)
        }
    }

    /**
     * Sets size of the shape, fill is used to draw.
     *
     * The size only matters when fill is shader-like or drawable.
     * If fill is drawable, the drawable's bounds are set relative to the origin.
     * In other words bounds are `(left=0, top=0, right=width, bottom=height)`
     */
    fun setSize(width: Float, height: Float) {
        val oldWidth = _width
        val oldHeight = _height

        _width = width
        _height = height

        if (oldWidth == width && oldHeight == height) {
            return
        }

        if (isShaderLike) {
            var needToUpdateShader = true

            if (type == TYPE_LINEAR_GRADIENT) {
                needToUpdateShader = when (gradientOrientation) {
                    // If the gradient line is horizontal, it changes visually only when width is changed.
                    Orientation.LEFT_RIGHT, Orientation.RIGHT_LEFT -> oldWidth != width

                    // If the gradient line is vertical, it changes visually only when height is changed.
                    Orientation.TOP_BOTTOM, Orientation.BOTTOM_TOP -> oldHeight != height
                }
            }

            if (needToUpdateShader) {
                updateShader()
            }
        } else if (isDrawableType) {
            drawable!!.setBounds(0, 0, ceilToInt(width), ceilToInt(height))
        }
    }

    private fun updateShader() {
        shader = createShader()
    }

    /**
     * Applies options of [Fill] to specified [paint].
     *
     * If the fill is solid, [alpha] parameter specifies with what `alpha` value solid color is applied to the [paint].
     * Note that alpha of the solid color and alpha of the parameter are combined.
     *
     * If the fill is solid, [alpha] is ignored. Shader is set to the [paint].
     *
     * **The method can't be used for drawable fills**
     */
    @JvmOverloads
    fun applyToPaint(paint: Paint, alpha: Float = 1f) {
        if (isDrawableType) {
            throw IllegalStateException("The method can't be called when Fill's type is TYPE_DRAWABLE")
        }

        paint.style = Paint.Style.FILL

        if (type == TYPE_SOLID) {
            // Combine alphas.
            paint.color = solidColor.withCombinedAlpha(alpha)
        }

        // If shader is null, it means either type is SOLID or type is shader-like but setSize() hasn't been called.
        // In the latter case, it's illegal thing to do. We don't force it though.
        paint.shader = shader
    }

    /**
     * Initializes [paint] with the current options of [Fill] and specified [alpha], then calls [block] lambda.
     *
     * It's indented to be used when a shape is drawn using shader-like fill and alpha can vary.
     * In other words, the shape is not always opaque. It can be used for solid fills but it's same as calling [applyToPaint] with specified alpha and drawing the shape.
     *
     * For Kotlin, there's `inline` version of the method which makes it more performant.
     *
     * **The method can't be used for drawable fills**
     */
    fun drawWith(canvas: Canvas, shapeBounds: RectF, paint: Paint, alpha: Float, block: Runnable) {
        drawWith(canvas, shapeBounds, paint, alpha) { block.run() }
    }

    /**
     * Initializes [paint] with the current options of [Fill] and specified [alpha], then calls [block] lambda.
     *
     * It's indented to be used when a shape is drawn using shader-like fill and alpha can vary.
     * In other words, the shape is not always opaque. It can be used for solid fills but it's same as calling [applyToPaint] with specified alpha and drawing the shape.
     *
     * **The method can't be used for drawable fills**
     */
    inline fun drawWith(canvas: Canvas, shapeBounds: RectF, paint: Paint, alpha: Float, block: Canvas.() -> Unit) {
        // If alpha is 0, there's no sense in drawing. If the value is less than 0, that's illegal
        // and better to return from the method.
        if (alpha <= 0f) {
            return
        }

        // applyToPaint() will check whether type is not TYPE_DRAWABLE.
        applyToPaint(paint, alpha)

        var count = -1

        // alpha doesn't work in applyToPaint() if fill is not solid. We need
        // to use a layer with different alpha to draw a shape using shader. However, we don't need
        // it in case alpha is 1 -- we can draw it as is
        if (isShaderLike && alpha < 1f) {
            // Convert float alpha [0; 1] to int alpha [0; 255]
            val iAlpha = (alpha * 255f + 0.5f).toInt()

            count = if (Build.VERSION.SDK_INT >= 21) {
                canvas.saveLayerAlpha(shapeBounds, iAlpha)
            } else {
                paint.alpha = iAlpha

                // Use deprecated method because it's the only available method when API < 21
                @Suppress("DEPRECATION")
                canvas.saveLayer(shapeBounds, paint, Canvas.ALL_SAVE_FLAG)
            }
        }

        try {
            canvas.block()
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

        return when(type) {
            TYPE_SOLID -> solidColor == other.solidColor
            TYPE_LINEAR_GRADIENT, TYPE_RADIAL_GRADIENT -> {
                gradientOrientation == other.gradientOrientation &&
                        gradientPositions.contentEquals(other.gradientPositions) &&
                        gradientColors.contentEquals(other.gradientColors)
            }
            TYPE_SHADER -> shaderFactory == other.shaderFactory
            TYPE_DRAWABLE -> drawable == other.drawable

            else -> throwInvalidType(type)
        }
    }

    override fun hashCode(): Int {
        return when (type) {
            TYPE_SOLID -> solidColor
            TYPE_LINEAR_GRADIENT, TYPE_RADIAL_GRADIENT -> {
                var result = gradientColors.contentHashCode()
                result = result * 31 + gradientPositions.contentHashCode()
                result = result * 31 + gradientOrientation.ordinal

                result
            }

            TYPE_SHADER -> shaderFactory.hashCode()
            TYPE_DRAWABLE -> drawable.hashCode()
            else -> throwInvalidType(type)
        }
    }

    override fun toString(): String {
        val type = type

        return buildString(64) {
            when (type) {
                TYPE_SOLID -> {
                    append("Fill(type=SOLID, color=")
                    appendColor(solidColor)
                }

                TYPE_LINEAR_GRADIENT, TYPE_RADIAL_GRADIENT -> {
                    append("Fill(type=")
                    append(if (type == TYPE_LINEAR_GRADIENT) "LINEAR" else "RADIAL")
                    append("_GRADIENT, colors=")
                    appendColors(gradientColors!!)
                    append(", positions=")
                    append(gradientPositions.contentToString())

                    if (type == TYPE_LINEAR_GRADIENT) {
                        append(", orientation=")
                        append(gradientOrientation.name)
                    }
                }

                TYPE_SHADER -> {
                    append("Fill(type=SHADER, factory=")
                    append(shaderFactory)
                }

                TYPE_DRAWABLE -> {
                    append("Fill(type=DRAWABLE, drawable=")
                    append(drawable)
                }

                else -> throwInvalidType(type)
            }

            append(')')
        }
    }

    companion object {
        private const val TYPE_SHADER_LIKE_BIT = 1 shl 31

        const val TYPE_SOLID = 0
        const val TYPE_SHADER = TYPE_SHADER_LIKE_BIT
        const val TYPE_LINEAR_GRADIENT = 1 or TYPE_SHADER_LIKE_BIT
        const val TYPE_RADIAL_GRADIENT = 2 or TYPE_SHADER_LIKE_BIT
        const val TYPE_DRAWABLE = 4

        private val zeroOneArray = floatArrayOf(0f, 1f)

        private var tempBoxHolder: RectF? = null
        private var tempPointHolder: PointF? = null

        private fun getTempBox(): RectF =
            getLazyValue(tempBoxHolder, ::RectF) { tempBoxHolder = it }

        private fun getTempPoint(): PointF =
            getLazyValue(tempPointHolder, ::PointF) { tempPointHolder = it }

        private fun isZeroOneArray(arr: FloatArray?) =
            arr != null && (arr.size == 2 && arr[0] == 0f && arr[1] == 1f)

        private fun throwInvalidType(type: Int): Nothing {
            throw RuntimeException("Invalid Fill type ($type)")
        }

        /**
         * Creates a solid fill.
         *
         * @param color color of fill.
         */
        @JvmStatic
        fun solid(@ColorInt color: Int) = Fill(TYPE_SOLID, solidColor = color)

        // Places bounds' components in appropriate order to achieve the desired effect of using Orientation.
        private inline fun createLinearShader(
            orientation: Orientation,
            width: Float,
            height: Float,
            create: (x0: Float, y0: Float, x1: Float, y1: Float) -> LinearGradient
        ): LinearGradient {
            var x0 = 0f
            var y0 = 0f
            var x1 = 0f
            var y1 = 0f

            when (orientation) {
                Orientation.LEFT_RIGHT -> x1 = width
                Orientation.RIGHT_LEFT -> x0 = width
                Orientation.TOP_BOTTOM -> y1 = height
                Orientation.BOTTOM_TOP -> y0 = height
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
                gradientColors = intArrayOf(startColor, endColor),
                gradientPositions = zeroOneArray,
                gradientOrientation = orientation
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
                gradientColors = colors.copyOf(),
                gradientPositions = positions ?: zeroOneArray,
                gradientOrientation = orientation
            )
        }

        /**
         * Creates a linear gradient fill using start and end colors.
         * Two colors are distributed evenly along the gradient line.
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
         * @param positions relative positions of each color, each element should be in range `[0; 1]`.
         * If null, colors are distributed evenly along the gradient line.
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
         * Creates a radial gradient fill using start and end colors.
         * Two colors are distributed evenly along the gradient line.
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
         * @param positions relative positions of each color, each element should be in range `[0; 1]`.
         * If null, colors are distributed evenly along the gradient line.
         */
        @JvmStatic
        fun radialGradient(colors: IntArray, positions: FloatArray?): Fill {
            return createGradient(TYPE_RADIAL_GRADIENT, colors, positions, Orientation.LEFT_RIGHT)
        }

        /**
         * Creates a shader fill.
         *
         * As desired size of gradient may change, single [Shader] instance can't be used.
         * Instead, [ShaderFactory] is used that creates new instances of [Shader] when the size is changed.
         */
        @JvmStatic
        fun shader(factory: ShaderFactory): Fill {
            return Fill(TYPE_SHADER, shaderFactory = factory)
        }

        /**
         * Creates a fill based on [Drawable] instance.
         *
         * When a shape needs to be drawn using this fill, a [Drawable] is simply drawn in desired bounds,
         * but only intersection with the shape is visible.
         *
         * If you want a solid fill or a shader fill (gradient fills are also shader-like),
         * use [solid], [shader], [linearGradient], [radialGradient].
         * Although these fills can be replicated using this method,
         * using fills created by the specialized methods is more performant as their properties are known
         * unlike [Drawable] where the way it's drawn is left to the implementation.
         */
        @JvmStatic
        fun drawable(value: Drawable): Fill {
            return Fill(TYPE_DRAWABLE, drawable = value)
        }
    }
}