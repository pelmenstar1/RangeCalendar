package com.github.pelmenstar1.rangecalendar.utils

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.core.content.res.ResourcesCompat

internal fun Context.getColorFromAttribute(@AttrRes resId: Int): Int {
    val typedValue = TypedValue()

    val theme = theme
    if (theme.resolveAttribute(resId, typedValue, true)) {
        val type = typedValue.type

        return if (type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
            typedValue.data
        } else {
            ResourcesCompat.getColor(resources, typedValue.resourceId, theme)
        }
    }

    throw IllegalArgumentException("Attribute $resId isn't defined")
}

internal fun Context.getColorStateListFromAttribute(@AttrRes resId: Int): ColorStateList {
    val array = obtainStyledAttributes(intArrayOf(resId))

    try {
        return array.getColorStateList(0)!!
    } finally {
        array.recycle()
    }
}

internal fun Context.getSelectableItemBackground(): Drawable? {
    val theme = theme

    val value = TypedValue()
    val attr = androidx.appcompat.R.attr.selectableItemBackgroundBorderless

    if (theme.resolveAttribute(attr, value, true)) {
        return ResourcesCompat.getDrawable(resources, value.resourceId, theme)
    }

    return null
}