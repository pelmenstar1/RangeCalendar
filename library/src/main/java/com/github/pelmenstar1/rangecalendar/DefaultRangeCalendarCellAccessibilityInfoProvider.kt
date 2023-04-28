package com.github.pelmenstar1.rangecalendar

import android.content.Context

internal class DefaultRangeCalendarCellAccessibilityInfoProvider(context: Context) : RangeCalendarCellAccessibilityInfoProvider {
    private val dateFormatter = CompatDateFormatter(context, pattern = "dd MMMM yyyy")

    override fun getContentDescription(year: Int, month: Int, dayOfMonth: Int): CharSequence {
        return dateFormatter.format(PackedDate(year, month, dayOfMonth))
    }
}