package io.github.pelmenstar1.rangecalendar.decoration

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import androidx.annotation.ColorInt
import android.os.Parcel
import android.graphics.RectF
import io.github.pelmenstar1.rangecalendar.R
import android.os.Parcelable.Creator
import kotlin.math.max

class LineDecor : CellDecor<LineDecor> {
    @get:ColorInt
    val color: Int

    constructor(@ColorInt color: Int) {
        this.color = color
    }

    constructor(source: Parcel) : super(source) {
        color = source.readInt()
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        super.writeToParcel(dest, 0)
        dest.writeInt(color)
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun newRenderer(context: Context): CellDecorRenderer<LineDecor> = Renderer(context)

    private class Renderer(context: Context) : CellDecorRenderer<LineDecor> {
        private val stripeMarginTop: Float
        private val stripeHorizontalMargin: Float
        private val paint: Paint

        init {
            val res = context.resources
            stripeMarginTop = res.getDimension(R.dimen.rangeCalendar_stripeMarginTop)
            stripeHorizontalMargin = res.getDimension(R.dimen.rangeCalendar_stripeHorizontalMargin)

            paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
        }

        override fun decorationClass() = LineDecor::class.java

        override fun render(
            canvas: Canvas,
            decorations: Array<out CellDecor<*>>,
            start: Int, endInclusive: Int,
            info: CellInfo,
        ) {
            val length = endInclusive - start + 1

            // The coefficient is hand-picked for line not to be very thin or thick
            val lineHeight = max(1f, info.size * (1f / 12f))

            // Find half of total height of lines
            val halfTotalHeight = (lineHeight + stripeMarginTop) * length * 0.5f

            // Find center y of bottom part of the cell
            val bottomCenterY = info.size * 0.75f
            var top = bottomCenterY - halfTotalHeight
            val bottom = bottomCenterY + halfTotalHeight

            tempRect.set(0f, top, info.size, bottom)

            // If cell is round, then it should be narrowed to fit the cell shape
            info.narrowRectOnBottom(tempRect)

            val halfWidth = tempRect.width() * 0.5f - stripeHorizontalMargin
            val centerX = tempRect.centerX()

            for (i in start..endInclusive) {
                val decor = decorations[i] as LineDecor
                val animFraction = decor.animationFraction

                val animatedHalfWidth = halfWidth * animFraction

                // The line is animated starting from its center
                val stripeLeft = centerX - animatedHalfWidth
                val stripeRight = centerX + animatedHalfWidth

                tempRect.set(stripeLeft, top, stripeRight, top + lineHeight)

                paint.color = decor.color
                canvas.drawRect(tempRect, paint)

                top += lineHeight + stripeMarginTop
            }
        }

        companion object {
            private val tempRect = RectF()
        }
    }

    companion object {
        @JvmField
        val CREATOR: Creator<LineDecor> = object : Creator<LineDecor> {
            override fun createFromParcel(source: Parcel) = LineDecor(source)
            override fun newArray(size: Int) = arrayOfNulls<LineDecor>(size)
        }
    }
}