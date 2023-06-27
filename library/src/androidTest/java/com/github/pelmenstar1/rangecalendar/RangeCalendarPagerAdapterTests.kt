package com.github.pelmenstar1.rangecalendar

import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class RangeCalendarPagerAdapterTests {
    enum class NotifyType {
        INSERTED,
        REMOVED,
        CHANGED
    }

    data class TypedRange(val type: NotifyType, val position: Int, val itemCount: Int)

    class RangeListBuilder {
        private val list = ArrayList<TypedRange>()

        fun inserted(position: Int, itemCount: Int) =
            list.add(TypedRange(NotifyType.INSERTED, position, itemCount))

        fun removed(position: Int, itemCount: Int) =
            list.add(TypedRange(NotifyType.REMOVED, position, itemCount))

        fun changed(position: Int, itemCount: Int) =
            list.add(TypedRange(NotifyType.CHANGED, position, itemCount))

        fun toArray() = list.toTypedArray()
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
            val actualRanges = ArrayList<TypedRange>()
            val expectedRanges = RangeListBuilder().also(buildRanges).toArray()

            val adapter = RangeCalendarPagerAdapter(cr)
            adapter.setRange(oldMinDate, oldMaxDate)

            adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    actualRanges.add(TypedRange(NotifyType.INSERTED, positionStart, itemCount))
                }

                override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                    actualRanges.add(TypedRange(NotifyType.REMOVED, positionStart, itemCount))
                }

                override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                    actualRanges.add(TypedRange(NotifyType.CHANGED, positionStart, itemCount))
                }
            })

            adapter.setRange(newMinDate, newMaxDate)

            val actualItemCount = adapter.itemCount
            assertEquals(expectedItemCount, actualItemCount, "item count")

            val actualRangesArray = actualRanges.toTypedArray()
            assertContentEquals(expectedRanges, actualRangesArray)
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
}