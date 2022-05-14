package io.github.pelmenstar1.rangecalendar

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import androidx.annotation.GravityInt
import android.view.Gravity
import kotlin.jvm.JvmOverloads
import io.github.pelmenstar1.rangecalendar.CalendarSelectionViewLayoutParams

/**
 * LayoutParams for calendar's selection view.
 * Besides of default width and height properties, there is also gravity.
 */
class CalendarSelectionViewLayoutParams : ViewGroup.LayoutParams {
    @GravityInt
    var gravity = Gravity.CENTER

    constructor(c: Context, attrs: AttributeSet) : super(c, attrs)

    @JvmOverloads
    constructor(width: Int, height: Int, @GravityInt gravity: Int = Gravity.CENTER) : super(
        width,
        height
    ) {
        this.gravity = gravity
    }

    constructor(source: ViewGroup.LayoutParams) : super(source)

    companion object {
        val DEFAULT = CalendarSelectionViewLayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER)
    }
}