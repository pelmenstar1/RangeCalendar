package io.github.pelmenstar1.rangecalendar.utils

import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.ColorLong
import androidx.annotation.RequiresApi

internal fun CompatColorArray(size: Int): CompatColorArray {
    return if(Build.VERSION.SDK_INT >= 26) {
        CompatColorArray(LongArray(size))
    } else {
        CompatColorArray(IntArray(size))
    }
}

@JvmInline
internal value class CompatColorArray(private val array: Any) {
    val size: Int
        get() = if(Build.VERSION.SDK_INT >= 26) {
            (array as LongArray).size
        } else {
            (array as IntArray).size
        }

    fun ints() = if(Build.VERSION.SDK_INT < 26) {
        array as IntArray
    } else {
        throw IllegalStateException("API level is greater than 26")
    }

    fun longs() = if(Build.VERSION.SDK_INT >= 26) {
        array as LongArray
    } else {
        throw IllegalStateException("API level is less than 26")
    }

    @ColorInt
    fun getColorInt(index: Int) = if(Build.VERSION.SDK_INT >= 26) {
        Color.toArgb(longs()[index])
    } else {
        ints()[index]
    }

    @ColorLong
    @RequiresApi(26)
    fun getColorLong(index: Int) = longs()[index]

    fun setColorInt(index: Int, @ColorInt color: Int) {
        if(Build.VERSION.SDK_INT >= 29) {
            longs()[index] = Color.pack(color)
        } else {
            ints()[index] = color
        }
    }

    @RequiresApi(26)
    fun setColorLong(index: Int, @ColorLong color: Long) {
        longs()[index] = color
    }

    fun initPaintColor(index: Int, paint: Paint) {
        if(Build.VERSION.SDK_INT >= 26) {
            paint.setColorLongFast(getColorLong(index))
        } else {
            paint.color = getColorInt(index)
        }
    }

    fun initPaintColor(index: Int, alpha: Float, paint: Paint) {
        if(Build.VERSION.SDK_INT >= 26) {
            paint.setColorLongFast(getColorLong(index).withAlpha(alpha))
        } else {
            paint.color = getColorInt(index).withAlpha(alpha)
        }
    }

    fun copyFrom(colors: CompatColorArray) {
        if(Build.VERSION.SDK_INT >= 26) {
            val source = array as LongArray
            val dest = colors.array as LongArray

            System.arraycopy(source, 0, dest, 0, source.size)
        } else {
            val source = array as IntArray
            val dest = colors.array as IntArray

            System.arraycopy(source, 0, dest, 0, source.size)
        }
    }
}