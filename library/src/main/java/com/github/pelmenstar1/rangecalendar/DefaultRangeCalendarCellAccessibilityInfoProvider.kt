package com.github.pelmenstar1.rangecalendar

import java.util.Locale

internal class DefaultRangeCalendarCellAccessibilityInfoProvider(
    locale: Locale
) : RangeCalendarCellAccessibilityInfoProvider {
    private val dateFormatter = CompatDateFormatter(locale, pattern = "dd MMMM yyyy")

    override fun getContentDescription(year: Int, month: Int, dayOfMonth: Int): CharSequence {
        return dateFormatter.format(PackedDate(year, month, dayOfMonth))
    }
}