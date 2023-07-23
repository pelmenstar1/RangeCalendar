package com.github.pelmenstar1.rangecalendar

import android.graphics.PointF
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.pelmenstar1.rangecalendar.selection.CellAnimationType
import com.github.pelmenstar1.rangecalendar.selection.CellRange
import com.github.pelmenstar1.rangecalendar.selection.DefaultSelectionManager
import com.github.pelmenstar1.rangecalendar.selection.DefaultSelectionState
import com.github.pelmenstar1.rangecalendar.selection.SelectionRenderOptions
import com.github.pelmenstar1.rangecalendar.selection.SelectionState
import com.github.pelmenstar1.rangecalendar.selection.createState
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
internal class DefaultSelectionManagerTests {
    class CellMeasureManagerImpl : CellMeasureManager {
        override val cellWidth: Float
            get() = 30f

        override val cellHeight: Float
            get() = 20f

        override val roundRadius: Float
            get() = 5f

        val padding = 1f

        override fun getCellLeft(cellIndex: Int): Float {
            return (cellWidth + padding) * (cellIndex % 7)
        }

        override fun getCellTop(cellIndex: Int): Float {
            return (cellHeight + padding) * (cellIndex / 7)
        }

        override fun getCellDistance(cellIndex: Int): Float {
            return cellIndex * cellWidth
        }

        override fun getCellAndPointByDistance(distance: Float, outPoint: PointF): Int {
            throw NotImplementedError()
        }

        override fun getCellDistanceByPoint(x: Float, y: Float): Float {
            throw NotImplementedError()
        }

        override fun getCellAt(x: Float, y: Float, relativity: CellMeasureManager.CoordinateRelativity): Int {
            throw NotImplementedError()
        }

        override fun getRelativeAnchorValue(anchor: Distance.RelativeAnchor): Float {
            throw NotImplementedError()
        }
    }

    @Test
    fun createStateTest() {
        fun testHelper(
            rangeStart: Int,
            rangeEnd: Int,
            expectedStartLeft: Float,
            expectedStartTop: Float,
            expectedEndRight: Float,
            expectedEndTop: Float
        ) {
            val manager = DefaultSelectionManager()
            val cellMeasureManager = CellMeasureManagerImpl()

            val state = manager.createState(rangeStart, rangeEnd, cellMeasureManager) as DefaultSelectionState
            val shapeInfo = state.shapeInfo

            Log.i("ManagerTests", shapeInfo.toString())

            assertEquals(CellRange(rangeStart, rangeEnd), shapeInfo.range, "range")
            assertEquals(30f, shapeInfo.cellWidth, "cellWidth")
            assertEquals(20f, shapeInfo.cellHeight, "cellHeight")
            assertEquals(5f, shapeInfo.roundRadius, "roundRadius")
            assertEquals(0f, shapeInfo.firstCellOnRowLeft, "firstCellOnRowLeft")
            assertEquals(216f, shapeInfo.lastCellOnRowRight, "lastCellOnRowRight")
            assertEquals(expectedStartLeft, shapeInfo.startLeft, "startLeft")
            assertEquals(expectedStartTop, shapeInfo.startTop, "startTop")
            assertEquals(expectedEndRight, shapeInfo.endRight, "endRight")
            assertEquals(expectedEndTop, shapeInfo.endTop, "endTop")
        }

        testHelper(
            // start: (1, 1) end: (1, 1)
            rangeStart = 8, rangeEnd = 8,
            expectedStartLeft = 31f, expectedStartTop = 21f,
            expectedEndRight = 61f, expectedEndTop = 21f
        )

        testHelper(
            // start: (1, 1) end: (3, 4)
            rangeStart = 8, rangeEnd = 25,
            expectedStartLeft = 31f, expectedStartTop = 21f,
            expectedEndRight = 154f, expectedEndTop = 63f
        )
    }

    private inline fun<reified T : SelectionState.Transitive> createTransitionTestHelper(
        prevRange: CellRange,
        currentRange: CellRange,
        options: SelectionRenderOptions? = null,
        expectedNull: Boolean,
        validateTransition: (T) -> Unit = {}
    ) {
        val manager = DefaultSelectionManager()
        val cellMeasureManager = CellMeasureManagerImpl()

        val opts = options ?: SelectionRenderOptions()
        val prevState = if (prevRange.isValid) manager.createState(prevRange, cellMeasureManager) else null
        val currentState = if (currentRange.isValid) manager.createState(currentRange, cellMeasureManager) else null

        val transitiveState = manager.createTransition(prevState, currentState, cellMeasureManager, opts)

        if (expectedNull) {
            assertNull(transitiveState)
            return
        } else {
            assertNotNull(transitiveState)
        }

        assertEquals(T::class.java, transitiveState.javaClass as Class<*>)
        validateTransition(transitiveState as T)
    }

