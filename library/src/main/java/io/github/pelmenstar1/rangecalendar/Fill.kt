package io.github.pelmenstar1.rangecalendar

import android.graphics.*
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.ColorLong
import androidx.annotation.RequiresApi
import androidx.core.graphics.*
import io.github.pelmenstar1.rangecalendar.utils.asSrgbUnsafe
import io.github.pelmenstar1.rangecalendar.utils.getLazyValue
import io.github.pelmenstar1.rangecalendar.utils.withAlpha
import io.github.pelmenstar1.rangecalendar.utils.withCombinedAlpha

/**
 * Represents either a solid fill or gradient fill.
 *
 * This class is declared as `sealed` which means classes outside the library module cannot extend [Fill].
 * To create an instance of [Fill], use [Fill.solid], [Fill.linearGradient], [Fill.radialGradient] methods.
 */
sealed class Fill(val type: Int) {
    private class Pre29(
        type: Int,
        private val color: Int = 0,

        // gradientColor1, gradientColor2 is only valid if gradientColors is null.
        // This tuple is used instead of int array only before API 29, because Linear-,RadialGradient
        // had special handling for start and end color constructor without positions.
        // We can save an array allocation.
        private var gradientColor1: Int = 0,
        private var gradientColor2: Int = 0,
        private val gradientColors: IntArray? = null,

        // it's only valid if gradientColors is not null
        private val gradientPositions: FloatArray? = null
    ) : Fill(type) {
        private var shaderAlpha: Float = 1f

        // These variables can be initialized on-place, there's no need for lazy-init
        // as in originGradientColorsAlphas case.
        private val originGradientColor1Alpha = gradientColor1.alpha
        private val originGradientColor2Alpha = gradientColor2.alpha

        private var originGradientColorsAlphas: ByteArray? = null

        private fun originGradientColor1() = gradientColor1.withAlpha(originGradientColor1Alpha)
        private fun originGradientColor2() = gradientColor2.withAlpha(originGradientColor2Alpha)

        // make it inline to possibly remove check on assert call (!!)
        @Suppress("NOTHING_TO_INLINE")
        private inline fun getOriginGradientColor(index: Int): Int {
            return gradientColors!![index].withAlpha(originGradientColorsAlphas!![index])
        }

        override fun createShader(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            shape: Shape,
            alpha: Float
        ): Shader {
            val tempBox = getTempBox()
            tempBox.set(left, top, right, bottom)

            if (shaderAlpha != alpha) {
                shaderAlpha = alpha

                if (gradientColors != null) {
                    var originAlphas = originGradientColorsAlphas
                    if (originAlphas == null) {
                        originAlphas = extractAlphas(gradientColors)
                        originGradientColorsAlphas = originAlphas
                    }

                    combineAlphas(alpha, originAlphas, gradientColors)
                } else {
                    gradientColor1 =
                        gradientColor1.withCombinedAlpha(alpha, originGradientColor1Alpha)
                    gradientColor2 =
                        gradientColor2.withCombinedAlpha(alpha, originGradientColor2Alpha)
                }
            }

            return when (type) {
                TYPE_LINEAR_GRADIENT -> {
                    shape.narrowBox(tempBox)

                    if (gradientColors != null) {
                        LinearGradient(
                            tempBox.left, tempBox.top, tempBox.right, tempBox.bottom,
                            gradientColors, gradientPositions,
                            Shader.TileMode.MIRROR
                        )
                    } else {
                        LinearGradient(
                            tempBox.left, tempBox.top, tempBox.right, tempBox.bottom,
                            gradientColor1, gradientColor2,
                            Shader.TileMode.MIRROR
                        )
                    }
                }
                TYPE_RADIAL_GRADIENT -> {
                    val tempPoint = getTempPoint()
                    val radius = shape.computeCircumcircle(tempBox, tempPoint)

                    if (gradientColors != null) {
                        RadialGradient(
                            tempPoint.x, tempPoint.y,
                            radius,
                            gradientColors, gradientPositions,
                            Shader.TileMode.MIRROR
                        )
                    } else {
                        RadialGradient(
                            tempPoint.x, tempPoint.y,
                            radius,
                            gradientColor1, gradientColor2,
                            Shader.TileMode.MIRROR
                        )
                    }
                }
                else -> throw IllegalArgumentException("type is not gradient")
            }
        }


        override fun applyToPaint(paint: Paint, alpha: Float) {
            paint.style = Paint.Style.FILL

            if (type == TYPE_SOLID) {
                paint.color = color.withCombinedAlpha(alpha)
                paint.shader = null
            } else {
                if (shaderAlpha != alpha) {
                    val (left, top, right, bottom) = shaderBounds

                    shader = createShader(left, top, right, bottom, shaderShape!!, alpha)
                }

                paint.shader = shader
            }
        }

        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other == null || javaClass != other.javaClass) return false

