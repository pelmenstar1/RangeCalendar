package com.github.pelmenstar1.rangecalendar

import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.pelmenstar1.rangecalendar.selection.CellRange
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

    internal class RangeListBuilder {
        private val list = ArrayList<TypedRange>()

        fun inserted(position: Int, itemCount: Int) =
            add(NotifyType.INSERTED, position, itemCount)

        fun removed(position: Int, itemCount: Int) =
            add(NotifyType.REMOVED, position, itemCount)

        fun changed(position: Int, itemCount: Int) =
            add(NotifyType.CHANGED, position, itemCount)

        fun changed(ym: YearMonth, payload: RangeCalendarPagerAdapter.Payload? = null) =
            add(NotifyType.CHANGED, position = ym.totalMonths, itemCount = 1, payload)

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
    private val themedContext =
        ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat)
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
            changed(
                YearMonth(year = 2023, month = 5),
                payload = RangeCalendarPagerAdapter.Payload.updateTodayIndex()
            )
        }

        testCase(
            oldToday = PackedDate(year = 2023, month = 5, dayOfMonth = 5),
            newToday = PackedDate(year = 2023, month = 5, dayOfMonth = 5)
        ) {
            changed(
                YearMonth(year = 2023, month = 5),
                payload = RangeCalendarPagerAdapter.Payload.updateTodayIndex()
            )
        }

        testCase(
            oldToday = PackedDate(year = 2023, month = 5, dayOfMonth = 5),
            newToday = PackedDate(year = 2023, month = 6, dayOfMonth = 5)
        ) {
            changed(
                YearMonth(year = 2023, month = 6),
                payload = RangeCalendarPagerAdapter.Payload.updateTodayIndex()
            )

            changed(
                YearMonth(year = 2023, month = 5),
                payload = RangeCalendarPagerAdapter.Payload.updateTodayIndex()
            )
        }
    }

    @Test
    fun clearSelectionTest() {
        fun testCase(
            selectionYm: YearMonth,
            selectionRange: CellRange,
            withAnimation: Boolean,
            expectedEventFired: Boolean,
            buildRanges: RangeListBuilder.() -> Unit
        ) {
            val adapter = RangeCalendarPagerAdapter(cr)
            adapter.selectionYm = selectionYm
            adapter.selectionRange = selectionRange

            var isEventFired = false

            adapter.onSelectionListener = object : RangeCalendarView.OnSelectionListener {
                override fun onSelectionCleared() {
                    isEventFired = true
                }

                override fun onSelection(
                    startYear: Int, startMonth: Int, startDay: Int,
                    endYear: Int, endMonth: Int, endDay: Int
                ) {
                }
            }

            val notifications = CapturedAdapterNotifications(adapter, capturePayloads = true)
            adapter.clearSelection(withAnimation)

            assertEquals(CellRange.Invalid, adapter.selectionRange)
            assertEquals(isEventFired, expectedEventFired, "event")

            val expectedRanges = RangeListBuilder().also(buildRanges).toArray()
            val actualRanges = notifications.getRanges()

            assertContentEquals(expectedRanges, actualRanges)
        }

        testCase(
            selectionYm = YearMonth(year = 2023, month = 6),
            selectionRange = CellRange(0, 5),
            withAnimation = true,
            expectedEventFired = true
        ) {
            changed(
                YearMonth(year = 2023, month = 6),
                payload = RangeCalendarPagerAdapter.Payload.clearSelection(withAnimation = true)
            )
        }

        testCase(
            selectionYm = YearMonth(0),
            selectionRange = CellRange.Invalid,
            withAnimation = false,
            expectedEventFired = false
        ) {
            // No notifications should be made
        }
    }

    @Test
    fun selectRangeTest() {
        val ym = YearMonth(year = 2023, month = 6)
        val dateRange = PackedDateRange(
            start = PackedDate(year = 2023, month = 6, dayOfMonth = 2),
            end = PackedDate(year = 2023, month = 6, dayOfMonth = 5)
        )
        val expectedCellRange = CellRange(4, 7)
        val reqRejectedBehaviour = SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION
        val expectedRanges = RangeListBuilder().apply {
            val payload = RangeCalendarPagerAdapter.Payload.select(
                expectedCellRange,
                reqRejectedBehaviour,
                withAnimation = true
            )

            changed(ym, payload)
        }.toArray()

        val adapter = RangeCalendarPagerAdapter(cr)
        val notifications = CapturedAdapterNotifications(adapter, capturePayloads = true)

        val isSelected =
            adapter.selectRange(ym, dateRange, reqRejectedBehaviour, withAnimation = true)

        val actualRanges = notifications.getRanges()
        assertContentEquals(expectedRanges, actualRanges)

        assertEquals(adapter.selectionRange, expectedCellRange)
        assertEquals(adapter.selectionYm, ym)
        assertTrue(isSelected)
    }

    @Test
    fun selectRangeShouldClearOtherSelectionTest() {
        val adapter = RangeCalendarPagerAdapter(cr)
        val oldYm = YearMonth(year = 2023, month = 5)
        val newYm = YearMonth(year = 2023, month = 6)

        val newRange = PackedDateRange(
            PackedDate(newYm, dayOfMonth = 5),
            PackedDate(newYm, dayOfMonth = 6)
        )

        val newCellRange = CellRange(7, 8)

        val expectedRanges = RangeListBuilder().apply {
            changed(
                oldYm,
                RangeCalendarPagerAdapter.Payload.clearSelection(withAnimation = false)
            )
            changed(
                newYm,
                RangeCalendarPagerAdapter.Payload.select(
                    newCellRange,
                    SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION,
                    withAnimation = false
                )
            )
        }.toArray()

        adapter.selectRange(
            oldYm,
            PackedDateRange(
                PackedDate(oldYm, dayOfMonth = 5),
                PackedDate(oldYm, dayOfMonth = 6)
            ),
            SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION,
            withAnimation = false
        )

        val notifications = CapturedAdapterNotifications(adapter, capturePayloads = true)

        adapter.selectRange(
            newYm,
            newRange,
            SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION,
            withAnimation = false
        )

        val actualRanges = notifications.getRanges()
        assertContentEquals(expectedRanges, actualRanges)
    }

    @Test
    fun selectRangeRespectsGateTest() {
        fun assertDate(year: Int, month: Int, dayOfMonth: Int, expectedDate: PackedDate) {
            assertEquals(year, expectedDate.year, "year")
            assertEquals(month, expectedDate.month, "month")
            assertEquals(dayOfMonth, expectedDate.dayOfMonth, "dayOfMonth")
        }


        val ym = YearMonth(year = 2023, month = 6)
        val dateRange = PackedDateRange(
            PackedDate(ym, dayOfMonth = 5),
            PackedDate(ym, dayOfMonth = 6)
        )

        val gate = object : RangeCalendarView.SelectionGate {
            override fun range(
                startYear: Int,
                startMonth: Int,
                startDay: Int,
                endYear: Int,
                endMonth: Int,
                endDay: Int
            ): Boolean {
                assertDate(startYear, startMonth, startDay, dateRange.start)
                assertDate(endYear, endMonth, endDay, dateRange.end)

                return false
            }
        }

        val adapter = RangeCalendarPagerAdapter(cr)
        adapter.selectionGate = gate

        val notifications = CapturedAdapterNotifications(adapter, capturePayloads = true)
        val isSelected = adapter.selectRange(
            ym,
            dateRange,
            SelectionRequestRejectedBehaviour.CLEAR_CURRENT_SELECTION,
            withAnimation = false
        )

        val actualRanges = notifications.getRanges()
        assertEquals(0, actualRanges.size)
        assertFalse(isSelected)
    }
}