    @Test
    fun createTransitionTest() {
        //
        // prevRange = Invalid
        //
        createTransitionTestHelper<SelectionState.Transitive>(
            prevRange = CellRange.Invalid,
            currentRange = CellRange.Invalid,
            expectedNull = true
        )

        createTransitionTestHelper<DefaultSelectionState.AppearAlpha>(
            prevRange = CellRange.Invalid,
            currentRange = CellRange(start = 1, end = 1),
            options = SelectionRenderOptions().apply { cellAnimationType = CellAnimationType.ALPHA },
            expectedNull = false,
            validateTransition = {
                assertFalse(it.isReversed)
            }
        )

        createTransitionTestHelper<DefaultSelectionState.CellAppearBubble>(
            prevRange = CellRange.Invalid,
            currentRange = CellRange(start = 1, end = 1),
            options = SelectionRenderOptions().apply { cellAnimationType = CellAnimationType.BUBBLE },
            expectedNull = false,
            validateTransition = {
                assertFalse(it.isReversed)
            }
        )

        createTransitionTestHelper<DefaultSelectionState.AppearAlpha>(
            prevRange = CellRange.Invalid,
            currentRange = CellRange(start = 1, end = 2),
            expectedNull = false,
            validateTransition = {
                assertFalse(it.isReversed)
            }
        )

        //
        // prevRange and currentRange are single-cell
        //

        createTransitionTestHelper<DefaultSelectionState.CellMoveToCell>(
            prevRange = CellRange.single(1),
            currentRange = CellRange.single(2),
            expectedNull = false
        )

        createTransitionTestHelper<DefaultSelectionState.CellMoveToCell>(
            prevRange = CellRange.single(1),
            currentRange = CellRange.single(8),
            expectedNull = false
        )

        createTransitionTestHelper<DefaultSelectionState.DualAlpha>(
            prevRange = CellRange.single(1),
            currentRange = CellRange.single(9),
            options = SelectionRenderOptions().apply { cellAnimationType = CellAnimationType.ALPHA },
            expectedNull = false
        )

        createTransitionTestHelper<DefaultSelectionState.CellDualBubble>(
            prevRange = CellRange.single(1),
            currentRange = CellRange.single(9),
            options = SelectionRenderOptions().apply { cellAnimationType = CellAnimationType.BUBBLE },
            expectedNull = false
        )

        //
        // prevRange is single-cell, currentRange is not.
        //

        // [1, 1] intersects with [0, 2]
        createTransitionTestHelper<DefaultSelectionState.RangeToRange>(
            prevRange = CellRange.single(1),
            currentRange = CellRange(0, 2),
            expectedNull = false
        ) {
            assertEquals(30f, it.startStateStartCellDistance)
            assertEquals(60f, it.startStateEndCellDistance)
            assertEquals(0f, it.endStateStartCellDistance)
            assertEquals(90f, it.endStateEndCellDistance)
        }

        // There's no intersection between [1, 1] and [2, 3]
        createTransitionTestHelper<DefaultSelectionState.DualAlpha>(
            prevRange = CellRange.single(1),
            currentRange = CellRange(2, 3),
            expectedNull = false
        )

        //
        // prevRange is single-cell, currentRange is Invalid
        //

        createTransitionTestHelper<DefaultSelectionState.AppearAlpha>(
            prevRange = CellRange.single(1),
            currentRange = CellRange.Invalid,
            options = SelectionRenderOptions().apply { cellAnimationType = CellAnimationType.ALPHA },
            expectedNull = false
        ) {
            assertTrue(it.isReversed)
        }

        createTransitionTestHelper<DefaultSelectionState.CellAppearBubble>(
            prevRange = CellRange.single(1),
            currentRange = CellRange.Invalid,
            options = SelectionRenderOptions().apply { cellAnimationType = CellAnimationType.BUBBLE },
            expectedNull = false
        ) {
            assertTrue(it.isReversed)
        }

        //
        // Other
        //

        createTransitionTestHelper<DefaultSelectionState.AppearAlpha>(
            prevRange = CellRange(1, 3),
            currentRange = CellRange.Invalid,
            expectedNull = false
        ) {
            assertTrue(it.isReversed)
        }

        // There's no intersection between [1, 3] and [5, 6]
        createTransitionTestHelper<DefaultSelectionState.DualAlpha>(
            prevRange = CellRange(1, 3),
            currentRange = CellRange(5, 6),
            expectedNull = false
        )

        // [1, 3] intersects with [0, 5]
        createTransitionTestHelper<DefaultSelectionState.RangeToRange>(
            prevRange = CellRange(1, 3),
            currentRange = CellRange(0, 5),
            expectedNull = false
        ) {
            assertEquals(30f, it.startStateStartCellDistance)
            assertEquals(120f, it.startStateEndCellDistance)
            assertEquals(0f, it.endStateStartCellDistance)
            assertEquals(180f, it.endStateEndCellDistance)
        }
    }
}