            other as Pre29

            if (!super.equals(other)) return false
            if (color != other.color) return false

            if (gradientColors != null) {
                if (other.gradientColors != null) {
                    if (!colorsContentEquals(
                            gradientColors,
                            originGradientColorsAlphas,
                            other.gradientColors,
                            other.originGradientColorsAlphas
                        )
                    ) return false
                    if (!gradientPositions.contentEquals(other.gradientPositions)) return false
                } else {
                    if (getOriginGradientColor(0) != other.originGradientColor1()) return false
                    if (getOriginGradientColor(1) != other.originGradientColor2()) return false
                }
            } else {
                if (other.gradientColors != null) {
                    if (originGradientColor1() != other.getOriginGradientColor(0)) return false
                    if (originGradientColor2() != other.getOriginGradientColor(1)) return false
                } else {
                    if (originGradientColor1() != other.originGradientColor1()) return false
                    if (originGradientColor2() != other.originGradientColor2()) return false
                }
            }

            return true
        }

        override fun hashCode(): Int {
            var result = super.hashCode()

            if (type == TYPE_SOLID) {
                result = 31 * result + color
            } else {
                if (gradientColors != null) {
                    result = 31 * result + gradientColors.contentHashCode()
                    result = 31 * result + gradientPositions.contentHashCode()
                } else {
                    result = 31 * result + gradientColor1
                    result = 31 * result + gradientColor2
                }
            }

            return result
        }

        override fun toString(): String {
            return when (type) {
                TYPE_SOLID -> "Fill(type=SOLID, color=#${color.toString(16)}"
                TYPE_LINEAR_GRADIENT, TYPE_RADIAL_GRADIENT -> if (gradientColors != null) {
                    "Fill(type=${gradientTypeToString(type)}, colors=${colorsToString(gradientColors)}, positions=${gradientPositions!!.contentToString()})"
                } else {
                    "Fill(type==${gradientTypeToString(type)}, colors=[${gradientColor1.toString(16)}, ${
                        gradientColor2.toString(
                            16
                        )
                    }], positions=[0, 1]"
                }
                else -> throw RuntimeException("Invalid type of Fill")
            }
        }
    }

    @RequiresApi(29)
    private class Post29(
        type: Int,

        // Always sRGB color space
        private val color: Long = 0,

        // Always sRGB color space
        private val gradientColors: LongArray? = null,
        private val gradientPositions: FloatArray? = null
    ) : Fill(type) {
        private var shaderAlpha: Float = 1f
        private var originGradientColorsAlphas: ByteArray? = null

        override fun createShader(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            shape: Shape,
            alpha: Float
        ): Shader {
            val tempBox = getTempBox()

            tempBox.set(left, top, right, bottom)

            gradientColors!!
            gradientPositions!!

            if (shaderAlpha != alpha) {
                var originAlphas = originGradientColorsAlphas
                if (originAlphas == null) {
                    originAlphas = extractAlphasSrgb(gradientColors)
                    originGradientColorsAlphas = originAlphas
                }

                shaderAlpha = alpha

                combineAlphas(alpha, originAlphas, gradientColors)
            }

            return when (type) {
                TYPE_LINEAR_GRADIENT -> {
                    shape.narrowBox(tempBox)

                    LinearGradient(
                        tempBox.left, tempBox.top, tempBox.right, tempBox.bottom,
                        gradientColors, gradientPositions,
                        Shader.TileMode.MIRROR
                    )
                }
                TYPE_RADIAL_GRADIENT -> {
                    val tempPoint = getTempPoint()

                    val radius = shape.computeCircumcircle(tempBox, tempPoint)

                    RadialGradient(
                        tempPoint.x, tempPoint.y,
                        radius,
                        gradientColors, gradientPositions,
                        Shader.TileMode.MIRROR
                    )
                }
                else -> throw IllegalArgumentException("type is not gradient")
            }
        }

        override fun applyToPaint(paint: Paint, alpha: Float) {
            paint.style = Paint.Style.FILL

            if (type == TYPE_SOLID) {
                paint.color = color.asSrgbUnsafe().withCombinedAlpha(alpha)

                paint.shader = null
            } else {
                if (shaderAlpha != alpha) {
                    val (left, top, right, bottom) = shaderBounds

                    shader = createShader(left, top, right, bottom, shaderShape!!, alpha)
                }

                paint.shader = shader
            }
        }

        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other == null || javaClass != other.javaClass) return false

            other as Post29

            if (!super.equals(other)) return false
            if (color != other.color) return false

            if (type != TYPE_SOLID) {
                if (!colorsContentEquals(
                        gradientColors!!, originGradientColorsAlphas,
                        other.gradientColors!!, other.originGradientColorsAlphas
                    )
                ) return false
                if (!gradientPositions.contentEquals(other.gradientPositions)) return false
            }

            return true
        }

        override fun hashCode(): Int {
            var result = super.hashCode()

            if (type == TYPE_SOLID) {
                result = 31 * result + java.lang.Long.hashCode(color)
            } else {
                result = 31 * result + gradientColors.contentHashCode()
                result = 31 * result + gradientPositions.contentHashCode()
            }

            return result
        }

        override fun toString(): String {
            return when (type) {
                TYPE_SOLID -> "Fill(type=SOLID, color=#${color.toString(16)}"
                TYPE_LINEAR_GRADIENT, TYPE_RADIAL_GRADIENT ->
                    "Fill(type=${gradientTypeToString(type)}, colors=${colorsToString(gradientColors!!)}, positions=${gradientPositions!!.contentToString()})"
                else -> throw RuntimeException("Invalid type of Fill")
            }
        }
    }

    protected var shader: Shader? = null

    internal var shaderBounds = PackedRectF(0)
    protected var shaderShape: Shape? = null

    fun getBounds(outRect: RectF) {
        shaderBounds.setTo(outRect)
    }

    /**
     * Sets bounds in which the fill is applied
     */
    @JvmOverloads
    fun setBounds(bounds: RectF, shape: Shape = RectangleShape) {
        setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom, shape)
    }

    /**
     * Sets bounds in which the fill is applied
     */
    @JvmOverloads
    fun setBounds(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        shape: Shape = RectangleShape
    ) {
        setBounds(left, top, right, bottom, shape) { PackedRectF(left, top, right, bottom) }
    }

    internal fun setBounds(bounds: PackedRectF, shape: Shape) {
        setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom, shape) { bounds }
    }

    internal inline fun setBounds(
        left: Float, top: Float, right: Float, bottom: Float, shape: Shape,
        computePackedBounds: () -> PackedRectF
    ) {
        if (type != TYPE_SOLID) {
            val packedBounds = computePackedBounds()
            if (shaderBounds != packedBounds) {
                shaderBounds = packedBounds

                setBoundsInternal(left, top, right, bottom, shape)
            }
        }
    }

    private fun setBoundsInternal(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        shape: Shape
    ) {
        shaderShape = shape

        shader = createShader(left, top, right, bottom, shape, 1f)
    }

    protected abstract fun createShader(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        shape: Shape,
        alpha: Float
    ): Shader

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
     * If Kotlin is used, there's `inline` version of the method which makes it more performant.
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
        if(Build.VERSION.SDK_INT < 21 || type == TYPE_SOLID) {
            applyToPaint(paint, alpha)

            canvas.block()
        } else {
            applyToPaint(paint, alpha = 1f)

            if(alpha < 1f) {
                val box = getTempBox()
                getBounds(box)

                val alpha255 = (alpha * 255f + 0.5f).toInt()
                val count = canvas.saveLayerAlpha(box, alpha255)

                try {
                    canvas.block()
                } finally {
                    canvas.restoreToCount(count)
                }
            } else {
                canvas.block()
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null || other.javaClass != javaClass) return false

        other as Fill

        return type == other.type
    }

    override fun hashCode(): Int {
        return type
    }

    companion object {
        const val TYPE_SOLID = 0
        const val TYPE_LINEAR_GRADIENT = 1
        const val TYPE_RADIAL_GRADIENT = 2

        private var zeroOneArrayHolder: FloatArray? = null
        private var tempBoxHolder: RectF? = null
        private var tempPointHolder: PointF? = null

        @PublishedApi
        @JvmSynthetic
        internal fun getTempBox(): RectF {
            return getLazyValue(tempBoxHolder, { RectF() }, { tempBoxHolder = it })
        }

        private fun getTempPoint(): PointF {
            return getLazyValue(tempPointHolder, { PointF() }, { tempPointHolder = it })
        }

        private fun getZeroOneArray(): FloatArray {
            return getLazyValue(zeroOneArrayHolder, { floatArrayOf(0f, 1f) }, { zeroOneArrayHolder = it })
        }

        private fun gradientTypeToString(type: Int): String {
            return when (type) {
                TYPE_LINEAR_GRADIENT -> "LINEAR_GRADIENT"
                TYPE_RADIAL_GRADIENT -> "RADIAL_GRADIENT"
                else -> throw IllegalArgumentException("type")
            }
        }

        private fun StringBuilder.appendColor(@ColorInt color: Int) {
            append('#')
            append(color.toString(16))
        }

        @RequiresApi(26)
        private fun StringBuilder.appendColor(@ColorLong color: Long) {
            append("(r=")
            append(color.red)
            append(", g=")
            append(color.green)
            append(", b=")
            append(color.blue)
            append(", a=")
            append(color.alpha)
            append(", space=")
            append(color.colorSpace.name)
            append(')')
        }

        private fun colorsToString(colors: IntArray): String {
            return buildString {
                append('[')
                colors.forEachIndexed { index, color ->
                    appendColor(color)

                    if (index < colors.size - 1) {
                        append(", ")
                    }
                }
                append(']')
            }
        }

        @RequiresApi(26)
        private fun colorsToString(colors: LongArray): String {
            // All colors have the same color space.
            val isSrgb = colors[0].colorSpace.isSrgb

            return buildString {
                append('[')
                colors.forEachIndexed { index, color ->
                    if (isSrgb) {
                        appendColor((color shr 32).toInt())
                    } else {
                        appendColor(color)
                    }

                    if (index < colors.size - 1) {
                        append(", ")
                    }
                }
                append(']')
            }
        }

        private fun combineAlphas(alpha: Float, originAlphas: ByteArray, colors: IntArray) {
            for (i in colors.indices) {
                val originAlpha = originAlphas[i].toInt() and 0xFF

                colors[i] = colors[i].withCombinedAlpha(alpha, originAlpha)
            }
        }

        @RequiresApi(26)
        private fun combineAlphas(alpha: Float, originAlphas: ByteArray, colors: LongArray) {
            for (i in colors.indices) {
                val originAlpha = originAlphas[i].toInt() and 0xFF

                colors[i] = Color.pack(colors[i].asSrgbUnsafe().withCombinedAlpha(alpha, originAlpha))
            }
        }

        private fun colorsContentEquals(
            a: IntArray,
            aAlphas: ByteArray?,
            b: IntArray,
            bAlphas: ByteArray?
        ): Boolean {
            if (a.size != b.size) return false

            for (i in a.indices) {
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

        @RequiresApi(26)
        private fun colorsContentEquals(
            a: LongArray,
            aAlphas: ByteArray?,
            b: LongArray,
            bAlphas: ByteArray?
        ): Boolean {
            if (a.size != b.size) return false

            for (i in a.indices) {
                var aElement = a[i]
                if (aAlphas != null) {
                    aElement = Color.pack(aElement.asSrgbUnsafe().withAlpha(aAlphas[i]))
                }

                var bElement = b[i]
                if (bAlphas != null) {
                    bElement = Color.pack(bElement.asSrgbUnsafe().withAlpha(bAlphas[i]))
                }

                if (aElement != bElement) {
                    return false
                }
            }

            return true
        }

        private fun extractAlphas(colors: IntArray): ByteArray {
            return ByteArray(colors.size) { i -> colors[i].alpha.toByte() }
        }

        @RequiresApi(26)
        private fun extractAlphasSrgb(colors: LongArray): ByteArray {
            return ByteArray(colors.size) { i -> colors[i].asSrgbUnsafe().alpha.toByte() }
        }

        @RequiresApi(26)
        private fun startEndColorsToLongArray(@ColorInt start: Int, @ColorInt end: Int): LongArray {
            return longArrayOf(Color.pack(start), Color.pack(end))
        }

        @RequiresApi(26)
        private fun convertColorIntsToLongs(colors: IntArray): LongArray {
            return LongArray(colors.size) { i -> Color.pack(colors[i]) }
        }

        private fun checkColorsAndPositions(colors: IntArray, positions: FloatArray) {
            if (colors.size != positions.size) {
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
        fun solid(@ColorInt color: Int): Fill {
            return if (Build.VERSION.SDK_INT >= 29) {
                Post29(TYPE_SOLID, color = Color.pack(color))
            } else {
                Pre29(TYPE_SOLID, color)
            }
        }

        private fun createGradient(
            type: Int,
            @ColorInt startColor: Int,
            @ColorInt endColor: Int
        ): Fill {
            return if (Build.VERSION.SDK_INT >= 29) {
                Post29(
                    type,
                    gradientColors = startEndColorsToLongArray(startColor, endColor),
                    gradientPositions = getZeroOneArray()
                )
            } else {
                Pre29(
                    type,
                    gradientColor1 = startColor,
                    gradientColor2 = endColor
                )
            }
        }

        private fun createGradient(type: Int, colors: IntArray, positions: FloatArray): Fill {
            checkColorsAndPositions(colors, positions)

            return if (Build.VERSION.SDK_INT >= 29) {
                Post29(
                    type,
                    gradientColors = convertColorIntsToLongs(colors),
                    gradientPositions = positions
                )
            } else {
                Pre29(
                    type,
                    gradientColors = colors.copyOf(),
                    gradientPositions = positions
                )
            }
        }

        /**
         * Creates a linear gradient fill using start and end color specified by color int.
         *
         * @param startColor start color of gradient.
         * @param endColor end color of gradient.
         */
        @JvmStatic
        fun linearGradient(@ColorInt startColor: Int, @ColorInt endColor: Int): Fill {
            return createGradient(TYPE_LINEAR_GRADIENT, startColor, endColor)
        }

        /**
         * Creates a linear gradient fill using array of colors specified by color ints and relative positions for colors.
         *
         * @param colors colors of gradient
         * @param positions relative positions of each color, each element should be in range `[0; 1]`
         */
        @JvmStatic
        fun linearGradient(colors: IntArray, positions: FloatArray): Fill {
            return createGradient(TYPE_LINEAR_GRADIENT, colors, positions)
        }

        /**
         * Creates a radial gradient fill using start and end color specified by color int.
         *
         * @param startColor start color of gradient.
         * @param endColor end color of gradient.
         */
        @JvmStatic
        fun radialGradient(@ColorInt startColor: Int, @ColorInt endColor: Int): Fill {
            return createGradient(TYPE_RADIAL_GRADIENT, startColor, endColor)
        }

        /**
         * Creates a radial gradient fill.
         *
         * @param colors colors of gradient
         * @param positions relative positions of each color, each element should be in range `[0; 1]`
         */
        @JvmStatic
        fun radialGradient(colors: IntArray, positions: FloatArray): Fill {
            return createGradient(TYPE_RADIAL_GRADIENT, colors, positions)
        }
    }
}