package com.github.pelmenstar1.rangecalendar.utils

internal object Radii {
    private val array = FloatArray(8)
    private var currentRadius: Float = 0f

    private var flags = 0

    private const val LT_SHIFT = 0
    private const val RT_SHIFT = 1
    private const val RB_SHIFT = 2
    private const val LB_SHIFT = 3

    private fun setFlag(shift: Int) {
        flags = flags or (1 shl shift)
    }

    private fun setFlag(shift: Int, condition: Boolean) {
        if (condition) setFlag(shift)
    }

    fun leftTop() = setFlag(LT_SHIFT)
    fun leftTop(condition: Boolean) = setFlag(LT_SHIFT, condition)

    fun rightTop() = setFlag(RT_SHIFT)
    fun rightTop(condition: Boolean) = setFlag(RT_SHIFT, condition)

    fun rightBottom() = setFlag(RB_SHIFT)
    fun rightBottom(condition: Boolean) = setFlag(RB_SHIFT, condition)

    fun leftBottom() = setFlag(LB_SHIFT)
    fun leftBottom(condition: Boolean) = setFlag(LB_SHIFT, condition)

    inline fun withRadius(radius: Float, block: Radii.() -> Unit) {
        flags = 0
        currentRadius = radius

        block(this)
    }

    fun radii(): FloatArray {
        initCorner(LT_SHIFT)
        initCorner(RT_SHIFT)
        initCorner(RB_SHIFT)
        initCorner(LB_SHIFT)

        return array
    }

    private fun initCorner(shift: Int) {
        val cornerRadius = if((flags and (1 shl shift)) != 0) currentRadius else 0f
        val offset = shift * 2

        array[offset] = cornerRadius
        array[offset + 1] = cornerRadius
    }
}