package com.github.pelmenstar1.rangecalendar

import com.github.pelmenstar1.rangecalendar.utils.getBestDatePatternCompat
import java.util.Locale

internal class DefaultRangeCalendarCellAccessibilityInfoProvider(
    locale: Locale
) : RangeCalendarCellAccessibilityInfoProvider {
    private val dateFormatter = CompatDateFormatter(
        locale,
        pattern = getBestDatePatternCompat(locale, "dd MMMM yyyy")
    )

    override fun getContentDescription(year: Int, month: Int, dayOfMonth: Int): CharSequence {
        return dateFormatter.format(PackedDate(year, month, dayOfMonth))
    }
}