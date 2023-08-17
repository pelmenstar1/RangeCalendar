package com.github.pelmenstar1.rangecalendar

import android.graphics.PointF
import android.graphics.RectF
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.pelmenstar1.rangecalendar.selection.CellRange
import com.github.pelmenstar1.rangecalendar.selection.SelectionShape
import com.github.pelmenstar1.rangecalendar.selection.SelectionShapeInfo
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SelectionShapeTests {
    class PointArrayBuilder {
        private val list = ArrayList<PointF>()

        fun point(x: Float, y: Float) {
            list.add(PointF(x, y))
        }

        fun rect(left: Float, top: Float, right: Float, bottom: Float) {
            point(left, top)
            point(right, top)
            point(right, bottom)
            point(left, bottom)
            point(left, top)
        }

        // For some unknown reason, Path.addRoundRect (when rx=ry=0) adds lines in different order than addRect.
        // It seems like a correct behaviour, because it's not changed among the releases, but it's a little
        // counterintuitive.
        //
        // Path.addRect(..., Path.Direction.CW) starts from left-top corner and goes clockwise, meanwhile
        // Path.addRoundRect(..., 0f, 0f, Path.Direction.CW) starts from left-bottom corner and goes clockwise.
        fun rectCreatedByRoundRect(left: Float, top: Float, right: Float, bottom: Float) {
            point(left, bottom)
            point(left, top)
            point(right, top)
            point(right, bottom)
            point(left, bottom)
        }

        fun toArray() = list.toTypedArray()
    }

    private fun createShapeInfo(
        range: CellRange,
        startLeft: Float, startTop: Float,
        endRight: Float, endTop: Float
    ): SelectionShapeInfo {
        return SelectionShapeInfo(
            range,
            startLeft, startTop,
            endRight, endTop,
            FIRST_CELL_LEFT, LAST_CELL_RIGHT,
            CELL_WIDTH, CELL_HEIGHT,
            roundRadius = 0f,
            useInMonthShape = false, inMonthShapeInfo = null
        )
    }

    @RequiresApi(26)
    private fun pathNoRoundRadiiTestHelper(
        shapeInfo: SelectionShapeInfo,
        expectedBounds: RectF,
        buildPoints: PointArrayBuilder.() -> Unit
    ) {
        val shape = SelectionShape()
        shape.update(
            shapeInfo,
            SelectionShape.ORIGIN_LOCAL,
            forcePath = true
        )

        assertEquals(expectedBounds, shape.bounds)

        val expectedPoints = PointArrayBuilder().also(buildPoints).toArray()
        val actualPoints = PathTestHelper.getPathPoints(shape.path!!)

        assertContentEquals(expectedPoints, actualPoints)
    }

    @Test
    fun pathNoRoundRadiiTest() {
        // PathTestHelper.getPathPoints requires API level 26.
        // Currently, there's no way to replace Path.approximate on lower API levels
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 26)

        // 1 row
        pathNoRoundRadiiTestHelper(
            shapeInfo = createShapeInfo(
                range = CellRange(1, 2),
                startLeft = 17f, startTop = 5f,
                endRight = 30f, endTop = 5f
            ),
            expectedBounds = RectF(17f, 5f, 30f, 15f)
        ) {
            rectCreatedByRoundRect(17f, 5f, 30f, 15f)
        }

        // 2 rows
        pathNoRoundRadiiTestHelper(
            shapeInfo = createShapeInfo(
                CellRange(2, 7),
                startLeft = 30f, startTop = 5f,
                endRight = 20f, endTop = 5f + CELL_HEIGHT
            ),
            expectedBounds = RectF(FIRST_CELL_LEFT, 5f, LAST_CELL_RIGHT, 5f + 2f * CELL_HEIGHT)
        ) {
            rect(30f, 5f, LAST_CELL_RIGHT, 5f + CELL_HEIGHT)
            rect(FIRST_CELL_LEFT, 5f + CELL_HEIGHT, 20f, 5f + 2f * CELL_HEIGHT)
        }

        pathNoRoundRadiiTestHelper(
            shapeInfo = createShapeInfo(
                range = CellRange(1, 8),
                startLeft = 30f, startTop = 5f,
                endRight = 40f, endTop = 5f + CELL_HEIGHT
            ),
            expectedBounds = RectF(FIRST_CELL_LEFT, 5f, LAST_CELL_RIGHT, 5f + 2f * CELL_HEIGHT),
        ) {
            point(30f, 5f)
            point(LAST_CELL_RIGHT, 5f)
            point(LAST_CELL_RIGHT, 5f + CELL_HEIGHT)
            point(40f, 5f + CELL_HEIGHT)
            point(40f, 5f + 2f * CELL_HEIGHT)
            point(FIRST_CELL_LEFT, 5f + 2f * CELL_HEIGHT)
            point(FIRST_CELL_LEFT, 5f + CELL_HEIGHT)
            point(30f, 5f + CELL_HEIGHT)
            point(30f, 5f)
        }

        pathNoRoundRadiiTestHelper(
            shapeInfo = createShapeInfo(
                range = CellRange(0, 13),
                startLeft = FIRST_CELL_LEFT, startTop = 5f,
                endRight = LAST_CELL_RIGHT, endTop = 5f + CELL_HEIGHT
            ),
            expectedBounds = RectF(FIRST_CELL_LEFT, 5f, LAST_CELL_RIGHT, 5f + 2f * CELL_HEIGHT)
        ) {
            rect(FIRST_CELL_LEFT, 5f, LAST_CELL_RIGHT, 5f + 2f * CELL_HEIGHT)
        }

        pathNoRoundRadiiTestHelper(
            shapeInfo = createShapeInfo(
                range = CellRange(1, 13),
                startLeft = 15f, startTop = 5f,
                endRight = LAST_CELL_RIGHT, endTop = 5f + CELL_HEIGHT
            ),
            expectedBounds = RectF(FIRST_CELL_LEFT, 5f, LAST_CELL_RIGHT, 5f + 2f * CELL_HEIGHT)
        ) {
            point(15f, 5f)
            point(LAST_CELL_RIGHT, 5f)
            point(LAST_CELL_RIGHT, 5f + 2f * CELL_HEIGHT)
            point(FIRST_CELL_LEFT, 5f + 2f * CELL_HEIGHT)
            point(FIRST_CELL_LEFT, 5f + CELL_HEIGHT)
            point(15f, 5f + CELL_HEIGHT)
            point(15f, 5f)
        }

        // 2+ rows
        pathNoRoundRadiiTestHelper(
            shapeInfo = createShapeInfo(
                range = CellRange(0, 20),
                startLeft = FIRST_CELL_LEFT, startTop = 5f,
                endRight = LAST_CELL_RIGHT, endTop = 5f + 2f * CELL_HEIGHT
            ),
            expectedBounds = RectF(FIRST_CELL_LEFT, 5f, LAST_CELL_RIGHT, 5f + 3f * CELL_HEIGHT)
        ) {
            rect(FIRST_CELL_LEFT, 5f, LAST_CELL_RIGHT, 5f + 3f * CELL_HEIGHT)
        }

        pathNoRoundRadiiTestHelper(
            shapeInfo = createShapeInfo(
                range = CellRange(1, 19),
                startLeft = 15f, startTop = 5f,
                endRight = 50f, endTop = 5f + 2f * CELL_HEIGHT
            ),
            expectedBounds = RectF(FIRST_CELL_LEFT, 5f, LAST_CELL_RIGHT, 5f + 3f * CELL_HEIGHT)
        ) {
            point(15f, 5f)
            point(LAST_CELL_RIGHT, 5f)
            point(LAST_CELL_RIGHT, 5f + 2f * CELL_HEIGHT)
            point(50f, 5f + 2f * CELL_HEIGHT)
            point(50f, 5f + 3f * CELL_HEIGHT)
            point(FIRST_CELL_LEFT, 5f + 3f * CELL_HEIGHT)
            point(FIRST_CELL_LEFT, 5f + CELL_HEIGHT)
            point(15f, 5f + CELL_HEIGHT)
            point(15f, 5f)
        }

        pathNoRoundRadiiTestHelper(
            shapeInfo = createShapeInfo(
                range = CellRange(1, 20),
                startLeft = 15f, startTop = 5f,
                endRight = LAST_CELL_RIGHT, endTop = 5f + 2f * CELL_HEIGHT
            ),
            expectedBounds = RectF(FIRST_CELL_LEFT, 5f, LAST_CELL_RIGHT, 5f + 3f * CELL_HEIGHT)
        ) {
            point(15f, 5f)
            point(LAST_CELL_RIGHT, 5f)
            point(LAST_CELL_RIGHT, 5f + 3f * CELL_HEIGHT)
            point(FIRST_CELL_LEFT, 5f + 3f * CELL_HEIGHT)
            point(FIRST_CELL_LEFT, 5f + CELL_HEIGHT)
            point(15f, 5f + CELL_HEIGHT)
            point(15f, 5f)
        }

        pathNoRoundRadiiTestHelper(
            shapeInfo = createShapeInfo(
                range = CellRange(0, 19),
                startLeft = FIRST_CELL_LEFT, startTop = 5f,
                endRight = 50f, endTop = 5f + 2f * CELL_HEIGHT
            ),
            expectedBounds = RectF(FIRST_CELL_LEFT, 5f, LAST_CELL_RIGHT, 5f + 3f * CELL_HEIGHT)
        ) {
            point(FIRST_CELL_LEFT, 5f)
            point(LAST_CELL_RIGHT, 5f)
            point(LAST_CELL_RIGHT, 5f + 2f * CELL_HEIGHT)
            point(50f, 5f + 2f * CELL_HEIGHT)
            point(50f, 5f + 3f * CELL_HEIGHT)
            point(FIRST_CELL_LEFT, 5f + 3f * CELL_HEIGHT)
            point(FIRST_CELL_LEFT, 5f)
        }
    }

    companion object {
        private const val CELL_WIDTH = 10f
        private const val CELL_HEIGHT = 10f

        private const val FIRST_CELL_LEFT = 5f
        private const val LAST_CELL_RIGHT = FIRST_CELL_LEFT + CELL_WIDTH * GridConstants.COLUMN_COUNT
    }
}