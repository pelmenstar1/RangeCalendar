package com.github.pelmenstar1.rangecalendar

import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.pelmenstar1.rangecalendar.complexRange.cell.CellComplexRange
import com.github.pelmenstar1.rangecalendar.complexRange.date.DateComplexRange
import com.github.pelmenstar1.rangecalendar.complexRange.date.DateFragment
import com.github.pelmenstar1.rangecalendar.test.R
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class RangeCalendarPagerAdapterTests {
    enum class NotifyType {
        INSERTED,
        REMOVED,
        CHANGED
    }

    internal data class TypedRange(
        val type: NotifyType,
        val position: Int,
        val itemCount: Int,
        val payload: RangeCalendarPagerAdapter.Payload? = null
    )

    internal object PayloadScope {
        fun select(
            range: CellComplexRange,
            requestRejectedBehaviour: SelectionRequestRejectedBehaviour,
            withAnimation: Boolean
        ) = payload { select(range, requestRejectedBehaviour, withAnimation) }

        fun select(
            range: IntRange,
            requestRejectedBehaviour: SelectionRequestRejectedBehaviour,
            withAnimation: Boolean
        ) = select(CellComplexRange(range), requestRejectedBehaviour, withAnimation)

        fun clearSelection(withAnimation: Boolean) =
            payload { clearSelection(withAnimation) }

        fun updateTodayIndex() = payload { updateTodayIndex() }

        private fun payload(
            block: RangeCalendarPagerAdapter.Payload.Companion.() -> RangeCalendarPagerAdapter.Payload
        ): RangeCalendarPagerAdapter.Payload {
            return RangeCalendarPagerAdapter.Payload.block()
        }
    }

    internal class RangeListBuilder {
        private val list = ArrayList<TypedRange>()

        fun inserted(position: Int, itemCount: Int) =
            add(NotifyType.INSERTED, position, itemCount)

        fun removed(position: Int, itemCount: Int) =
            add(NotifyType.REMOVED, position, itemCount)

        fun changed(position: Int, itemCount: Int) =
            add(NotifyType.CHANGED, position, itemCount)

        fun changed(ym: YearMonth, count: Int = 1, payload: RangeCalendarPagerAdapter.Payload? = null) =
            add(NotifyType.CHANGED, position = ym.totalMonths, count, payload)

        fun changed(ym: YearMonth, count: Int = 1, payloadSelect: PayloadScope.() -> RangeCalendarPagerAdapter.Payload) {
            changed(ym, count, PayloadScope.payloadSelect())
        }

        fun changed(year: Int, month: Int, count: Int = 1, payloadSelect: PayloadScope.() -> RangeCalendarPagerAdapter.Payload) {
            changed(YearMonth(year, month), count, payloadSelect)
        }

        private fun add(
            type: NotifyType,
            position: Int,
            itemCount: Int,
            payload: RangeCalendarPagerAdapter.Payload? = null
        ) {
            list.add(TypedRange(type, position, itemCount, payload))
        }

        fun toArray() = list.toTypedArray()
    }

    private class CapturedAdapterNotifications(
        adapter: RangeCalendarPagerAdapter,
        capturePayloads: Boolean
    ) {
        private val ranges = ArrayList<TypedRange>()

        init {
            adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    ranges.add(TypedRange(NotifyType.INSERTED, positionStart, itemCount))
                }

                override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                    ranges.add(TypedRange(NotifyType.REMOVED, positionStart, itemCount))
                }

                override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
                    ranges.add(
                        TypedRange(
                            NotifyType.CHANGED,
                            positionStart,
                            itemCount,
                            (payload as RangeCalendarPagerAdapter.Payload?).takeIf { capturePayloads }
                        )
                    )
                }

                override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                    onItemRangeChanged(positionStart, itemCount, payload = null)
                }
            })
        }

        fun getRanges() = ranges.toTypedArray()
    }

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val themedContext = ContextThemeWrapper(context, R.style.Theme_TestTheme)
    private val cr = CalendarResources(themedContext)

    @Test
    fun setRangeTests() {
        fun testCase(
            oldMinDate: PackedDate,
            oldMaxDate: PackedDate,
            newMinDate: PackedDate,
            newMaxDate: PackedDate,
            expectedItemCount: Int,
            buildRanges: RangeListBuilder.() -> Unit,
        ) {
            val expectedRanges = RangeListBuilder().also(buildRanges).toArray()

            val adapter = RangeCalendarPagerAdapter(cr)
            adapter.setRange(oldMinDate, oldMaxDate)

            val notifications = CapturedAdapterNotifications(adapter, capturePayloads = false)

            adapter.setRange(newMinDate, newMaxDate)

            val actualItemCount = adapter.itemCount
            assertEquals(expectedItemCount, actualItemCount, "item count")

            val actualRanges = notifications.getRanges()
            assertContentEquals(expectedRanges, actualRanges)
        }

        fun testCase(
            oldRange: IntRange,
            newRange: IntRange,
            expectedItemCount: Int,
            buildRanges: RangeListBuilder.() -> Unit
        ) {
            val oldMinDate = PackedDate(YearMonth(oldRange.first), 1)
            val oldMaxDate = PackedDate(YearMonth(oldRange.last), 1)
            val newMinDate = PackedDate(YearMonth(newRange.first), 1)
            val newMaxDate = PackedDate(YearMonth(newRange.last), 1)

            testCase(
                oldMinDate, oldMaxDate,
                newMinDate, newMaxDate,
                expectedItemCount,
                buildRanges
            )
        }

        testCase(
            oldMinDate = PackedDate(year = 2023, month = 6, dayOfMonth = 1),
            oldMaxDate = PackedDate(year = 2023, month = 6, dayOfMonth = 3),
            newMinDate = PackedDate(year = 2023, month = 6, dayOfMonth = 1),
            newMaxDate = PackedDate(year = 2023, month = 6, dayOfMonth = 5),
            expectedItemCount = 1,
        ) {
            changed(position = 0, itemCount = 1)
        }

        testCase(
            oldMinDate = PackedDate(year = 2023, month = 6, dayOfMonth = 1),
            oldMaxDate = PackedDate(year = 2023, month = 6, dayOfMonth = 3),
            newMinDate = PackedDate(year = 2023, month = 6, dayOfMonth = 1),
            newMaxDate = PackedDate(year = 2023, month = 6, dayOfMonth = 3),
            expectedItemCount = 1,
        ) {
            // The range remains the same, so there should be no 'changed' notification
        }

        testCase(
            oldRange = 0..1,
            newRange = 1..1,
            expectedItemCount = 1
        ) {
            removed(position = 0, itemCount = 1)
            changed(position = 0, itemCount = 1)
        }

        testCase(
            oldRange = 0..3,
            newRange = 0..2,
            expectedItemCount = 3
        ) {
            removed(position = 3, itemCount = 1)
            changed(position = 0, itemCount = 3)
        }

        testCase(
            oldRange = 0..2,
            newRange = 0..4,
            expectedItemCount = 5,
        ) {
            inserted(position = 3, itemCount = 2)
            changed(position = 0, itemCount = 5)
        }

        testCase(
            oldRange = 2..4,
            newRange = 0..3,
            expectedItemCount = 4
        ) {
            inserted(position = 0, itemCount = 2)
            removed(position = 4, itemCount = 1)
            changed(position = 0, itemCount = 4)
        }

        testCase(
            oldRange = 2..3,
            newRange = 0..4,
            expectedItemCount = 5
        ) {
            inserted(position = 0, itemCount = 2)
            inserted(position = 4, itemCount = 1)
            changed(position = 0, itemCount = 5)
        }

        testCase(
            oldRange = 0..3,
            newRange = 1..2,
            expectedItemCount = 2
        ) {
            removed(position = 0, itemCount = 1)
            removed(position = 2, itemCount = 1)
            changed(position = 0, itemCount = 2)
        }

        testCase(
            oldRange = 0..2,
            newRange = 2..3,
            expectedItemCount = 2,
        ) {
            removed(position = 0, itemCount = 2)
            inserted(position = 1, itemCount = 1)
            changed(position = 0, itemCount = 2)
        }

        testCase(
            oldRange = 0..1,
            newRange = 2..4,
            expectedItemCount = 3,
        ) {
            removed(position = 0, itemCount = 2)
            inserted(position = 0, itemCount = 3)
            changed(position = 0, itemCount = 3)
        }

        testCase(
            oldRange = 0..1,
            newRange = 3..4,
            expectedItemCount = 2
        ) {
            changed(position = 0, itemCount = 2)
        }
    }

    @Test
    fun pagesBetweenAbsMinMaxTest() {
        val maxYm = YearMonth.forDate(PackedDate.MAX_DATE)
        val minYm = YearMonth.forDate(PackedDate.MIN_DATE)
        val expectedValue = (maxYm - minYm + 1).totalMonths
        val actualValue = RangeCalendarPagerAdapter.PAGES_BETWEEN_ABS_MIN_MAX

        assertEquals(expectedValue, actualValue)
    }

    @Test
    fun setTodayTest() {
        fun testCase(
            oldToday: PackedDate,
            newToday: PackedDate,
            buildRanges: RangeListBuilder.() -> Unit
        ) {
            val adapter = RangeCalendarPagerAdapter(cr)

            if (oldToday != PackedDate.INVALID) {
                adapter.setToday(oldToday)
            }

            val notifications = CapturedAdapterNotifications(adapter, capturePayloads = true)
            adapter.setToday(newToday)

            assertEquals(newToday, adapter.today)

            val expectedRanges = RangeListBuilder().also(buildRanges).toArray()
            val actualRanges = notifications.getRanges()

            assertContentEquals(expectedRanges, actualRanges)
        }

        testCase(
            oldToday = PackedDate.INVALID,
            newToday = PackedDate(year = 2023, month = 5, dayOfMonth = 5)
        ) {
            changed(year = 2023, month = 5) { updateTodayIndex() }
        }

        testCase(
            oldToday = PackedDate(year = 2023, month = 5, dayOfMonth = 5),
            newToday = PackedDate(year = 2023, month = 5, dayOfMonth = 5)
        ) {
            changed(year = 2023, month = 5) { updateTodayIndex() }
        }

        testCase(
            oldToday = PackedDate(year = 2023, month = 5, dayOfMonth = 5),
            newToday = PackedDate(year = 2023, month = 6, dayOfMonth = 5)
        ) {
            changed(year = 2023, month = 6) { updateTodayIndex() }
            changed(year = 2023, month = 5) { updateTodayIndex() }
        }
    }

    @Test
    fun clearSelectionTest() {
        fun testCase(
            selectionRange: DateComplexRange,
            withAnimation: Boolean,
            expectedEventFired: Boolean,
            buildRanges: RangeListBuilder.() -> Unit
        ) {
            val adapter = RangeCalendarPagerAdapter(cr)
            val isSelected = adapter.selectRange(
                selectionRange,
                SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION,
                withAnimation = false
            )

            // isSelected is false when selectionRange is empty
            assertEquals(!selectionRange.isEmpty, isSelected)

            var isEventFired = false

            adapter.onSelectionListener = object : RangeCalendarView.OnSelectionListener {
                override fun onSelectionCleared() {
                    isEventFired = true
                }

                override fun onSelection(complexRange: DateComplexRange) {
                }
            }

            val notifications = CapturedAdapterNotifications(adapter, capturePayloads = true)
            adapter.clearSelection(withAnimation)

            assertTrue(adapter.selectedRange.isEmpty)
            assertEquals(isEventFired, expectedEventFired, "event")

            val expectedRanges = RangeListBuilder().also(buildRanges).toArray()
            val actualRanges = notifications.getRanges()

            assertContentEquals(expectedRanges, actualRanges)
        }

        testCase(
            selectionRange = DateComplexRange(
                DateFragment(
                    PackedDate(year = 2023, month = 6, dayOfMonth = 7),
                    PackedDate(year = 2023, month = 6, dayOfMonth = 10)
                )
            ),
            withAnimation = true,
            expectedEventFired = true
        ) {
            changed(year = 2023, month = 5, count = 2) { clearSelection(withAnimation = true) }
        }

        testCase(
            selectionRange = DateComplexRange(
                DateFragment(
                    PackedDate(year = 2023, month = 6, dayOfMonth = 7),
                    PackedDate(year = 2023, month = 7, dayOfMonth = 10),
                )
            ),
            withAnimation = true,
            expectedEventFired = true
        ) {
            changed(year = 2023, month = 5, count = 3) { clearSelection(withAnimation = true) }
        }

        testCase(
            selectionRange = DateComplexRange.empty(),
            withAnimation = false,
            expectedEventFired = false
        ) {
            // No notifications should be made
        }
    }

    @Test
    fun selectRangeTest() {
        fun testHelper(dateRange: DateComplexRange, buildRanges: RangeListBuilder.() -> Unit) {
            val expectedRanges = RangeListBuilder().also(buildRanges).toArray()

            val adapter = RangeCalendarPagerAdapter(cr).apply {
                setFirstDayOfWeek(CompatDayOfWeek.Monday)
            }
            val notifications = CapturedAdapterNotifications(adapter, capturePayloads = true)

            val isSelected = adapter.selectRange(
                dateRange,
                SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION,
                withAnimation = true
            )

            val actualRanges = notifications.getRanges()
            assertContentEquals(expectedRanges, actualRanges)

            assertEquals(adapter.selectedRange, dateRange)
            assertTrue(isSelected)
        }

        fun testHelper(startDate: PackedDate, endDate: PackedDate, buildRanges: RangeListBuilder.() -> Unit) {
            testHelper(DateComplexRange(DateFragment(startDate, endDate)), buildRanges)
        }

        testHelper(
            startDate = PackedDate(year = 2023, month = 7, dayOfMonth = 10),
            endDate = PackedDate(year = 2023, month = 7, dayOfMonth = 15)
        ) {
            changed(year = 2023, month = 7) {
                select(
                    range = 14..19,
                    requestRejectedBehaviour = SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION,
                    withAnimation = true
                )
            }
        }

        testHelper(
            startDate = PackedDate(year = 2023, month = 7, dayOfMonth = 10),
            endDate = PackedDate(year = 2023, month = 8, dayOfMonth = 1),
        ) {
            changed(year = 2023, month = 7) {
                select(
                    range = 14..36,
                    requestRejectedBehaviour = SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION,
                    withAnimation = true
                )
            }

            changed(year = 2023, month = 8) {
                select(
                    range = 0..1,
                    requestRejectedBehaviour = SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION,
                    withAnimation = true
                )
            }
        }

        testHelper(
            startDate = PackedDate(year = 2023, month = 6, dayOfMonth = 26),
            endDate = PackedDate(year = 2023, month = 8, dayOfMonth = 1)
        ) {
            changed(year = 2023, month = 6) {
                select(
                    range = 28..41,
                    requestRejectedBehaviour = SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION,
                    withAnimation = true
                )
            }

            changed(year = 2023, month = 7) {
                select(
                    range = 0..36,
                    requestRejectedBehaviour = SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION,
                    withAnimation = true
                )
            }

            changed(year = 2023, month = 8) {
                select(
                    range = 0..1,
                    requestRejectedBehaviour = SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION,
                    withAnimation = true
                )
            }
        }

        testHelper(
            startDate = PackedDate(year = 2023, month = 6, dayOfMonth = 25),
            endDate = PackedDate(year = 2023, month = 8, dayOfMonth = 7),
        ) {
            changed(year = 2023, month = 6) {
                select(
                    range = 27..41,
                    requestRejectedBehaviour = SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION,
                    withAnimation = true
                )
            }

            changed(year = 2023, month = 7) {
                select(
                    range = CellComplexRange.All,
                    requestRejectedBehaviour = SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION,
                    withAnimation = true
                )
            }

            changed(year = 2023, month = 8) {
                select(
                    range = 0..7,
                    requestRejectedBehaviour = SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION,
                    withAnimation = true
                )
            }
        }
    }

    @Test
    fun selectRangeShouldClearOtherSelectionTest() {
        fun testHelper(
            previousDateRange: DateComplexRange,
            newDateRange: DateComplexRange,
            buildRanges: RangeListBuilder.() -> Unit
        ) {
            val expectedRanges = RangeListBuilder().also(buildRanges).toArray()

            val adapter = RangeCalendarPagerAdapter(cr).apply {
                setFirstDayOfWeek(CompatDayOfWeek.Monday)
                selectRange(
                    previousDateRange,
                    requestRejectedBehaviour = SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION,
                    withAnimation = false
                )
            }

            val notifications = CapturedAdapterNotifications(adapter, capturePayloads = true)

            val isSelected = adapter.selectRange(
                newDateRange,
                SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION,
                withAnimation = true
            )

            val actualRanges = notifications.getRanges()
            assertContentEquals(expectedRanges, actualRanges)

            assertEquals(adapter.selectedRange, newDateRange)
            assertTrue(isSelected)
        }

        fun testHelper(
            previousDateRange: PackedDateRange,
            newDateRange: PackedDateRange,
            buildRanges: RangeListBuilder.() -> Unit
        ) {
            testHelper(
                DateComplexRange(DateFragment(previousDateRange)),
                DateComplexRange(DateFragment(newDateRange)),
                buildRanges
            )
        }

        testHelper(
            previousDateRange = PackedDateRange(
                PackedDate(year = 2023, month = 7, dayOfMonth = 1),
                PackedDate(year = 2023, month = 7, dayOfMonth = 1),
            ),
            newDateRange = PackedDateRange(
                PackedDate(year = 2023, month = 7, dayOfMonth = 1),
                PackedDate(year = 2023, month = 7, dayOfMonth = 2),
            )
        ) {
            changed(year = 2023, month = 6) {
                select(
                    range = 33..34,
                    requestRejectedBehaviour = SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION,
                    withAnimation = true
                )
            }

            changed(year = 2023, month = 7) {
                select(
                    range = 5..6,
                    requestRejectedBehaviour = SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION,
                    withAnimation = true
                )
            }
        }
    }

    @Test
    fun selectRangeRespectsGateTest() {
        val ym = YearMonth(year = 2023, month = 6)
        val expectedRange = DateComplexRange(DateFragment(
            PackedDate(ym, dayOfMonth = 5),
            PackedDate(ym, dayOfMonth = 6)
        ))

        val gate = object : RangeCalendarView.SelectionGate {
            override fun accept(complexRange: DateComplexRange): Boolean {
                assertEquals(expectedRange, complexRange)

                return false
            }
        }

        val adapter = RangeCalendarPagerAdapter(cr)
        adapter.selectionGate = gate

        val notifications = CapturedAdapterNotifications(adapter, capturePayloads = true)
        val isSelected = adapter.selectRange(
            expectedRange,
            SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION,
            withAnimation = false
        )

        val actualRanges = notifications.getRanges()
        assertEquals(0, actualRanges.size)
        assertFalse(isSelected)
    }

    @Test
    fun isDateVisibleOnPageTest() {
        fun testHelper(date: PackedDate, pageYm: YearMonth, expectedResult: Boolean) {
            val adapter = RangeCalendarPagerAdapter(cr).apply {
                setFirstDayOfWeek(CompatDayOfWeek.Monday)
            }

            val actualResult = adapter.isDateVisibleOnPage(date, pageYm)

            assertEquals(expectedResult, actualResult)
        }

        testHelper(
            date = PackedDate(year = 2023, month = 8, dayOfMonth = 31),
            pageYm = YearMonth(2023, 8),
            expectedResult = true
        )

        testHelper(
            date = PackedDate(year = 2023, month = 7, dayOfMonth = 31),
            pageYm = YearMonth(2023, 8),
            expectedResult = true
        )
    }
}